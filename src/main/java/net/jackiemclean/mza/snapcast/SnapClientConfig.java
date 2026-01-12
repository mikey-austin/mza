package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Snapcast-compatible client configuration.
 */
public class SnapClientConfig {

    @JsonProperty("instance")
    private int instance = 1;

    @JsonProperty("latency")
    private int latency = 0;

    @JsonProperty("name")
    private String name = "";

    @JsonProperty("volume")
    private SnapVolume volume;

    public SnapClientConfig() {
        this.volume = new SnapVolume();
    }

    public SnapClientConfig(String name, SnapVolume volume) {
        this.name = name != null ? name : "";
        this.volume = volume;
    }

    public int getInstance() {
        return instance;
    }

    public void setInstance(int instance) {
        this.instance = instance;
    }

    public int getLatency() {
        return latency;
    }

    public void setLatency(int latency) {
        this.latency = latency;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public SnapVolume getVolume() {
        return volume;
    }

    public void setVolume(SnapVolume volume) {
        this.volume = volume;
    }
}
