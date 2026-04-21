package net.jackiemclean.mza;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the portion of the PipeWire graph mza cares about:
 * node name/id mappings, port name/id mappings split by direction, and
 * existing links.
 */
public record GraphState(
		Map<String, Integer> nodeIds,
		Map<Integer, String> nodeNames,
		Map<String, Integer> inPorts,
		Map<String, Integer> outPorts,
		Map<String, String> portNames,
		List<LinkInfo> links) {

	public static GraphState empty() {
		return new GraphState(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
	}
}
