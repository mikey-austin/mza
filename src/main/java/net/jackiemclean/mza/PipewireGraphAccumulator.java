package net.jackiemclean.mza;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accumulates PipeWire graph chunks streamed by `pw-dump --monitor`.
 *
 * Each chunk is a JSON array of objects. An object with {@code "info": null}
 * (or missing info) represents a removal; otherwise the object is added or
 * replaced by id. After applying a chunk, a fresh {@link GraphState} is
 * derived from all currently-known objects.
 */
public class PipewireGraphAccumulator {

	private final Map<Integer, JsonNode> objects = new LinkedHashMap<>();

	public synchronized GraphState apply(JsonNode array) {
		if (array != null && array.isArray()) {
			for (JsonNode entry : array) {
				int id = entry.path("id").asInt(-1);
				if (id < 0) {
					continue;
				}
				JsonNode info = entry.get("info");
				if (info == null || info.isNull()) {
					objects.remove(id);
				} else {
					objects.put(id, entry);
				}
			}
		}
		return PipewireGraphParser.parse(objects.values());
	}

	public synchronized GraphState snapshot() {
		return PipewireGraphParser.parse(objects.values());
	}

	synchronized int size() {
		return objects.size();
	}
}
