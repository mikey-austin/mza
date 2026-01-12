package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.jackiemclean.mza.Zone;
import net.jackiemclean.mza.ZoneState;

/**
 * Snapcast-compatible client object.
 * Maps from MZA Zone + ZoneState.
 */
public class SnapClient {

    @JsonProperty("config")
    private SnapClientConfig config;

    @JsonProperty("connected")
    private boolean connected = true;

    @JsonProperty("host")
    private SnapHost host;

    @JsonProperty("id")
    private String id;

    @JsonProperty("lastSeen")
    private SnapTimestamp lastSeen;

    @JsonProperty("snapclient")
    private SnapClientInfo snapclient;

    public SnapClient() {
        this.config = new SnapClientConfig();
        this.host = new SnapHost();
        this.lastSeen = new SnapTimestamp();
        this.snapclient = new SnapClientInfo();
    }

    /**
     * Create a SnapClient from MZA Zone and ZoneState.
     */
    public static SnapClient fromZone(Zone zone, ZoneState state) {
        SnapClient client = new SnapClient();
        client.id = zone.getName();

        // Host info from zone
        SnapHost host = new SnapHost(zone.getName());
        if (zone.getDescription() != null) {
            host.setOs(zone.getDescription());
        }
        client.host = host;

        // Config from state
        SnapClientConfig config = new SnapClientConfig();
        if (zone.getDescription() != null) {
            config.setName(zone.getDescription());
        } else {
            config.setName(zone.getName());
        }

        if (state != null) {
            SnapVolume volume = new SnapVolume(state.isMuted(), state.getVolume());
            config.setVolume(volume);
            client.connected = true;
        } else {
            config.setVolume(new SnapVolume(true, 0));
            client.connected = false;
        }
        client.config = config;

        client.lastSeen = new SnapTimestamp();
        client.snapclient = new SnapClientInfo();

        return client;
    }

    public SnapClientConfig getConfig() {
        return config;
    }

    public void setConfig(SnapClientConfig config) {
        this.config = config;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public SnapHost getHost() {
        return host;
    }

    public void setHost(SnapHost host) {
        this.host = host;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SnapTimestamp getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(SnapTimestamp lastSeen) {
        this.lastSeen = lastSeen;
    }

    public SnapClientInfo getSnapclient() {
        return snapclient;
    }

    public void setSnapclient(SnapClientInfo snapclient) {
        this.snapclient = snapclient;
    }
}
