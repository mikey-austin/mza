package net.jackiemclean.mza.snapcast;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Snapcast-compatible timestamp (seconds and microseconds).
 */
public class SnapTimestamp {

    @JsonProperty("sec")
    private long sec;

    @JsonProperty("usec")
    private long usec;

    public SnapTimestamp() {
        long now = System.currentTimeMillis();
        this.sec = now / 1000;
        this.usec = (now % 1000) * 1000;
    }

    public SnapTimestamp(long epochMillis) {
        this.sec = epochMillis / 1000;
        this.usec = (epochMillis % 1000) * 1000;
    }

    public long getSec() {
        return sec;
    }

    public void setSec(long sec) {
        this.sec = sec;
    }

    public long getUsec() {
        return usec;
    }

    public void setUsec(long usec) {
        this.usec = usec;
    }
}
