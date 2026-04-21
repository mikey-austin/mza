package net.jackiemclean.mza;

/**
 * Supplies a PipeWire graph snapshot. Implementations should return a
 * non-null GraphState (possibly empty) and must be safe to call from
 * any thread.
 */
public interface PipewireGraphSource {
	GraphState getSnapshot();
}
