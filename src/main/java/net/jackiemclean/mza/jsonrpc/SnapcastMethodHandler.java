package net.jackiemclean.mza.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import net.jackiemclean.mza.*;
import net.jackiemclean.mza.snapcast.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Handles all Snapcast JSON-RPC method calls.
 * Maps MZA concepts to Snapcast equivalents:
 * - Zone -> Client
 * - Group -> Group
 * - Source -> Stream
 */
@Service
public class SnapcastMethodHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SnapcastMethodHandler.class);

    @Autowired
    private ZoneRepository zoneRepository;
    @Autowired
    private ZoneStateRepository zoneStateRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private GroupStateRepository groupStateRepository;
    @Autowired
    private GroupService groupService;
    @Autowired
    private ZoneController zoneController;
    @Autowired
    private SnapcastNotificationService notificationService;

    /**
     * Dispatch a JSON-RPC request to the appropriate handler method.
     */
    public JsonRpcResponse handleRequest(JsonRpcRequest request) {
        if (request.getMethod() == null) {
            return JsonRpcResponse.error(request.getId(), JsonRpcError.invalidRequest());
        }

        try {
            Object result = switch (request.getMethod()) {
                // Client methods
                case "Client.GetStatus" -> handleClientGetStatus(request.getParams());
                case "Client.SetVolume" -> handleClientSetVolume(request.getParams());
                case "Client.SetLatency" -> handleClientSetLatency(request.getParams());
                case "Client.SetName" -> handleClientSetName(request.getParams());

                // Group methods
                case "Group.GetStatus" -> handleGroupGetStatus(request.getParams());
                case "Group.SetMute" -> handleGroupSetMute(request.getParams());
                case "Group.SetStream" -> handleGroupSetStream(request.getParams());
                case "Group.SetClients" -> handleGroupSetClients(request.getParams());
                case "Group.SetName" -> handleGroupSetName(request.getParams());

                // Server methods
                case "Server.GetRPCVersion" -> handleServerGetRPCVersion();
                case "Server.GetStatus" -> handleServerGetStatus();
                case "Server.DeleteClient" -> handleServerDeleteClient(request.getParams());

                // Stream methods
                case "Stream.Control" -> handleStreamControl(request.getParams());
                case "Stream.SetProperty" -> handleStreamSetProperty(request.getParams());
                case "Stream.AddStream" -> handleStreamAddStream(request.getParams());
                case "Stream.RemoveStream" -> handleStreamRemoveStream(request.getParams());

                default -> throw new JsonRpcException(JsonRpcError.METHOD_NOT_FOUND, "Method not found");
            };

            return JsonRpcResponse.success(request.getId(), result);
        } catch (JsonRpcException e) {
            return JsonRpcResponse.error(request.getId(), e.getError());
        } catch (Exception e) {
            LOG.error("Error handling request: {}", request.getMethod(), e);
            return JsonRpcResponse.error(request.getId(),
                    JsonRpcError.internalError(e.getMessage()));
        }
    }

    // ===== Client Methods =====

    private Object handleClientGetStatus(JsonNode params) throws JsonRpcException {
        String clientId = requireParam(params, "id").asText();
        Optional<Zone> zone = zoneRepository.findByName(clientId);
        if (zone.isEmpty()) {
            throw new JsonRpcException(JsonRpcError.INVALID_PARAMS, "Client not found");
        }

        ZoneState state = zoneStateRepository.findById(clientId).orElse(null);
        SnapClient client = SnapClient.fromZone(zone.get(), state);

        Map<String, Object> result = new HashMap<>();
        result.put("client", client);
        return result;
    }

    @Transactional
    private Object handleClientSetVolume(JsonNode params) throws JsonRpcException {
        String clientId = requireParam(params, "id").asText();
        JsonNode volumeNode = requireParam(params, "volume");

        int percent = volumeNode.has("percent") ? volumeNode.get("percent").asInt() : 100;
        boolean muted = volumeNode.has("muted") && volumeNode.get("muted").asBoolean();

        // Apply volume change via ZoneController
        zoneController.changeVolume(clientId, percent);
        zoneController.muteZone(clientId, muted);

        // Send notification to other clients
        SnapVolume volume = new SnapVolume(muted, percent);
        notificationService.broadcastClientVolumeChanged(clientId, volume);

        Map<String, Object> result = new HashMap<>();
        result.put("volume", volume);
        return result;
    }

    private Object handleClientSetLatency(JsonNode params) throws JsonRpcException {
        String clientId = requireParam(params, "id").asText();
        int latency = requireParam(params, "latency").asInt();

        // MZA doesn't actually use latency, but we accept the call
        LOG.debug("Client.SetLatency called for {} with latency {}", clientId, latency);

        notificationService.broadcastClientLatencyChanged(clientId, latency);

        Map<String, Object> result = new HashMap<>();
        result.put("latency", latency);
        return result;
    }

    private Object handleClientSetName(JsonNode params) throws JsonRpcException {
        String clientId = requireParam(params, "id").asText();
        String name = requireParam(params, "name").asText();

        // MZA zones are configured in YAML, we can't change them.
        // But we accept the call for compatibility.
        LOG.debug("Client.SetName called for {} with name {}", clientId, name);

        notificationService.broadcastClientNameChanged(clientId, name);

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        return result;
    }

    // ===== Group Methods =====

    private Object handleGroupGetStatus(JsonNode params) throws JsonRpcException {
        String groupId = requireParam(params, "id").asText();
        GroupState groupState = findGroupById(groupId);

        SnapGroup group = buildSnapGroup(groupState);

        Map<String, Object> result = new HashMap<>();
        result.put("group", group);
        return result;
    }

    @Transactional
    private Object handleGroupSetMute(JsonNode params) throws JsonRpcException {
        String groupId = requireParam(params, "id").asText();
        boolean mute = requireParam(params, "mute").asBoolean();

        GroupState groupState = findGroupById(groupId);

        // Mute/unmute all zones in the group
        for (String zoneName : groupState.getZones()) {
            zoneController.muteZone(zoneName, mute);
        }

        notificationService.broadcastGroupMute(groupId, mute);

        Map<String, Object> result = new HashMap<>();
        result.put("mute", mute);
        return result;
    }

    @Transactional
    private Object handleGroupSetStream(JsonNode params) throws JsonRpcException {
        String groupId = requireParam(params, "id").asText();
        String streamId = requireParam(params, "stream_id").asText();

        // Verify stream exists
        if (sourceRepository.findByName(streamId).isEmpty()) {
            throw new JsonRpcException(JsonRpcError.INVALID_PARAMS, "Stream not found");
        }

        GroupState groupState = findGroupById(groupId);

        // Change source for all zones in the group
        for (String zoneName : groupState.getZones()) {
            zoneController.changeSource(zoneName, streamId);
        }

        notificationService.broadcastGroupStreamChanged(groupId, streamId);

        Map<String, Object> result = new HashMap<>();
        result.put("stream_id", streamId);
        return result;
    }

    @Transactional
    private Object handleGroupSetClients(JsonNode params) throws JsonRpcException {
        String groupId = requireParam(params, "id").asText();
        JsonNode clientsNode = requireParam(params, "clients");

        List<String> clientIds = new ArrayList<>();
        if (clientsNode.isArray()) {
            for (JsonNode node : clientsNode) {
                clientIds.add(node.asText());
            }
        }

        // Check if group exists in database
        boolean groupExistsInDb = groupStateRepository.findById(groupId).isPresent()
                || groupStateRepository.findByName(groupId).isPresent();

        if (groupExistsInDb) {
            // Update existing group
            GroupState groupState = findGroupById(groupId);
            groupService.updateGroup(
                    groupState.getName(),
                    groupState.getDisplayName(),
                    new HashSet<>(clientIds),
                    groupState.getDescription());
        } else {
            // Create a new group from clients (implicit group becoming real)
            // Use the first client's zone info for the group name
            String groupName = groupId;
            String displayName = groupId;

            Optional<Zone> zone = zoneRepository.findByName(groupId);
            if (zone.isPresent() && zone.get().getDescription() != null) {
                displayName = zone.get().getDescription();
            }

            groupService.createGroup(groupName, displayName, new HashSet<>(clientIds), null);
        }

        // Return full server status like Snapcast does
        SnapServer server = buildFullServerStatus();

        notificationService.broadcastServerUpdate(server);

        Map<String, Object> result = new HashMap<>();
        result.put("server", server);
        return result;
    }

    private Object handleGroupSetName(JsonNode params) throws JsonRpcException {
        String groupId = requireParam(params, "id").asText();
        String name = requireParam(params, "name").asText();

        // Check if group exists in database
        boolean groupExistsInDb = groupStateRepository.findById(groupId).isPresent()
                || groupStateRepository.findByName(groupId).isPresent();

        if (groupExistsInDb) {
            GroupState groupState = findGroupById(groupId);
            groupService.updateGroup(
                    groupState.getName(),
                    name,
                    groupState.getZones(),
                    groupState.getDescription());
        } else {
            // Create a new group from implicit group (zone name)
            Optional<Zone> zone = zoneRepository.findByName(groupId);
            if (zone.isPresent()) {
                groupService.createGroup(groupId, name, Set.of(groupId), null);
            } else {
                throw new JsonRpcException(JsonRpcError.INVALID_PARAMS, "Group not found");
            }
        }

        notificationService.broadcastGroupNameChanged(groupId, name);

        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        return result;
    }

    // ===== Server Methods =====

    private Object handleServerGetRPCVersion() {
        Map<String, Object> result = new HashMap<>();
        result.put("major", 2);
        result.put("minor", 0);
        result.put("patch", 0);
        return result;
    }

    private Object handleServerGetStatus() {
        SnapServer server = buildFullServerStatus();
        Map<String, Object> result = new HashMap<>();
        result.put("server", server);
        return result;
    }

    @Transactional
    private Object handleServerDeleteClient(JsonNode params) throws JsonRpcException {
        String clientId = requireParam(params, "id").asText();

        // Delete zone state (reset to defaults)
        zoneStateRepository.deleteById(clientId);

        SnapServer server = buildFullServerStatus();

        notificationService.broadcastServerUpdate(server);

        Map<String, Object> result = new HashMap<>();
        result.put("server", server);
        return result;
    }

    // ===== Stream Methods =====

    private Object handleStreamControl(JsonNode params) throws JsonRpcException {
        requireParam(params, "id");
        requireParam(params, "command");

        // MZA doesn't control stream playback
        throw new JsonRpcException(1, "Stream can not be controlled");
    }

    private Object handleStreamSetProperty(JsonNode params) throws JsonRpcException {
        requireParam(params, "id");
        requireParam(params, "property");

        // MZA doesn't set stream properties
        throw new JsonRpcException(-32602, "Property not supported");
    }

    private Object handleStreamAddStream(JsonNode params) throws JsonRpcException {
        // MZA sources are configured in YAML, not dynamically
        throw new JsonRpcException(-32602, "Dynamic stream addition not supported");
    }

    private Object handleStreamRemoveStream(JsonNode params) throws JsonRpcException {
        // MZA sources are configured in YAML, not dynamically
        throw new JsonRpcException(-32602, "Dynamic stream removal not supported");
    }

    // ===== Helper Methods =====

    private JsonNode requireParam(JsonNode params, String name) throws JsonRpcException {
        if (params == null || !params.has(name)) {
            throw new JsonRpcException(JsonRpcError.INVALID_PARAMS,
                    "Parameter '" + name + "' is missing");
        }
        return params.get(name);
    }

    private GroupState findGroupById(String groupId) throws JsonRpcException {
        // First try by ID, then by name
        Optional<GroupState> byId = groupStateRepository.findById(groupId);
        if (byId.isPresent()) {
            return byId.get();
        }

        Optional<GroupState> byName = groupStateRepository.findByName(groupId);
        if (byName.isPresent()) {
            return byName.get();
        }

        // Check if this is an implicit group (zone name as group ID)
        Optional<Zone> zone = zoneRepository.findByName(groupId);
        if (zone.isPresent()) {
            // Create a virtual GroupState for this single zone
            GroupState implicitGroup = new GroupState();
            implicitGroup.setId(groupId);
            implicitGroup.setName(groupId);
            implicitGroup.setDisplayName(zone.get().getDescription() != null
                    ? zone.get().getDescription()
                    : groupId);
            implicitGroup.setZones(Set.of(groupId));
            return implicitGroup;
        }

        throw new JsonRpcException(JsonRpcError.INVALID_PARAMS, "Group not found");
    }

    private SnapGroup buildSnapGroup(GroupState groupState) {
        List<SnapClient> clients = new ArrayList<>();
        String streamId = null;

        for (String zoneName : groupState.getZones()) {
            Optional<Zone> zone = zoneRepository.findByName(zoneName);
            ZoneState state = zoneStateRepository.findById(zoneName).orElse(null);

            if (zone.isPresent()) {
                clients.add(SnapClient.fromZone(zone.get(), state));
                // Use first zone's source as stream_id
                if (streamId == null && state != null && state.getSourceName() != null) {
                    streamId = state.getSourceName();
                }
            }
        }

        return SnapGroup.fromGroupState(groupState, clients, streamId);
    }

    private SnapServer buildFullServerStatus() {
        SnapServer server = new SnapServer();

        // Get all zones that are already in groups
        Set<String> zonesInGroups = new HashSet<>();
        List<GroupState> existingGroups = groupStateRepository.findAll();
        for (GroupState group : existingGroups) {
            zonesInGroups.addAll(group.getZones());
        }

        // Build groups from existing groups
        List<SnapGroup> groups = existingGroups.stream()
                .map(this::buildSnapGroup)
                .collect(Collectors.toList());

        // Create implicit single-zone groups for zones not in any group
        for (Zone zone : zoneRepository.findAll()) {
            if (!zonesInGroups.contains(zone.getName())) {
                // Create an implicit group for this ungrouped zone
                ZoneState state = zoneStateRepository.findById(zone.getName()).orElse(null);
                SnapClient client = SnapClient.fromZone(zone, state);

                String streamId = (state != null && state.getSourceName() != null)
                        ? state.getSourceName()
                        : null;

                SnapGroup implicitGroup = new SnapGroup();
                implicitGroup.setId(zone.getName());
                implicitGroup.setName(zone.getDescription() != null ? zone.getDescription() : zone.getName());
                implicitGroup.setClients(List.of(client));
                implicitGroup.setStreamId(streamId);
                implicitGroup.setMuted(state != null && state.isMuted());

                groups.add(implicitGroup);
            }
        }
        server.setGroups(groups);

        // Build streams from sources
        List<SnapStream> streams = StreamSupport.stream(
                sourceRepository.findAll().spliterator(), false)
                .map(SnapStream::fromSource)
                .collect(Collectors.toList());
        server.setStreams(streams);

        return server;
    }

    /**
     * Exception for JSON-RPC specific errors.
     */
    public static class JsonRpcException extends Exception {
        private final JsonRpcError error;

        public JsonRpcException(int code, String message) {
            super(message);
            this.error = new JsonRpcError(code, message);
        }

        public JsonRpcException(JsonRpcError error) {
            super(error.getMessage());
            this.error = error;
        }

        public JsonRpcError getError() {
            return error;
        }
    }
}
