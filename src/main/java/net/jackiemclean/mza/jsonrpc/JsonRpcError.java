package net.jackiemclean.mza.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-RPC 2.0 error object with standard error codes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcError {

    // Standard JSON-RPC 2.0 error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    @JsonProperty("code")
    private int code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private Object data;

    public JsonRpcError() {
    }

    public JsonRpcError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public JsonRpcError(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static JsonRpcError parseError() {
        return new JsonRpcError(PARSE_ERROR, "Parse error");
    }

    public static JsonRpcError invalidRequest() {
        return new JsonRpcError(INVALID_REQUEST, "Invalid request");
    }

    public static JsonRpcError methodNotFound() {
        return new JsonRpcError(METHOD_NOT_FOUND, "Method not found");
    }

    public static JsonRpcError invalidParams(String message) {
        return new JsonRpcError(INVALID_PARAMS, message);
    }

    public static JsonRpcError internalError(String message) {
        return new JsonRpcError(INTERNAL_ERROR, message);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
