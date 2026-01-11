package net.jackiemclean.mza;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    private static final Logger LOG = LoggerFactory.getLogger(GroupService.class);

    @Autowired
    private GroupStateRepository groupStateRepository;
    @Autowired
    private ZoneRepository zoneRepository;
    @Autowired
    private GroupRouter groupRouter;

    @Transactional
    public GroupState createGroup(
            String name, String displayName, Set<String> zones, String description) {
        // Validate name format
        validateGroupName(name);

        // Validate zones exist
        validateZonesExist(zones);

        // Remove zones from their current groups
        for (String zoneName : zones) {
            removeZoneFromCurrentGroup(zoneName);
        }

        // Create new group
        GroupState group = new GroupState();
        group.setName(name);
        group.setDisplayName(displayName != null ? displayName : toDisplayName(name));
        group.setZones(new HashSet<>(zones));
        group.setDescription(description);
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());

        GroupState saved = groupStateRepository.save(group);
        groupRouter.publishGroupToMqtt(saved);
        LOG.info("Created group: {}", saved.getName());
        return saved;
    }

    @Transactional
    public GroupState updateGroup(
            String name, String displayName, Set<String> zones, String description) {
        GroupState group = groupStateRepository
                .findByName(name)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // Validate new zones exist
        validateZonesExist(zones);

        // Find zones being removed from this group
        Set<String> removedZones = new HashSet<>(group.getZones());
        removedZones.removeAll(zones);

        // Remove new zones from their current groups
        for (String zoneName : zones) {
            if (!group.getZones().contains(zoneName)) {
                removeZoneFromCurrentGroup(zoneName);
            }
        }

        // Update group
        group.setZones(new HashSet<>(zones));
        group.setDisplayName(displayName != null ? displayName : group.getDisplayName());
        group.setDescription(description);
        group.setUpdatedAt(Instant.now());

        GroupState saved = groupStateRepository.save(group);
        groupRouter.publishGroupToMqtt(saved);

        // Create single-member groups for removed zones
        for (String zoneName : removedZones) {
            createSingleMemberGroup(zoneName);
        }

        LOG.info("Updated group: {}", saved.getName());
        return saved;
    }

    @Transactional
    public void deleteGroup(String name) {
        GroupState group = groupStateRepository
                .findByName(name)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        Set<String> orphanedZones = new HashSet<>(group.getZones());

        groupStateRepository.delete(group);
        groupRouter.publishGroupDeletion(name);

        // Create single-member groups for all orphaned zones
        for (String zoneName : orphanedZones) {
            createSingleMemberGroup(zoneName);
        }

        LOG.info("Deleted group: {}", name);
    }

    @Transactional
    public GroupState addZoneToGroup(String groupName, String zoneName) {
        GroupState group = groupStateRepository
                .findByName(groupName)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        validateZoneExists(zoneName);
        removeZoneFromCurrentGroup(zoneName);

        group.getZones().add(zoneName);
        group.setUpdatedAt(Instant.now());

        GroupState saved = groupStateRepository.save(group);
        groupRouter.publishGroupToMqtt(saved);
        LOG.info("Added zone {} to group {}", zoneName, groupName);
        return saved;
    }

    @Transactional
    public GroupState removeZoneFromGroup(String groupName, String zoneName) {
        GroupState group = groupStateRepository
                .findByName(groupName)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!group.getZones().contains(zoneName)) {
            throw new RuntimeException("Zone not in group");
        }

        group.getZones().remove(zoneName);
        group.setUpdatedAt(Instant.now());

        // If group is now empty, delete it
        if (group.getZones().isEmpty()) {
            groupStateRepository.delete(group);
            groupRouter.publishGroupDeletion(groupName);
            LOG.info("Deleted empty group: {}", groupName);
        } else {
            groupStateRepository.save(group);
            groupRouter.publishGroupToMqtt(group);
            LOG.info("Removed zone {} from group {}", zoneName, groupName);
        }

        // Create single-member group for the removed zone
        createSingleMemberGroup(zoneName);

        return group;
    }

    private void removeZoneFromCurrentGroup(String zoneName) {
        groupStateRepository
                .findByZonesContaining(zoneName)
                .ifPresent(
                        currentGroup -> {
                            currentGroup.getZones().remove(zoneName);

                            if (currentGroup.getZones().isEmpty()) {
                                groupStateRepository.delete(currentGroup);
                                groupRouter.publishGroupDeletion(currentGroup.getName());
                                LOG.debug("Deleted empty group: {}", currentGroup.getName());
                            } else {
                                currentGroup.setUpdatedAt(Instant.now());
                                groupStateRepository.save(currentGroup);
                                groupRouter.publishGroupToMqtt(currentGroup);
                                LOG.debug("Removed zone {} from group {}", zoneName, currentGroup.getName());
                            }
                        });
    }

    private void createSingleMemberGroup(String zoneName) {
        // Create a group named after the zone
        GroupState singleGroup = new GroupState();
        singleGroup.setName(zoneName);
        singleGroup.setDisplayName(toDisplayName(zoneName));
        singleGroup.setZones(Set.of(zoneName));
        singleGroup.setDescription("Auto-created group for " + zoneName);
        singleGroup.setCreatedAt(Instant.now());
        singleGroup.setUpdatedAt(Instant.now());

        GroupState saved = groupStateRepository.save(singleGroup);
        groupRouter.publishGroupToMqtt(saved);
        LOG.debug("Created single-member group for zone: {}", zoneName);
    }

    private void validateZonesExist(Set<String> zones) {
        for (String zoneName : zones) {
            validateZoneExists(zoneName);
        }
    }

    private void validateZoneExists(String zoneName) {
        if (!zoneRepository.existsByName(zoneName)) {
            throw new RuntimeException("Zone not found: " + zoneName);
        }
    }

    private void validateGroupName(String name) {
        if (!name.matches("^[a-z0-9_]+$")) {
            throw new IllegalArgumentException(
                    "Group name must be lowercase alphanumeric with underscores only");
        }
    }

    private String toDisplayName(String name) {
        // Convert "living_room" to "Living Room"
        return Arrays.stream(name.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
