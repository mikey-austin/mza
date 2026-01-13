package net.jackiemclean.mza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Decorator that debounces/coalesces rapid sync requests per zone.
 * Within a configurable quantum (default 500ms), multiple sync calls for the
 * same zone
 * are merged, and only the final merged state is applied to the delegate.
 */
public class DebouncingAudioInterface implements AudioInterface, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(DebouncingAudioInterface.class);

    private final AudioInterface delegate;
    private final ScheduledExecutorService scheduler;
    private final long quantumMs;

    // Per-zone pending state
    private final Map<String, PendingSyncState> pending = new ConcurrentHashMap<>();

    public DebouncingAudioInterface(AudioInterface delegate, long quantumMs) {
        this(delegate, quantumMs, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "audio-debounce");
            t.setDaemon(true);
            return t;
        }));
    }

    // Constructor for testing with custom scheduler
    DebouncingAudioInterface(AudioInterface delegate, long quantumMs, ScheduledExecutorService scheduler) {
        this.delegate = delegate;
        this.quantumMs = quantumMs;
        this.scheduler = scheduler;
    }

    @Override
    public void sync(Zone zone, Source source, ZoneState zoneState) {
        String zoneName = zone.getName();

        synchronized (pending) {
            PendingSyncState existing = pending.get(zoneName);

            if (existing != null) {
                // Merge: latest values win
                existing.merge(zone, source, zoneState);
                // Cancel existing scheduled flush
                existing.cancelScheduledFlush();
            } else {
                existing = new PendingSyncState(zone, source, zoneState);
                pending.put(zoneName, existing);
            }

            // Schedule new flush
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> flush(zoneName),
                    quantumMs,
                    TimeUnit.MILLISECONDS);
            existing.setScheduledFlush(future);

            LOG.debug("Queued sync for zone {} (quantum={}ms)", zoneName, quantumMs);
        }
    }

    private void flush(String zoneName) {
        PendingSyncState state;
        synchronized (pending) {
            state = pending.remove(zoneName);
        }

        if (state != null) {
            LOG.debug("Flushing debounced sync for zone {}", zoneName);
            try {
                delegate.sync(state.zone, state.source, state.zoneState);
            } catch (Exception e) {
                LOG.error("Error syncing zone {} after debounce", zoneName, e);
            }
        }
    }

    @Override
    public void destroy() {
        LOG.info("Shutting down debouncing audio interface, flushing pending syncs");
        scheduler.shutdown();

        // Flush all pending immediately
        synchronized (pending) {
            for (String zoneName : pending.keySet()) {
                PendingSyncState state = pending.get(zoneName);
                if (state != null) {
                    state.cancelScheduledFlush();
                    try {
                        delegate.sync(state.zone, state.source, state.zoneState);
                    } catch (Exception e) {
                        LOG.error("Error flushing zone {} on shutdown", zoneName, e);
                    }
                }
            }
            pending.clear();
        }

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Holds pending state for a zone, including context needed for sync.
     */
    private static class PendingSyncState {
        Zone zone;
        Source source;
        ZoneState zoneState;
        ScheduledFuture<?> scheduledFlush;

        PendingSyncState(Zone zone, Source source, ZoneState zoneState) {
            this.zone = zone;
            this.source = source;
            // Copy the state to avoid mutation issues
            this.zoneState = copyState(zoneState);
        }

        void merge(Zone zone, Source source, ZoneState incoming) {
            // Always take the latest zone and source context
            this.zone = zone;
            this.source = source;
            // Merge state: latest values win
            this.zoneState.setVolume(incoming.getVolume());
            this.zoneState.setMuted(incoming.isMuted());
            if (incoming.getSourceName() != null) {
                this.zoneState.setSourceName(incoming.getSourceName());
            }
        }

        void setScheduledFlush(ScheduledFuture<?> future) {
            this.scheduledFlush = future;
        }

        void cancelScheduledFlush() {
            if (scheduledFlush != null && !scheduledFlush.isDone()) {
                scheduledFlush.cancel(false);
            }
        }

        private static ZoneState copyState(ZoneState original) {
            ZoneState copy = new ZoneState();
            copy.setName(original.getName());
            copy.setVolume(original.getVolume());
            copy.setMuted(original.isMuted());
            copy.setSourceName(original.getSourceName());
            copy.setZoneDetails(original.getZoneDetails());
            return copy;
        }
    }
}
