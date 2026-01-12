package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Snapcast-compatible host information.
 */
public class SnapHost {

    @JsonProperty("arch")
    private String arch = "x86_64";

    @JsonProperty("ip")
    private String ip = "";

    @JsonProperty("mac")
    private String mac = "";

    @JsonProperty("name")
    private String name = "";

    @JsonProperty("os")
    private String os = "Linux";

    public SnapHost() {
    }

    public SnapHost(String name) {
        this.name = name != null ? name : "";
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }
}
