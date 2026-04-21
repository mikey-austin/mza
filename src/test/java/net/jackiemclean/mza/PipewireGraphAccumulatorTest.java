package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PipewireGraphAccumulatorTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void parsesFullInitialDump() throws Exception {
		PipewireGraphAccumulator acc = new PipewireGraphAccumulator();
		GraphState state = acc.apply(loadDump());

		// Zone output node present
		assertEquals(Integer.valueOf(66), state.nodeIds().get("output.zone6_laundry_room"));
		// Zone input node present
		assertEquals(Integer.valueOf(67), state.nodeIds().get("input.zone6_laundry_room"));
		// Source node present
		assertEquals(Integer.valueOf(42), state.nodeIds().get("upnp2"));
		// Source out-port resolved
		assertNotNull(state.outPorts().get("42:monitor_FL"));
		// Zone in-port resolved
		assertNotNull(state.inPorts().get("67:playback_FL"));
		// Existing links from mpd (39) -> zone input (67) captured
		assertFalse(state.links().isEmpty());
		assertTrue(state.links().stream().anyMatch(l -> l.outNodeId() == 39 && l.inNodeId() == 67));
	}

	@Test
	void appliesIncrementalAddAndRemove() throws Exception {
		PipewireGraphAccumulator acc = new PipewireGraphAccumulator();
		acc.apply(loadDump());

		// Simulate a new node arriving as a subsequent monitor chunk.
		JsonNode addChunk = MAPPER.readTree("""
				[ { "id": 9999, "type": "PipeWire:Interface:Node", "info": { "props": { "node.name": "new_node" } } } ]
				""");
		GraphState after = acc.apply(addChunk);
		assertEquals(Integer.valueOf(9999), after.nodeIds().get("new_node"));

		// Simulate the same node being removed (info=null).
		JsonNode removeChunk = MAPPER.readTree("""
				[ { "id": 9999, "type": "PipeWire:Interface:Node", "info": null } ]
				""");
		GraphState afterRemove = acc.apply(removeChunk);
		assertNull(afterRemove.nodeIds().get("new_node"));
	}

	@Test
	void removesLinkByRemovalChunk() throws Exception {
		PipewireGraphAccumulator acc = new PipewireGraphAccumulator();
		acc.apply(loadDump());

		// Link 237 exists in initial dump; simulate pw-link -d 237 → monitor emits removal.
		GraphState before = acc.snapshot();
		assertTrue(before.links().stream().anyMatch(l -> l.linkId() == 237));

		JsonNode removeLink = MAPPER.readTree("""
				[ { "id": 237, "type": "PipeWire:Interface:Link", "info": null } ]
				""");
		GraphState after = acc.apply(removeLink);
		assertFalse(after.links().stream().anyMatch(l -> l.linkId() == 237));
	}

	@Test
	void ignoresMalformedEntries() throws Exception {
		PipewireGraphAccumulator acc = new PipewireGraphAccumulator();
		JsonNode chunk = MAPPER.readTree("""
				[
					{ "type": "PipeWire:Interface:Node" },
					{ "id": -1, "type": "PipeWire:Interface:Node", "info": { "props": { "node.name": "x" } } },
					{ "id": 1, "type": "PipeWire:Interface:Node", "info": { "props": { "node.name": "real" } } }
				]
				""");
		GraphState state = acc.apply(chunk);
		assertEquals(1, acc.size());
		assertEquals(Integer.valueOf(1), state.nodeIds().get("real"));
	}

	private JsonNode loadDump() throws Exception {
		try (InputStream is = getClass().getResourceAsStream("/pw-dump-test.json")) {
			assertNotNull(is, "pw-dump-test.json missing from test resources");
			return MAPPER.readTree(new String(is.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
}
