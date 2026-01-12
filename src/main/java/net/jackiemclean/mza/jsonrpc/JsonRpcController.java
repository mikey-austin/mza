package net.jackiemclean.mza.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP endpoint for Snapcast JSON-RPC protocol.
 * Handles POST requests to /jsonrpc for one-shot requests.
 */
@RestController
public class JsonRpcController {

    private static final Logger LOG = LoggerFactory.getLogger(JsonRpcController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SnapcastMethodHandler methodHandler;

    @PostMapping(value = "/api/jsonrpc", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonRpcResponse handleRequest(@RequestBody JsonRpcRequest request) {
        LOG.debug("HTTP JSON-RPC request: {}", request.getMethod());

        // Validate JSON-RPC version
        if (!"2.0".equals(request.getJsonrpc())) {
            return JsonRpcResponse.error(request.getId(), JsonRpcError.invalidRequest());
        }

        return methodHandler.handleRequest(request);
    }
}
