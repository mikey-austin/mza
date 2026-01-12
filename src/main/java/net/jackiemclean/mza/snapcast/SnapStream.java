package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.HashMap;
import net.jackiemclean.mza.Source;

/**
 * Snapcast-compatible stream object.
 * Maps from MZA Source.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SnapStream {

    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private String status = "idle";

    @JsonProperty("uri")
    private SnapStreamUri uri;

    @JsonProperty("properties")
    private Map<String, Object> properties;

    public SnapStream() {
        this.uri = new SnapStreamUri();
    }

    /**
     * Create a SnapStream from MZA Source.
     */
    public static SnapStream fromSource(Source source) {
        SnapStream stream = new SnapStream();
        stream.id = source.getName();
        stream.uri = new SnapStreamUri(source.getName());
        stream.status = "idle"; // MZA doesn't track stream status

        // Basic properties
        stream.properties = new HashMap<>();
        stream.properties.put("canGoNext", false);
        stream.properties.put("canGoPrevious", false);
        stream.properties.put("canPlay", false);
        stream.properties.put("canPause", false);
        stream.properties.put("canSeek", false);
        stream.properties.put("canControl", false);

        return stream;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public SnapStreamUri getUri() {
        return uri;
    }

    public void setUri(SnapStreamUri uri) {
        this.uri = uri;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
