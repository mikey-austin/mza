package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Snapcast server software information.
 */
public class SnapServerInfo {

    @JsonProperty("controlProtocolVersion")
    private int controlProtocolVersion = 1;

    @JsonProperty("name")
    private String name = "MZA";

    @JsonProperty("protocolVersion")
    private int protocolVersion = 1;

    @JsonProperty("version")
    private String version = "0.0.1";

    public SnapServerInfo() {
    }

    public int getControlProtocolVersion() {
        return controlProtocolVersion;
    }

    public void setControlProtocolVersion(int controlProtocolVersion) {
        this.controlProtocolVersion = controlProtocolVersion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
