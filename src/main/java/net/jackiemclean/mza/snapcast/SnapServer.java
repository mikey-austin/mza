package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapcast-compatible server status object.
 * Contains groups, server info, and streams.
 */
public class SnapServer {

    @JsonProperty("groups")
    private List<SnapGroup> groups = new ArrayList<>();

    @JsonProperty("server")
    private SnapServerMeta server;

    @JsonProperty("streams")
    private List<SnapStream> streams = new ArrayList<>();

    public SnapServer() {
        this.server = new SnapServerMeta();
    }

    public List<SnapGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<SnapGroup> groups) {
        this.groups = groups;
    }

    public SnapServerMeta getServer() {
        return server;
    }

    public void setServer(SnapServerMeta server) {
        this.server = server;
    }

    public List<SnapStream> getStreams() {
        return streams;
    }

    public void setStreams(List<SnapStream> streams) {
        this.streams = streams;
    }

    /**
     * Server metadata containing host info and snapserver info.
     */
    public static class SnapServerMeta {

        @JsonProperty("host")
        private SnapHost host;

        @JsonProperty("snapserver")
        private SnapServerInfo snapserver;

        public SnapServerMeta() {
            this.host = new SnapHost();
            this.snapserver = new SnapServerInfo();

            // Try to get hostname
            try {
                this.host.setName(java.net.InetAddress.getLocalHost().getHostName());
            } catch (Exception e) {
                this.host.setName("mza-server");
            }
        }

        public SnapHost getHost() {
            return host;
        }

        public void setHost(SnapHost host) {
            this.host = host;
        }

        public SnapServerInfo getSnapserver() {
            return snapserver;
        }

        public void setSnapserver(SnapServerInfo snapserver) {
            this.snapserver = snapserver;
        }
    }
}
