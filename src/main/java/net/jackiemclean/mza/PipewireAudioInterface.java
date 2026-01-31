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

		// Resolve zone properties node for volume/mute control
		String zonePropsNodeName = withPrefix(zone.getName(), zonePropsPrefix);
		Integer zonePropsNodeId = graph.nodeIds.get(zonePropsNodeName);
		if (zonePropsNodeId == null) {
			LOG.error("Zone properties node '{}' not found in PipeWire graph, skipping volume/mute", zonePropsNodeName);
		} else {
			applyMuteAndVolume(zonePropsNodeId, zoneState);
		}

		// Parse source and zone channels - each can specify its own node via "node:port" format
		NodePort leftSource = parseNodePort(source.getLeftInput().getName(), source.getName());
		NodePort rightSource = parseNodePort(source.getRightInput().getName(), source.getName());
		NodePort leftZone = parseNodePort(zone.getLeftOutput().getName(), zone.getName());
		NodePort rightZone = parseNodePort(zone.getRightOutput().getName(), zone.getName());

		// Reconcile left channel
		reconcileChannelWithNodePort(graph, leftSource, sourceLinkPrefix, leftZone, zoneLinkPrefix, zone.getName());

		// Reconcile right channel
		reconcileChannelWithNodePort(graph, rightSource, sourceLinkPrefix, rightZone, zoneLinkPrefix, zone.getName());
	}

	/**
	 * Reconciles a channel link between source and zone, resolving node IDs from parsed NodePort specs.
	 */
	private void reconcileChannelWithNodePort(GraphState graph,
			NodePort source, String sourcePrefix,
			NodePort zone, String zonePrefix,
			String zoneName) {
		// Only apply prefix when node was not explicitly specified
		String sourceNodeName = source.explicit() ? source.nodeName() : withPrefix(source.nodeName(), sourcePrefix);
		String zoneNodeName = zone.explicit() ? zone.nodeName() : withPrefix(zone.nodeName(), zonePrefix);

		Integer sourceNodeId = graph.nodeIds.get(sourceNodeName);
		Integer zoneNodeId = graph.nodeIds.get(zoneNodeName);

		if (sourceNodeId == null) {
			LOG.error("Source node '{}' not found in PipeWire graph, skipping channel", sourceNodeName);
			return;
		}
		if (zoneNodeId == null) {
			LOG.error("Zone node '{}' not found in PipeWire graph, skipping channel", zoneNodeName);
			return;
		}

		reconcileChannel(graph, sourceNodeId, source.portName(), zoneNodeId, zone.portName(), zoneName);
	}

	/**
	 * Parses a node:port specification. If the input contains a colon,
	 * the part before is the node name and after is the port name (explicit=true).
	 * Otherwise, uses the default name as the node (explicit=false).
	 */
	NodePort parseNodePort(String spec, String defaultNodeName) {
		if (spec == null) {
			return new NodePort(defaultNodeName, null, false);
		}
		int colonIdx = spec.indexOf(':');
		if (colonIdx > 0) {
			return new NodePort(spec.substring(0, colonIdx), spec.substring(colonIdx + 1), true);
		}
		return new NodePort(defaultNodeName, spec, false);
	}

	/**
	 * Represents a parsed node:port specification.
	 * @param nodeName The PipeWire node name
	 * @param portName The port name on that node
	 * @param explicit True if node was explicitly specified via "node:port" format (prefix should not be applied)
	 */
	record NodePort(String nodeName, String portName, boolean explicit) {
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
			// Check links going INTO the zone port - remove wrong sources
			if (link.inNodeId == zoneNodeId && link.inPortId == zonePortId) {
				if (link.outNodeId == sourceNodeId && link.outPortId == sourcePortId) {
					desiredExists = true;
				} else {
					wrongLinks.add(link.linkId);
					LOG.debug("Will remove wrong input link {} ({}:{} -> {}:{})", link.linkId,
							graph.portNames.get(link.outNodeId + ":" + link.outPortId), link.outNodeId,
							graph.portNames.get(link.inNodeId + ":" + link.inPortId), link.inNodeId);
				}
			}
			// Check links coming FROM the source port - remove links to wrong destinations
			else if (link.outNodeId == sourceNodeId && link.outPortId == sourcePortId) {
				// This source port is connected to something other than our zone port
				wrongLinks.add(link.linkId);
				LOG.debug("Will remove wrong output link {} ({}:{} -> {}:{})", link.linkId,
						graph.portNames.get(link.outNodeId + ":" + link.outPortId), link.outNodeId,
						graph.portNames.get(link.inNodeId + ":" + link.inPortId), link.inNodeId);
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
