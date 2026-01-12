package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Snapcast-compatible volume object.
 */
public class SnapVolume {

    @JsonProperty("muted")
    private boolean muted;

    @JsonProperty("percent")
    private int percent;

    public SnapVolume() {
    }

    public SnapVolume(boolean muted, int percent) {
        this.muted = muted;
        this.percent = percent;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }
}
