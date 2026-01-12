package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.HashMap;

/**
 * Snapcast-compatible stream URI object.
 */
public class SnapStreamUri {

    @JsonProperty("fragment")
    private String fragment = "";

    @JsonProperty("host")
    private String host = "";

    @JsonProperty("path")
    private String path = "";

    @JsonProperty("query")
    private Map<String, String> query = new HashMap<>();

    @JsonProperty("raw")
    private String raw = "";

    @JsonProperty("scheme")
    private String scheme = "pipe";

    public SnapStreamUri() {
    }

    public SnapStreamUri(String name) {
        this.scheme = "pipe";
        this.path = "/tmp/snapfifo";
        this.raw = "pipe:///tmp/snapfifo?name=" + name;
        this.query.put("name", name);
        this.query.put("codec", "flac");
        this.query.put("sampleformat", "48000:16:2");
        this.query.put("chunk_ms", "20");
    }

    public String getFragment() {
        return fragment;
    }

    public void setFragment(String fragment) {
        this.fragment = fragment;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getQuery() {
        return query;
    }

    public void setQuery(Map<String, String> query) {
        this.query = query;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }
}
