package net.jackiemclean.mza.jsonrpc;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-RPC 2.0 notification object (no id field).
 * Used for broadcasting events to connected WebSocket clients.
 */
public class JsonRpcNotification {

    @JsonProperty("jsonrpc")
    private final String jsonrpc = "2.0";

    @JsonProperty("method")
    private String method;

    @JsonProperty("params")
    private Object params;

    public JsonRpcNotification() {
    }

    public JsonRpcNotification(String method, Object params) {
        this.method = method;
        this.params = params;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }
}
