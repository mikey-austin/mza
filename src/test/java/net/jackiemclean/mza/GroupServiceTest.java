package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupStateRepository groupStateRepository;
    @Mock
    private ZoneRepository zoneRepository;
    @Mock
    private GroupRouter groupRouter;

    @InjectMocks
    private GroupService groupService;

    private GroupState testGroup;
    private Set<String> testZones;

    @BeforeEach
    void setUp() {
        testZones = new HashSet<>(Set.of("living_room", "kitchen"));
        testGroup = new GroupState();
        testGroup.setId("test-id");
        testGroup.setName("test_group");
        testGroup.setDisplayName("Test Group");
        testGroup.setZones(new HashSet<>(testZones));
        testGroup.setDescription("Test description");
        testGroup.setCreatedAt(Instant.now());
        testGroup.setUpdatedAt(Instant.now());
    }

    @Test
    void testCreateGroup_Success() {
        // Arrange
        when(zoneRepository.existsByName("living_room")).thenReturn(true);
        when(zoneRepository.existsByName("kitchen")).thenReturn(true);
        when(groupStateRepository.findByZonesContaining(anyString())).thenReturn(Optional.empty());
        when(groupStateRepository.save(any(GroupState.class))).thenReturn(testGroup);

        // Act
        GroupState result = groupService.createGroup("test_group", "Test Group", testZones, "Test description");

        // Assert
        assertNotNull(result);
        assertEquals("test_group", result.getName());
        assertEquals("Test Group", result.getDisplayName());
        assertEquals(2, result.getZones().size());
        verify(groupStateRepository).save(any(GroupState.class));
        verify(groupRouter).publishGroupToMqtt(any(GroupState.class));
    }

    @Test
    void testCreateGroup_InvalidName() {
        // Arrange
        Set<String> zones = Set.of("living_room");

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> groupService.createGroup("Invalid Name!", null, zones, null));
    }

    @Test
    void testCreateGroup_ZoneNotFound() {
        // Arrange
        when(zoneRepository.existsByName("living_room")).thenReturn(false);

        // Act & Assert
        assertThrows(
                RuntimeException.class,
                () -> groupService.createGroup("test_group", null, testZones, null));
    }

    @Test
    void testCreateGroup_RemovesZonesFromCurrentGroups() {
        // Arrange
        GroupState existingGroup = new GroupState();
        existingGroup.setName("existing_group");
        existingGroup.setZones(new HashSet<>(Set.of("living_room")));

        when(zoneRepository.existsByName(anyString())).thenReturn(true);
        when(groupStateRepository.findByZonesContaining("living_room"))
                .thenReturn(Optional.of(existingGroup));
        when(groupStateRepository.findByZonesContaining("kitchen")).thenReturn(Optional.empty());
        when(groupStateRepository.save(any(GroupState.class))).thenReturn(testGroup);

        // Act
        groupService.createGroup("test_group", "Test Group", testZones, "Test description");

        // Assert
        // When removing zone from existing group, it only saves the existing group (not
        // empty)
        // The single-member group is created separately
        verify(groupStateRepository, atLeast(1)).save(any(GroupState.class));
        verify(groupRouter, atLeast(1)).publishGroupToMqtt(any(GroupState.class));
    }

    @Test
    void testUpdateGroup_Success() {
        // Arrange
        Set<String> newZones = Set.of("living_room", "bathroom");
        when(groupStateRepository.findByName("test_group")).thenReturn(Optional.of(testGroup));
        when(zoneRepository.existsByName(anyString())).thenReturn(true);
        when(groupStateRepository.findByZonesContaining(anyString())).thenReturn(Optional.empty());
        when(groupStateRepository.save(any(GroupState.class))).thenReturn(testGroup);

        // Act
        GroupState result = groupService.updateGroup("test_group", "Updated Name", newZones, "Updated description");

        // Assert
        assertNotNull(result);
        // Should save: 1) updated group, 2) single-member group for removed "kitchen"
        // zone
        verify(groupStateRepository, times(2)).save(any(GroupState.class));
        verify(groupRouter, times(2)).publishGroupToMqtt(any(GroupState.class));
    }

    @Test
    void testUpdateGroup_NotFound() {
        // Arrange
        when(groupStateRepository.findByName("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                RuntimeException.class,
                () -> groupService.updateGroup("nonexistent", null, testZones, null));
    }

    @Test
    void testDeleteGroup_Success() {
        // Arrange
        when(groupStateRepository.findByName("test_group")).thenReturn(Optional.of(testGroup));

        // Act
        groupService.deleteGroup("test_group");

        // Assert
        verify(groupStateRepository).delete(testGroup);
        verify(groupRouter).publishGroupDeletion("test_group");
    }

    @Test
    void testAddZoneToGroup_Success() {
        // Arrange
        when(groupStateRepository.findByName("test_group")).thenReturn(Optional.of(testGroup));
        when(zoneRepository.existsByName("bathroom")).thenReturn(true);
        when(groupStateRepository.findByZonesContaining("bathroom")).thenReturn(Optional.empty());
        when(groupStateRepository.save(any(GroupState.class))).thenReturn(testGroup);

        // Act
        GroupState result = groupService.addZoneToGroup("test_group", "bathroom");

        // Assert
        assertNotNull(result);
        verify(groupStateRepository).save(testGroup);
        verify(groupRouter).publishGroupToMqtt(testGroup);
    }

    @Test
    void testRemoveZoneFromGroup_Success() {
        // Arrange
        when(groupStateRepository.findByName("test_group")).thenReturn(Optional.of(testGroup));

        // Act
        GroupState result = groupService.removeZoneFromGroup("test_group", "kitchen");

        // Assert
        assertNotNull(result);
        verify(groupStateRepository, atLeastOnce()).save(any(GroupState.class));
    }

    @Test
    void testRemoveZoneFromGroup_DeletesEmptyGroup() {
        // Arrange
        GroupState singleZoneGroup = new GroupState();
        singleZoneGroup.setName("single_zone");
        singleZoneGroup.setZones(new HashSet<>(Set.of("living_room")));

        when(groupStateRepository.findByName("single_zone")).thenReturn(Optional.of(singleZoneGroup));

        // Act
        groupService.removeZoneFromGroup("single_zone", "living_room");

        // Assert
        verify(groupStateRepository).delete(singleZoneGroup);
        verify(groupRouter).publishGroupDeletion("single_zone");
    }

    @Test
    void testToDisplayName() {
        // Test via createGroup which uses toDisplayName
        when(zoneRepository.existsByName(anyString())).thenReturn(true);
        when(groupStateRepository.findByZonesContaining(anyString())).thenReturn(Optional.empty());
        when(groupStateRepository.save(any(GroupState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GroupState result = groupService.createGroup("living_room", null, Set.of("living_room"), null);

        assertEquals("Living Room", result.getDisplayName());
    }
}
