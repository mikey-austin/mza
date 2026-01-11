package net.jackiemclean.mza;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupInitializationService {

    private static final Logger LOG = LoggerFactory.getLogger(GroupInitializationService.class);

    @Autowired
    private GroupStateRepository groupStateRepository;
    @Autowired
    private ZoneRepository zoneRepository;
    @Autowired
    private GroupRouter groupRouter;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeDefaultGroups() {
        if (groupStateRepository.count() == 0) {
            LOG.info("No groups found, creating default single-member groups for all zones");

            for (Zone zone : zoneRepository.findAll()) {
                GroupState group = new GroupState();
                group.setName(zone.getName());
                group.setDisplayName(toDisplayName(zone.getName()));
                group.setZones(Set.of(zone.getName()));
                group.setDescription("Auto-created group for " + zone.getName());
                group.setCreatedAt(Instant.now());
                group.setUpdatedAt(Instant.now());

                GroupState saved = groupStateRepository.save(group);
                groupRouter.publishGroupToMqtt(saved);

                LOG.info("Created default group for zone: {}", zone.getName());
            }
        } else {
            LOG.info("Groups already exist, skipping initialization");

            // Publish all existing groups to MQTT
            for (GroupState group : groupStateRepository.findAll()) {
                groupRouter.publishGroupToMqtt(group);
            }
        }
    }

    private String toDisplayName(String name) {
        // Convert "living_room" to "Living Room"
        return Arrays.stream(name.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
