package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:/tmp/test_group_mza.db",
        "spring.datasource.initialization-mode=always",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GroupStateRepositoryTest {

    @Autowired
    GroupStateRepository groupStateRepository;

    @BeforeEach
    public void setup() {
        groupStateRepository.deleteAll();
    }

    @Test
    public void testSaveAndFind() {
        // Arrange
        GroupState group = new GroupState();
        group.setName("test_group");
        group.setDisplayName("Test Group");
        group.setZones(Set.of("zone1", "zone2"));
        group.setDescription("Test description");
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());

        // Act
        GroupState saved = groupStateRepository.save(group);
        Optional<GroupState> found = groupStateRepository.findByName("test_group");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("test_group", found.get().getName());
        assertEquals("Test Group", found.get().getDisplayName());
        assertEquals(2, found.get().getZones().size());
        assertTrue(found.get().getZones().contains("zone1"));
        assertTrue(found.get().getZones().contains("zone2"));
    }

    @Test
    public void testFindByZonesContaining() {
        // Arrange
        GroupState group1 = new GroupState();
        group1.setName("group1");
        group1.setZones(Set.of("zone1", "zone2"));
        group1.setCreatedAt(Instant.now());
        group1.setUpdatedAt(Instant.now());

        GroupState group2 = new GroupState();
        group2.setName("group2");
        group2.setZones(Set.of("zone3"));
        group2.setCreatedAt(Instant.now());
        group2.setUpdatedAt(Instant.now());

        groupStateRepository.save(group1);
        groupStateRepository.save(group2);

        // Act
        Optional<GroupState> foundWithZone1 = groupStateRepository.findByZonesContaining("zone1");
        Optional<GroupState> foundWithZone3 = groupStateRepository.findByZonesContaining("zone3");
        Optional<GroupState> notFound = groupStateRepository.findByZonesContaining("zone999");

        // Assert
        assertTrue(foundWithZone1.isPresent());
        assertEquals("group1", foundWithZone1.get().getName());

        assertTrue(foundWithZone3.isPresent());
        assertEquals("group2", foundWithZone3.get().getName());

        assertFalse(notFound.isPresent());
    }

    @Test
    public void testExistsByName() {
        // Arrange
        GroupState group = new GroupState();
        group.setName("existing_group");
        group.setZones(Set.of("zone1"));
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());
        groupStateRepository.save(group);

        // Act & Assert
        assertTrue(groupStateRepository.existsByName("existing_group"));
        assertFalse(groupStateRepository.existsByName("nonexistent_group"));
    }

    @Test
    public void testUpdateGroup() {
        // Arrange
        GroupState group = new GroupState();
        group.setName("test_group");
        group.setDisplayName("Original Name");
        group.setZones(Set.of("zone1"));
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());
        GroupState saved = groupStateRepository.save(group);

        // Act
        saved.setDisplayName("Updated Name");
        saved.setZones(Set.of("zone1", "zone2"));
        saved.setUpdatedAt(Instant.now());
        groupStateRepository.save(saved);

        // Assert
        Optional<GroupState> updated = groupStateRepository.findByName("test_group");
        assertTrue(updated.isPresent());
        assertEquals("Updated Name", updated.get().getDisplayName());
        assertEquals(2, updated.get().getZones().size());
    }

    @Test
    public void testDeleteGroup() {
        // Arrange
        GroupState group = new GroupState();
        group.setName("to_delete");
        group.setZones(Set.of("zone1"));
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());
        groupStateRepository.save(group);

        // Act
        groupStateRepository.delete(group);

        // Assert
        Optional<GroupState> found = groupStateRepository.findByName("to_delete");
        assertFalse(found.isPresent());
    }

    @Test
    public void testFindAll() {
        // Arrange
        GroupState group1 = new GroupState();
        group1.setName("group1");
        group1.setZones(Set.of("zone1"));
        group1.setCreatedAt(Instant.now());
        group1.setUpdatedAt(Instant.now());

        GroupState group2 = new GroupState();
        group2.setName("group2");
        group2.setZones(Set.of("zone2"));
        group2.setCreatedAt(Instant.now());
        group2.setUpdatedAt(Instant.now());

        groupStateRepository.save(group1);
        groupStateRepository.save(group2);

        // Act
        List<GroupState> all = groupStateRepository.findAll();

        // Assert
        assertEquals(2, all.size());
    }

    @Test
    public void testUniqueNameConstraint() {
        // Arrange
        GroupState group1 = new GroupState();
        group1.setName("duplicate_name");
        group1.setZones(Set.of("zone1"));
        group1.setCreatedAt(Instant.now());
        group1.setUpdatedAt(Instant.now());
        groupStateRepository.save(group1);

        GroupState group2 = new GroupState();
        group2.setName("duplicate_name");
        group2.setZones(Set.of("zone2"));
        group2.setCreatedAt(Instant.now());
        group2.setUpdatedAt(Instant.now());

        // Act & Assert
        assertThrows(Exception.class, () -> groupStateRepository.save(group2));
    }
}
