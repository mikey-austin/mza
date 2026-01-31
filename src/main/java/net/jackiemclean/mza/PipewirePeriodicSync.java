package net.jackiemclean.mza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically re-syncs all zone states to ensure PipeWire routing stays correct.
 * This guards against external changes to the PipeWire graph that might connect
 * sources to wrong destinations.
 */
@Component
@ConditionalOnProperty(name = "audio.interface.backend", havingValue = "PIPEWIRE")
public class PipewirePeriodicSync {

	private static final Logger LOG = LoggerFactory.getLogger(PipewirePeriodicSync.class);

	private final ZoneStateRepository zoneStateRepository;
	private final ZoneRouter zoneRouter;

	public PipewirePeriodicSync(ZoneStateRepository zoneStateRepository, ZoneRouter zoneRouter) {
		this.zoneStateRepository = zoneStateRepository;
		this.zoneRouter = zoneRouter;
	}

	@Scheduled(fixedRateString = "${audio.interface.pipewire.periodic-sync-interval-ms:60000}")
	public void periodicSync() {
		LOG.debug("Running periodic PipeWire sync");
		for (var zoneState : zoneStateRepository.findAll()) {
			try {
				zoneRouter.syncZone(zoneState);
			} catch (Exception e) {
				LOG.error("Failed to sync zone {} during periodic sync", zoneState.getName(), e);
			}
		}
	}
}
