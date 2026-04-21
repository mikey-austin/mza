package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipewireGraphMonitorTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void publishesSnapshotAfterInitialChunk() throws Exception {
		PipewireGraphMonitor monitor = new PipewireGraphMonitor("pw-dump", Map.of());
		// Before any chunk the snapshot is empty.
		assertTrue(monitor.getSnapshot().nodeIds().isEmpty());

		monitor.applyChunk(loadDump());

		GraphState s = monitor.getSnapshot();
		assertEquals(Integer.valueOf(66), s.nodeIds().get("output.zone6_laundry_room"));
		// Initial dump signal is released.
		assertTrue(monitor.awaitInitialDump(100));
	}

	@Test
	void subsequentChunkMergesIncrementalChange() throws Exception {
		PipewireGraphMonitor monitor = new PipewireGraphMonitor("pw-dump", Map.of());
		monitor.applyChunk(loadDump());

		JsonNode removal = MAPPER.readTree("""
				[ { "id": 237, "type": "PipeWire:Interface:Link", "info": null } ]
				""");
		monitor.applyChunk(removal);

		assertNull(monitor.getSnapshot().links().stream()
				.filter(l -> l.linkId() == 237)
				.findAny().orElse(null));
	}

	private JsonNode loadDump() throws Exception {
		try (InputStream is = getClass().getResourceAsStream("/pw-dump-test.json")) {
			assertNotNull(is);
			return MAPPER.readTree(new String(is.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
}
