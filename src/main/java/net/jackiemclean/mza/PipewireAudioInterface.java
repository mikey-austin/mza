package net.jackiemclean.mza;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PipeWire backend: sets zone volume/mute via pw-cli, and keeps links
 * left-to-left, right-to-right using pw-link. Operates by reconciling the
 * PipeWire graph (via pw-dump) to the desired state on each sync.
 */
public class PipewireAudioInterface implements AudioInterface {

    private static final Logger LOG = LoggerFactory.getLogger(PipewireAudioInterface.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CommandExecutor commandExecutor;
    private final Map<String, String> stringEnv;

    private final String pwDumpCommand;
    private final String pwLinkCommand;
    private final String pwCliCommand;
    private final String sourceLinkPrefix;
    private final String zoneLinkPrefix;
    private final String zonePropsPrefix;

    /** Cache node.name -> id to avoid repeated lookups. */
    private final Map<String, Integer> nodeIdCache = new ConcurrentHashMap<>();

    public PipewireAudioInterface(
            String pipewireRuntimeDir,
            String pwDumpCommand,
            String pwLinkCommand,
            String pwCliCommand,
            String sourceLinkPrefix,
            String zoneLinkPrefix,
            String zonePropsPrefix,
            CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
        this.pwDumpCommand = pwDumpCommand;
        this.pwLinkCommand = pwLinkCommand;
        this.pwCliCommand = pwCliCommand;
        this.sourceLinkPrefix = sourceLinkPrefix == null ? "" : sourceLinkPrefix;
        this.zoneLinkPrefix = zoneLinkPrefix == null ? "" : zoneLinkPrefix;
        this.zonePropsPrefix = zonePropsPrefix == null ? "" : zonePropsPrefix;

        this.stringEnv = new HashMap<>();
        if (pipewireRuntimeDir != null && !pipewireRuntimeDir.isBlank()) {
            this.stringEnv.put("PIPEWIRE_RUNTIME_DIR", pipewireRuntimeDir);
            this.stringEnv.put("XDG_RUNTIME_DIR", pipewireRuntimeDir); // Fallback usually needed
        } else {
            String xdg = System.getenv("XDG_RUNTIME_DIR");
            if (xdg != null) {
                this.stringEnv.put("PIPEWIRE_RUNTIME_DIR", xdg);
                this.stringEnv.put("XDG_RUNTIME_DIR", xdg);
            }
        }
    }

    @Override
    public void sync(Zone zone, Source source, ZoneState zoneState) {
        LOG.debug("Syncing zone {} (Source: {}, State: {})", zone.getName(), source.getName(), zoneState);

        JsonNode dump = loadDump();

        String zonePropsNodeName = withPrefix(zone.getName(), zonePropsPrefix);
        String zoneLinkNodeName = withPrefix(zone.getName(), zoneLinkPrefix);
        String sourceLinkNodeName = withPrefix(source.getName(), sourceLinkPrefix);

        // Apply mute/volume on the zone node
        int zoneNodeId = resolveNodeId(dump, zonePropsNodeName);
        applyMuteAndVolume(zoneNodeId, zoneState);

        // Reconcile each channel independently
        reconcileChannel(dump, sourceLinkNodeName, source.getLeftInput().getName(),
                zoneLinkNodeName, zone.getLeftOutput().getName(), zone.getName());
        reconcileChannel(dump, sourceLinkNodeName, source.getRightInput().getName(),
                zoneLinkNodeName, zone.getRightOutput().getName(), zone.getName());
    }

    private String withPrefix(String rawName, String prefix) {
        if (rawName == null) {
            throw new IllegalArgumentException("Node name cannot be null");
        }
        return rawName.startsWith(prefix) ? rawName : prefix + rawName;
    }

    private int resolveNodeId(JsonNode dump, String nodeName) {
        Integer cached = nodeIdCache.get(nodeName);
        if (cached != null) {
            return cached;
        }

        for (JsonNode entry : dump) {
            if (!entry.has("type") || !"PipeWire:Interface:Node".equals(entry.get("type").asText())) {
                continue;
            }
            JsonNode info = entry.get("info");
            if (info == null) {
                continue;
            }
            JsonNode props = info.get("props");
            if (props != null) {
                JsonNode nameNode = props.get("node.name");
                if (nameNode != null && nodeName.equals(nameNode.asText())) {
                    int id = entry.get("id").asInt();
                    nodeIdCache.put(nodeName, id);
                    return id;
                }
            }
        }

        throw new IllegalStateException("Unable to resolve PipeWire node id for " + nodeName);
    }

    private Integer findPortId(JsonNode dump, int nodeId, String portName, String direction) {
        for (JsonNode entry : dump) {
            if (!entry.has("type") || !"PipeWire:Interface:Port".equals(entry.get("type").asText())) {
                continue;
            }
            JsonNode info = entry.get("info");
            if (info == null || info.get("props") == null) {
                continue;
            }
            JsonNode props = info.get("props");
            JsonNode nodeIdNode = props.get("node.id");
            JsonNode portIdNode = props.get("port.id");
            JsonNode portNameNode = props.get("port.name");
            JsonNode dirNode = props.get("port.direction");
            if (nodeIdNode == null || portIdNode == null || portNameNode == null || dirNode == null) {
                continue;
            }
            if (nodeIdNode.asInt() == nodeId && portName.equals(portNameNode.asText())
                    && direction.equals(dirNode.asText())) {
                return portIdNode.asInt();
            }
        }
        return null;
    }

    private void applyMuteAndVolume(int nodeId, ZoneState zoneState) {
        boolean muted = zoneState.isMuted();
        float volume = Math.max(0, Math.min(100, zoneState.getVolume())) / 100.0f;

        String muteCmd = String.format("%s set-param %d Props '{ node.mute = %s }'",
                pwCliCommand,
                nodeId,
                muted ? "true" : "false");
        commandExecutor.execute(muteCmd, stringEnv);

        String volumeCmd = String.format("%s set-param %d Props '{ node.volume = %.4f }'",
                pwCliCommand,
                nodeId,
                volume);
        commandExecutor.execute(volumeCmd, stringEnv);
    }

    private void reconcileChannel(JsonNode dump, String sourceNode, String sourcePort, String zoneNode,
            String zonePort, String zoneName) {
        if (sourcePort == null || zonePort == null) {
            LOG.warn("Skipping link: missing port (sourcePort={}, zonePort={})", sourcePort, zonePort);
            return;
        }

        int sourceNodeId = resolveNodeId(dump, sourceNode);
        int zoneNodeId = resolveNodeId(dump, zoneNode);

        Integer sourcePortId = findPortId(dump, sourceNodeId, sourcePort, "out");
        Integer zonePortId = findPortId(dump, zoneNodeId, zonePort, "in");
        if (sourcePortId == null || zonePortId == null) {
            LOG.warn("Missing port ids for {}:{} -> {}:{}, skipping link", sourceNode, sourcePort, zoneNode, zonePort);
            return;
        }

        List<Integer> wrongLinks = new java.util.ArrayList<>();
        boolean desiredExists = false;
        for (JsonNode entry : dump) {
            if (!entry.has("type") || !"PipeWire:Interface:Link".equals(entry.get("type").asText())) {
                continue;
            }
            JsonNode info = entry.get("info");
            if (info == null) {
                continue;
            }
            JsonNode inNode = info.get("input-node-id");
            JsonNode inPort = info.get("input-port-id");
            JsonNode outNode = info.get("output-node-id");
            JsonNode outPort = info.get("output-port-id");
            if (inNode == null || inPort == null || outNode == null || outPort == null) {
                continue;
            }
            if (inNode.asInt() == zoneNodeId && inPort.asInt() == zonePortId) {
                if (outNode.asInt() == sourceNodeId && outPort.asInt() == sourcePortId) {
                    desiredExists = true;
                } else {
                    wrongLinks.add(entry.get("id").asInt());
                }
            }
        }

        // Create link if missing
        if (!desiredExists) {
            String fullSource = sourceNode + ":" + sourcePort;
            String fullZone = zoneNode + ":" + zonePort;
            String cmd = String.format("%s '%s' '%s' 2>/dev/null || true", pwLinkCommand, fullSource, fullZone);
            try {
                commandExecutor.execute(cmd, stringEnv);
            } catch (Exception e) {
                LOG.error("Failed to link {} -> {}", fullSource, fullZone, e);
            }
        }

        // Remove any other links into this zone port
        unlinkById(wrongLinks, zoneName);
    }

    private void unlinkById(List<Integer> linkIds, String zoneName) {
        for (int linkId : linkIds) {
            String unlinkCmd = String.format("%s -d %d 2>/dev/null || true", pwLinkCommand, linkId);
            try {
                commandExecutor.execute(unlinkCmd, stringEnv);
            } catch (Exception e) {
                LOG.warn("Failed to unlink link {} for zone {}", linkId, zoneName, e);
            }
        }
    }

    private JsonNode loadDump() {
        try {
            List<String> lines = commandExecutor.executeAndGetOutput(pwDumpCommand, stringEnv);
            String json = String.join("\n", lines);
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load/parse pw-dump output", e);
        }
    }
}
