package net.jackiemclean.mza;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains an in-memory snapshot of the PipeWire graph by running
 * `pw-dump --monitor` as a single long-lived subprocess.
 *
 * This replaces per-sync `pw-dump` invocations so the PipeWire daemon
 * sees one persistent client instead of many short-lived ones — short-lived
 * clients churning through pipewire's socket layer were driving pipewire's
 * FD count toward its systemd LimitNOFILE ceiling and crashing the daemon.
 */
public class PipewireGraphMonitor implements PipewireGraphSource {

	private static final Logger LOG = LoggerFactory.getLogger(PipewireGraphMonitor.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final long INITIAL_DUMP_TIMEOUT_MS = 10_000;

	private final String pwDumpCommand;
	private final Map<String, String> env;

	private final PipewireGraphAccumulator accumulator = new PipewireGraphAccumulator();
	private final AtomicReference<GraphState> snapshot = new AtomicReference<>(GraphState.empty());
	private final CountDownLatch initialDumpLatch = new CountDownLatch(1);

	private volatile Thread readerThread;
	private volatile Process process;
	private volatile boolean shutdown;

	public PipewireGraphMonitor(String pwDumpCommand, Map<String, String> env) {
		this.pwDumpCommand = pwDumpCommand;
		this.env = env == null ? Map.of() : Map.copyOf(env);
	}

	@PostConstruct
	public void start() {
		Thread t = new Thread(this::run, "pw-monitor");
		t.setDaemon(true);
		t.start();
		readerThread = t;
		try {
			if (!initialDumpLatch.await(INITIAL_DUMP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
				LOG.warn("pw-dump --monitor did not produce an initial snapshot within {}ms; "
						+ "continuing with empty graph (will populate asynchronously)",
						INITIAL_DUMP_TIMEOUT_MS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public GraphState getSnapshot() {
		return snapshot.get();
	}

	public boolean awaitInitialDump(long timeoutMs) throws InterruptedException {
		return initialDumpLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
	}

	@PreDestroy
	public void stop() {
		shutdown = true;
		Process p = process;
		if (p != null) {
			p.destroy();
		}
		Thread t = readerThread;
		if (t != null) {
			t.interrupt();
			try {
				t.join(TimeUnit.SECONDS.toMillis(2));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void run() {
		long backoffMs = 500;
		while (!shutdown) {
			try {
				runOnce();
				backoffMs = 500;
			} catch (Exception e) {
				if (shutdown) {
					return;
				}
				LOG.warn("pw-dump --monitor failed, retrying in {}ms: {}", backoffMs, e.toString());
			}
			if (shutdown) {
				return;
			}
			try {
				Thread.sleep(backoffMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			backoffMs = Math.min(backoffMs * 2, 30_000);
		}
	}

	private void runOnce() throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(pwDumpCommand, "--monitor", "--no-colors");
		pb.environment().putAll(env);
		pb.redirectErrorStream(false);
		Process p = pb.start();
		process = p;
		LOG.info("Started pw-dump --monitor (pid={})", p.pid());

		Thread stderrDrain = new Thread(() -> drainStderr(p), "pw-monitor-stderr");
		stderrDrain.setDaemon(true);
		stderrDrain.start();

		StringBuilder buffer = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				buffer.append(line).append('\n');
				// pw-dump --monitor writes each chunk as a top-level JSON array
				// terminated by "]" on its own line. We try to parse on every
				// "]"; nested arrays will fail to parse and we keep buffering.
				if (line.trim().equals("]")) {
					if (tryProcess(buffer.toString())) {
						buffer.setLength(0);
					}
				}
			}
		}

		int exit = p.waitFor();
		stderrDrain.interrupt();
		if (!shutdown) {
			throw new IOException("pw-dump exited with code " + exit);
		}
	}

	private boolean tryProcess(String json) {
		try {
			JsonNode node = MAPPER.readTree(json);
			if (node.isArray()) {
				applyChunk(node);
			}
			return true;
		} catch (JsonProcessingException e) {
			return false;
		}
	}

	/** Package-private so tests can drive chunk processing without a subprocess. */
	void applyChunk(JsonNode array) {
		snapshot.set(accumulator.apply(array));
		initialDumpLatch.countDown();
	}

	private void drainStderr(Process p) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				LOG.debug("pw-dump stderr: {}", line);
			}
		} catch (IOException ignored) {
			// Pipe closed.
		}
	}
}
