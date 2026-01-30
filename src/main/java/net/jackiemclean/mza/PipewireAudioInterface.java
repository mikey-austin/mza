package net.jackiemclean.mza;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
			this.stringEnv.put("XDG_RUNTIME_DIR", pipewireRuntimeDir);
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

		// Parse dump once and extract all data in a single pass
		GraphState graph = loadAndParseGraph();

		// Resolve nodes with configured prefixes
		String zonePropsNodeName = withPrefix(zone.getName(), zonePropsPrefix);
		String zoneLinkNodeName = withPrefix(zone.getName(), zoneLinkPrefix);
		String sourceLinkNodeName = withPrefix(source.getName(), sourceLinkPrefix);

		// Apply mute/volume on the zone node
		Integer zonePropsNodeId = graph.nodeIds.get(zonePropsNodeName);
		if (zonePropsNodeId == null) {
			LOG.error("Zone properties node '{}' not found in PipeWire graph, skipping volume/mute", zonePropsNodeName);
		} else {
			applyMuteAndVolume(zonePropsNodeId, zoneState);
		}

		// Resolve link node IDs with graceful handling
		Integer zoneLinkNodeId = graph.nodeIds.get(zoneLinkNodeName);
		Integer sourceLinkNodeId = graph.nodeIds.get(sourceLinkNodeName);

		if (zoneLinkNodeId == null) {
			LOG.error("Zone link node '{}' not found in PipeWire graph, skipping link reconciliation", zoneLinkNodeName);
			return;
		}
		if (sourceLinkNodeId == null) {
			LOG.error("Source link node '{}' not found in PipeWire graph, skipping link reconciliation", sourceLinkNodeName);
			return;
		}

		reconcileChannel(graph, sourceLinkNodeId, source.getLeftInput().getName(),
				zoneLinkNodeId, zone.getLeftOutput().getName(), zone.getName());
		reconcileChannel(graph, sourceLinkNodeId, source.getRightInput().getName(),
				zoneLinkNodeId, zone.getRightOutput().getName(), zone.getName());
	}

	private String withPrefix(String rawName, String prefix) {
		if (rawName == null) {
			throw new IllegalArgumentException("Node name cannot be null");
		}
		return rawName.startsWith(prefix) ? rawName : prefix + rawName;
	}

	private void applyMuteAndVolume(int nodeId, ZoneState zoneState) {
		boolean muted = zoneState.isMuted();
		float volume = Math.max(0, Math.min(100, zoneState.getVolume())) / 100.0f;

		// Combined mute and volume in single pw-cli call
		String cmd = String.format("%s set-param %d Props '{ mute = %s, volume = %.4f }'",
				pwCliCommand,
				nodeId,
				muted ? "true" : "false",
				volume);
		commandExecutor.execute(cmd, stringEnv);
	}

	private void reconcileChannel(GraphState graph, int sourceNodeId, String sourcePort,
			int zoneNodeId, String zonePort, String zoneName) {
		if (sourcePort == null || zonePort == null) {
			LOG.warn("Skipping link for zone {}: missing port (sourcePort={}, zonePort={})",
					zoneName, sourcePort, zonePort);
			return;
		}

		Integer zonePortId = graph.inPorts.get(zoneNodeId + ":" + zonePort);
		Integer sourcePortId = graph.outPorts.get(sourceNodeId + ":" + sourcePort);

		if (sourcePortId == null) {
			LOG.warn("Missing source port id for node {}:{}, skipping link", sourceNodeId, sourcePort);
			return;
		}
		if (zonePortId == null) {
			LOG.warn("Missing zone port id for node {}:{}, skipping link", zoneNodeId, zonePort);
			return;
		}

		List<Integer> wrongLinks = new ArrayList<>();
		boolean desiredExists = false;

		for (LinkInfo link : graph.links) {
			if (link.inNodeId == zoneNodeId && link.inPortId == zonePortId) {
				if (link.outNodeId == sourceNodeId && link.outPortId == sourcePortId) {
					desiredExists = true;
				} else {
					wrongLinks.add(link.linkId);
					LOG.debug("Will remove link {} ({}:{} -> {}:{})", link.linkId,
							graph.portNames.get(link.outNodeId + ":" + link.outPortId), link.outNodeId,
							graph.portNames.get(link.inNodeId + ":" + link.inPortId), link.inNodeId);
				}
			}
		}

		// Remove stale links FIRST (reconcile by cleaning up before creating)
		unlinkById(wrongLinks, zoneName);

		// Create link if missing
		if (!desiredExists) {
			String fullSource = withPrefix(graph.nodeNames.get(sourceNodeId), sourceLinkPrefix) + ":" + sourcePort;
			String fullZone = withPrefix(graph.nodeNames.get(zoneNodeId), zoneLinkPrefix) + ":" + zonePort;
			String cmd = String.format("%s '%s' '%s' 2>/dev/null || true", pwLinkCommand, fullSource, fullZone);
			try {
				commandExecutor.execute(cmd, stringEnv);
			} catch (Exception e) {
				LOG.error("Failed to link {} -> {}", fullSource, fullZone, e);
			}
		}
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

	/**
	 * Parse the PipeWire graph dump in a single pass, extracting all needed data.
	 */
	private GraphState loadAndParseGraph() {
		JsonNode dump = loadDump();

		Map<String, Integer> nodeIds = new HashMap<>();      // node.name -> object.id
		Map<Integer, String> nodeNames = new HashMap<>();    // object.id -> node.name
		Map<String, Integer> inPorts = new HashMap<>();      // nodeId:portName -> object.id
		Map<String, Integer> outPorts = new HashMap<>();     // nodeId:portName -> object.id
		Map<String, String> portNames = new HashMap<>();     // nodeId:portObjectId -> portName
		List<LinkInfo> links = new ArrayList<>();

		for (JsonNode entry : dump) {
			if (!entry.has("type")) {
				continue;
			}

			String type = entry.get("type").asText();
			int objectId = entry.path("id").asInt(-1);
			if (objectId == -1) {
				continue;
			}

			JsonNode info = entry.get("info");
			if (info == null) {
				continue;
			}

			switch (type) {
				case "PipeWire:Interface:Node" -> {
					JsonNode props = info.get("props");
					if (props != null) {
						String nodeName = props.path("node.name").asText(null);
						if (nodeName != null) {
							nodeIds.put(nodeName, objectId);
							nodeNames.put(objectId, nodeName);
						}
					}
				}
				case "PipeWire:Interface:Port" -> {
					JsonNode props = info.get("props");
					if (props != null) {
						int nodeId = props.path("node.id").asInt(-1);
						String portName = props.path("port.name").asText(null);
						String direction = props.path("port.direction").asText();
						if (direction.isEmpty()) {
							direction = info.path("direction").asText();
						}

						if (nodeId != -1 && portName != null) {
							String key = nodeId + ":" + portName;
							if ("in".equals(direction)) {
								inPorts.put(key, objectId);
							} else if ("out".equals(direction)) {
								outPorts.put(key, objectId);
							}
							portNames.put(nodeId + ":" + objectId, portName);
						}
					}
				}
				case "PipeWire:Interface:Link" -> {
					int inNode = info.path("input-node-id").asInt(-1);
					int inPort = info.path("input-port-id").asInt(-1);
					int outNode = info.path("output-node-id").asInt(-1);
					int outPort = info.path("output-port-id").asInt(-1);

					if (inNode != -1 && inPort != -1 && outNode != -1 && outPort != -1) {
						links.add(new LinkInfo(objectId, inNode, inPort, outNode, outPort));
					}
				}
			}
		}

		return new GraphState(nodeIds, nodeNames, inPorts, outPorts, portNames, links);
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

	/**
	 * Holds all parsed graph state from a single pw-dump pass.
	 */
	private record GraphState(
			Map<String, Integer> nodeIds,
			Map<Integer, String> nodeNames,
			Map<String, Integer> inPorts,
			Map<String, Integer> outPorts,
			Map<String, String> portNames,
			List<LinkInfo> links) {
	}

	/**
	 * Represents a link between two ports.
	 */
	private record LinkInfo(int linkId, int inNodeId, int inPortId, int outNodeId, int outPortId) {
	}
}
