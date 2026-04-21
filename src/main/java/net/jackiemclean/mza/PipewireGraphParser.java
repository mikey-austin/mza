package net.jackiemclean.mza;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a collection of raw PipeWire objects (as returned by pw-dump)
 * into a GraphState. Accepts either a JSON array directly or an iterable
 * of per-object JsonNodes; entries missing required fields are skipped.
 */
public final class PipewireGraphParser {

	private PipewireGraphParser() {
	}

	public static GraphState parse(Iterable<JsonNode> objects) {
		Map<String, Integer> nodeIds = new HashMap<>();
		Map<Integer, String> nodeNames = new HashMap<>();
		Map<String, Integer> inPorts = new HashMap<>();
		Map<String, Integer> outPorts = new HashMap<>();
		Map<String, String> portNames = new HashMap<>();
		List<LinkInfo> links = new ArrayList<>();

		for (JsonNode entry : objects) {
			if (entry == null || !entry.has("type")) {
				continue;
			}
			String type = entry.get("type").asText();
			int objectId = entry.path("id").asInt(-1);
			if (objectId < 0) {
				continue;
			}
			JsonNode info = entry.get("info");
			if (info == null || info.isNull()) {
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
						if (nodeId >= 0 && portName != null) {
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
					if (inNode >= 0 && inPort >= 0 && outNode >= 0 && outPort >= 0) {
						links.add(new LinkInfo(objectId, inNode, inPort, outNode, outPort));
					}
				}
				default -> {
					// ignore unrelated object types
				}
			}
		}

		return new GraphState(nodeIds, nodeNames, inPorts, outPorts, portNames, links);
	}
}
