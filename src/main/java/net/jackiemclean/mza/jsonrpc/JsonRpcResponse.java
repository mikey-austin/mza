package net.jackiemclean.mza.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-RPC 2.0 response object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse {

    @JsonProperty("jsonrpc")
    private final String jsonrpc = "2.0";

    @JsonProperty("result")
    private Object result;

    @JsonProperty("error")
    private JsonRpcError error;

    @JsonProperty("id")
    private Object id;

    public JsonRpcResponse() {
    }

    public static JsonRpcResponse success(Object id, Object result) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.id = id;
        response.result = result;
        return response;
    }

    public static JsonRpcResponse error(Object id, JsonRpcError error) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.id = id;
        response.error = error;
        return response;
    }

    public static JsonRpcResponse notification(String method, Object params) {
        // Notifications are sent with no id
        JsonRpcResponse response = new JsonRpcResponse();
        response.result = null;
        response.error = null;
        // For notifications, we need a different structure - use JsonRpcNotification
        // instead
        return response;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public JsonRpcError getError() {
        return error;
    }

    public void setError(JsonRpcError error) {
        this.error = error;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }
}
