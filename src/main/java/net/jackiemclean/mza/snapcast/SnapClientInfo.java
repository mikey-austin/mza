package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Snapcast client software information.
 */
public class SnapClientInfo {

    @JsonProperty("name")
    private String name = "MZA";

    @JsonProperty("protocolVersion")
    private int protocolVersion = 2;

    @JsonProperty("version")
    private String version = "0.0.1";

    public SnapClientInfo() {
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
