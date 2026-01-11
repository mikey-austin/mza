package net.jackiemclean.mza;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public class UpdateGroupRequest {

    private String displayName;

    @NotNull
    private Set<String> zones;

    private String description;

    public UpdateGroupRequest() {
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
}
