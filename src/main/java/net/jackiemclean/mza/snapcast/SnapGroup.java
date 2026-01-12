package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import net.jackiemclean.mza.GroupState;

/**
 * Snapcast-compatible group object.
 * Maps from MZA GroupState.
 */
public class SnapGroup {

    @JsonProperty("clients")
    private List<SnapClient> clients = new ArrayList<>();

    @JsonProperty("id")
    private String id;

    @JsonProperty("muted")
    private boolean muted = false;

    @JsonProperty("name")
    private String name = "";

    @JsonProperty("stream_id")
    private String streamId;

    public SnapGroup() {
    }

    /**
     * Create a SnapGroup from MZA GroupState.
     * Note: Clients must be populated separately since we need Zone/ZoneState data.
     */
    public static SnapGroup fromGroupState(GroupState groupState, List<SnapClient> clients, String streamId) {
        SnapGroup group = new SnapGroup();
        group.id = groupState.getId();
        group.name = groupState.getDisplayName() != null ? groupState.getDisplayName() : groupState.getName();
        group.clients = clients;
        group.streamId = streamId;

        // Group is muted if all clients are muted
        group.muted = clients.stream()
                .allMatch(c -> c.getConfig().getVolume().isMuted());

        return group;
    }

    public List<SnapClient> getClients() {
        return clients;
    }

    public void setClients(List<SnapClient> clients) {
        this.clients = clients;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }
}
