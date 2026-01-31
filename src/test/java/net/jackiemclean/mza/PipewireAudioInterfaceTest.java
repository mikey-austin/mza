package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PipewireAudioInterfaceTest {

    private CommandExecutor commandExecutor;
    private PipewireAudioInterface audioInterface;
    private Zone zone;
    private Source source;
    private ZoneState zoneState;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        commandExecutor = mock(CommandExecutor.class);
        audioInterface = new PipewireAudioInterface(
                "/run/user/1000",
                "pw-dump",
                "pw-link",
                "pw-cli",
                "",        // source link prefix
                "input.",  // zone link prefix
                "output.", // zone props prefix
                commandExecutor);

        zone = new Zone();
        zone.setName("zone6_laundry_room");
        Output lOut = new Output();
        lOut.setName("playback_FL");
        Output rOut = new Output();
        rOut.setName("playback_FR");
        zone.setLeftOutput(lOut);
        zone.setRightOutput(rOut);

        source = new Source();
        source.setName("upnp2");
        Input lIn = new Input();
        lIn.setName("monitor_FL");
        Input rIn = new Input();
        rIn.setName("monitor_FR");
        source.setLeftInput(lIn);
        source.setRightInput(rIn);

        zoneState = new ZoneState();
        zoneState.setVolume(50);
        zoneState.setMuted(false);

        String dump = Files.readString(Path.of("pw-dump.txt"));
        when(commandExecutor.executeAndGetOutput(eq("pw-dump"), anyMap())).thenReturn(List.of(dump.split("\\R")));
    }

    @Test
    void syncUnlinksExistingLinksForZone() throws Exception {
        audioInterface.sync(zone, source, zoneState);

        // New links created
        verify(commandExecutor).execute(contains("pw-link 'upnp2:monitor_FL' 'input.zone6_laundry_room:playback_FL'"), anyMap());
        verify(commandExecutor).execute(contains("pw-link 'upnp2:monitor_FR' 'input.zone6_laundry_room:playback_FR'"), anyMap());

        // Existing links from mpd (node 39) to zone6 (node 67) removed by id
        verify(commandExecutor).execute(contains("pw-link -d 237"), anyMap());
        verify(commandExecutor).execute(contains("pw-link -d 211"), anyMap());
    }

    @Nested
    class ParseNodePortTest {

        @Test
        void parsesSimplePortName() {
            var result = audioInterface.parseNodePort("monitor_FL", "defaultNode");

            assertEquals("defaultNode", result.nodeName());
            assertEquals("monitor_FL", result.portName());
        }

        @Test
        void parsesNodeColonPortFormat() {
            var result = audioInterface.parseNodePort("alsa_input.usb-device:capture_AUX0", "defaultNode");

            assertEquals("alsa_input.usb-device", result.nodeName());
            assertEquals("capture_AUX0", result.portName());
        }

        @Test
        void parsesLongNodeNameWithColon() {
            var result = audioInterface.parseNodePort(
                    "alsa_input.usb-Focusrite_Scarlett_18i20_USB_03000295-00.multichannel-input:capture_AUX0",
                    "defaultNode");

            assertEquals("alsa_input.usb-Focusrite_Scarlett_18i20_USB_03000295-00.multichannel-input", result.nodeName());
            assertEquals("capture_AUX0", result.portName());
        }

        @Test
        void handlesNullInput() {
            var result = audioInterface.parseNodePort(null, "defaultNode");

            assertEquals("defaultNode", result.nodeName());
            assertNull(result.portName());
        }

        @Test
        void handlesColonAtStart() {
            // Colon at position 0 means no node name before it, use default
            var result = audioInterface.parseNodePort(":portOnly", "defaultNode");

            assertEquals("defaultNode", result.nodeName());
            assertEquals(":portOnly", result.portName());
        }

        @Test
        void handlesMultipleColons() {
            // Only first colon is used as separator
            var result = audioInterface.parseNodePort("node:port:extra", "defaultNode");

            assertEquals("node", result.nodeName());
            assertEquals("port:extra", result.portName());
        }
    }
}
