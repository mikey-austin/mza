package net.jackiemclean.mza;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PipeWire backend that mirrors the simple channel mapping of the ALSA/amixer
 * backend: left-to-left, right-to-right. It controls volume/mute on the zone
 * node using pw-cli set-param and connects ports using pw-link.
 */
public class PipewireAudioInterface implements AudioInterface {

	private static final Logger LOG = LoggerFactory.getLogger(PipewireAudioInterface.class);

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final CommandExecutor commandExecutor;
	private final Map<String, String> stringEnv;

	private final String pwDumpCommand;
	private final String pwLinkCommand;
	private final String pwCliCommand;
	private final String sourceLinkPrefix;
	private final String zoneLinkPrefix;
	private final String zonePropsPrefix;

	/** Cache node.name -> id to avoid repeated pw-dump parsing. */
	private final Map<String, Integer> nodeIdCache = new ConcurrentHashMap<>();

	public PipewireAudioInterface(
			String pipewireRuntimeDir,
			String pwDumpCommand,
			String pwLinkCommand,
			String pwCliCommand,
			String sourceLinkPrefix,
			String zoneLinkPrefix,
			String zonePropsPrefix,
			CommandExecutor commandExecutor) {
		this.commandExecutor = commandExecutor;
		this.pwDumpCommand = pwDumpCommand;
		this.pwLinkCommand = pwLinkCommand;
		this.pwCliCommand = pwCliCommand;
		this.sourceLinkPrefix = sourceLinkPrefix == null ? "" : sourceLinkPrefix;
		this.zoneLinkPrefix = zoneLinkPrefix == null ? "" : zoneLinkPrefix;
		this.zonePropsPrefix = zonePropsPrefix == null ? "" : zonePropsPrefix;

		this.stringEnv = new HashMap<>();
		if (pipewireRuntimeDir != null && !pipewireRuntimeDir.isBlank()) {
			this.stringEnv.put("PIPEWIRE_RUNTIME_DIR", pipewireRuntimeDir);
			this.stringEnv.put("XDG_RUNTIME_DIR", pipewireRuntimeDir); // Fallback usually needed
		} else {
			String xdg = System.getenv("XDG_RUNTIME_DIR");
			if (xdg != null) {
				this.stringEnv.put("PIPEWIRE_RUNTIME_DIR", xdg);
				this.stringEnv.put("XDG_RUNTIME_DIR", xdg);
			}
		}
	}

	@Override
	public void sync(Zone zone, Source source, ZoneState zoneState) {
		LOG.debug("Syncing zone {} (Source: {}, State: {})", zone.getName(), source.getName(), zoneState);

		JsonNode dump = loadDump();

		// Resolve nodes with configured prefixes
		String zonePropsNodeName = withPrefix(zone.getName(), zonePropsPrefix);
		String zoneLinkNodeName = withPrefix(zone.getName(), zoneLinkPrefix);
		String sourceLinkNodeName = withPrefix(source.getName(), sourceLinkPrefix);

		// Apply mute/volume on the zone node
		int zoneNodeId = resolveNodeId(dump, zonePropsNodeName);
		applyMuteAndVolume(zoneNodeId, zoneState);

		reconcileChannel(dump, sourceLinkNodeName, source.getLeftInput().getName(), zoneLinkNodeName,
				zone.getLeftOutput().getName(), zone.getName());
		reconcileChannel(dump, sourceLinkNodeName, source.getRightInput().getName(), zoneLinkNodeName,
				zone.getRightOutput().getName(), zone.getName());

	}

	private String withPrefix(String rawName, String prefix) {
		if (rawName == null) {
			throw new IllegalArgumentException("Node name cannot be null");
		}
		return rawName.startsWith(prefix) ? rawName : prefix + rawName;
	}

	private int resolveNodeId(JsonNode dump, String nodeName) {
		Integer cached = nodeIdCache.get(nodeName);
		if (cached != null) {
			LOG.debug("Using cached node id {} for {}", cached, nodeName);
			return cached;
		}

		for (JsonNode entry : dump) {
			if (!entry.has("type") || !"PipeWire:Interface:Node".equals(entry.get("type").asText())) {
				continue;
			}
			JsonNode info = entry.get("info");
			if (info == null) {
				continue;
			}
			JsonNode props = info.get("props");
			if (props != null) {
				JsonNode nameNode = props.get("node.name");
				if (nameNode != null && nodeName.equals(nameNode.asText())) {
					int id = entry.get("id").asInt();
					nodeIdCache.put(nodeName, id);
					return id;
				}
			}
		}

		throw new IllegalStateException("Unable to resolve PipeWire node id for " + nodeName);
	}

	private void applyMuteAndVolume(int nodeId, ZoneState zoneState) {
		boolean muted = zoneState.isMuted();
		float volume = Math.max(0, Math.min(100, zoneState.getVolume())) / 100.0f;

		String muteCmd = String.format("%s set-param %d Props '{ mute = %s }'",
				pwCliCommand,
				nodeId,
				muted ? "true" : "false");
		commandExecutor.execute(muteCmd, stringEnv);

		String volumeCmd = String.format("%s set-param %d Props '{ volume = %.4f }'",
				pwCliCommand,
				nodeId,
				volume);
		commandExecutor.execute(volumeCmd, stringEnv);
	}

	private void reconcileChannel(JsonNode dump, String sourceNode, String sourcePort, String zoneNode, String zonePort,
			String zoneName) {
		if (sourcePort == null || zonePort == null) {
			LOG.warn("Skipping link: missing port (sourcePort={}, zonePort={})", sourcePort, zonePort);
			return;
		}

		int sourceNodeId = resolveNodeId(dump, sourceNode);
		int zoneNodeId = resolveNodeId(dump, zoneNode);

		Map<String, Integer> inPortMap = portMap(dump, "in"); // key: nodeId:portName -> portId
		Map<String, Integer> outPortMap = portMap(dump, "out"); // key: nodeId:portName -> portId

		Integer zonePortId = inPortMap.get(zoneNodeId + ":" + zonePort);
		Integer sourcePortId = outPortMap.get(sourceNodeId + ":" + sourcePort);
		if (sourcePortId == null) {
			LOG.warn("Missing source port id for {}:{}, skipping link", sourceNode, sourcePort);
			return;
		}

		Map<String, String> portNames = portNameMap(dump); // key nodeId:portId -> portName

		List<Integer> wrongLinks = new java.util.ArrayList<>();
		boolean desiredExists = false;
		for (JsonNode entry : dump) {
			if (!entry.has("type") || !"PipeWire:Interface:Link".equals(entry.get("type").asText())) {
				continue;
			}
			JsonNode info = entry.get("info");
			if (info == null) {
				continue;
			}
			int inNode = info.path("input-node-id").asInt(-1);
			int inPort = info.path("input-port-id").asInt(-1);
			int outNode = info.path("output-node-id").asInt(-1);
			int outPort = info.path("output-port-id").asInt(-1);
			if (inNode == -1 || inPort == -1 || outNode == -1 || outPort == -1) {
				continue;
			}
			boolean sameNode = inNode == zoneNodeId;
			boolean samePort = zonePortId == null || inPort == zonePortId;
			if (sameNode && samePort) {
				if (outNode == sourceNodeId && outPort == sourcePortId) {
					desiredExists = true;
				} else {
					wrongLinks.add(entry.get("id").asInt());
					LOG.debug("Will remove link {} ({}:{} -> {}:{})", entry.get("id").asInt(),
							portNames.get(outNode + ":" + outPort), outNode,
							portNames.get(inNode + ":" + inPort), inNode);
				}
			}
		}

		// Remove any other links into this zone port FIRST (reconcile by cleaning up
		// stale state)
		unlinkById(wrongLinks, zoneName);

		// Create link if missing
		if (!desiredExists) {
			String fullSource = sourceNode + ":" + sourcePort;
			String fullZone = zoneNode + ":" + zonePort;
			String cmd = String.format("%s '%s' '%s' 2>/dev/null || true", pwLinkCommand, fullSource, fullZone);
			try {
				commandExecutor.execute(cmd, stringEnv);
			} catch (Exception e) {
				LOG.error("Failed to link {} -> {}", fullSource, fullZone, e);
			}
		}
	}

	private Map<String, Integer> portMap(JsonNode dump, String direction) {
		Map<String, Integer> map = new HashMap<>();
		for (JsonNode entry : dump) {
			if (!entry.has("type") || !"PipeWire:Interface:Port".equals(entry.get("type").asText())) {
				continue;
			}
			JsonNode info = entry.get("info");
			if (info == null || info.get("props") == null) {
				continue;
			}
			JsonNode props = info.get("props");
			String dir = props.path("port.direction").asText();
			if (dir.isEmpty()) {
				dir = info.path("direction").asText();
			}
			if (!direction.equals(dir)) {
				continue;
			}
			int nodeId = props.path("node.id").asInt(-1);
			// Use entry's top-level id (object.id) since links reference ports by object.id, not port.id
			int objectId = entry.path("id").asInt(-1);
			String name = props.path("port.name").asText(null);
			if (nodeId == -1 || objectId == -1 || name == null) {
				continue;
			}
			map.put(nodeId + ":" + name, objectId);
		}
		return map;
	}

	private Map<String, String> portNameMap(JsonNode dump) {
		Map<String, String> map = new HashMap<>();
		for (JsonNode entry : dump) {
			if (!entry.has("type") || !"PipeWire:Interface:Port".equals(entry.get("type").asText())) {
				continue;
			}
			JsonNode info = entry.get("info");
			if (info == null || info.get("props") == null) {
				continue;
			}
			JsonNode props = info.get("props");
			int nodeId = props.path("node.id").asInt(-1);
			// Use entry's top-level id (object.id) since links reference ports by object.id, not port.id
			int objectId = entry.path("id").asInt(-1);
			String name = props.path("port.name").asText(null);
			if (nodeId == -1 || objectId == -1 || name == null) {
				continue;
			}
			map.put(nodeId + ":" + objectId, name);
		}
		return map;
	}

	private void unlinkById(List<Integer> linkIds, String zoneName) {
		for (int linkId : linkIds) {
			String unlinkCmd = String.format("%s -d %d 2>/dev/null || true", pwLinkCommand, linkId);
			try {
				commandExecutor.execute(unlinkCmd, stringEnv);
			} catch (Exception e) {
				LOG.warn("Failed to unlink link {} for zone {}", linkId, zoneName, e);
			}
		}
	}

	private JsonNode loadDump() {
		try {
			List<String> lines = commandExecutor.executeAndGetOutput(pwDumpCommand, stringEnv);
			String json = String.join("\n", lines);
			return MAPPER.readTree(json);
		} catch (Exception e) {
			throw new RuntimeException("Failed to load/parse pw-dump output", e);
		}
	}
}
