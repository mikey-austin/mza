package net.jackiemclean.mza;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
public class GroupState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Name must be lowercase alphanumeric with underscores only")
    private String name;

    @Column(nullable = true)
    private String displayName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "group_zones", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "zone_name")
    private Set<String> zones = new HashSet<>();

    @Column(nullable = true)
    private String description;

    private Instant createdAt;
    private Instant updatedAt;

    public GroupState() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Set<String> getZones() {
        return zones;
    }

    public void setZones(Set<String> zones) {
        this.zones = zones;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
