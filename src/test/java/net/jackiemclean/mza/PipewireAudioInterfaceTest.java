package net.jackiemclean.mza;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PipewireAudioInterfaceTest {

    private CommandExecutor commandExecutor;
    private PipewireAudioInterface audioInterface;
    private Zone zone;
    private Source source;
    private ZoneState zoneState;

    @BeforeEach
    void setUp() {
        commandExecutor = mock(CommandExecutor.class);
        audioInterface = new PipewireAudioInterface("/run/user/1000", commandExecutor);

        // Setup mocks
        zone = new Zone();
        zone.setName("LivingRoom");
        Output lOut = new Output();
        lOut.setName("LivingRoom_L");
        Output rOut = new Output();
        rOut.setName("LivingRoom_R");
        zone.setLeftOutput(lOut);
        zone.setRightOutput(rOut);

        source = new Source();
        source.setName("TV");
        Input lIn = new Input();
        lIn.setName("TV_L");
        Input rIn = new Input();
        rIn.setName("TV_R");
        source.setLeftInput(lIn);
        source.setRightInput(rIn);

        zoneState = new ZoneState();
        zoneState.setVolume(50);
        zoneState.setMuted(false);
    }

    @Test
    void testSync_CreatesMissingLoopbacks() {
        // Mock that nodes do not exist
        when(commandExecutor.executeAndGetOutput(contains("pw-link -i"), anyMap()))
                .thenReturn(Collections.emptyList());

        audioInterface.sync(zone, source, zoneState);

        verify(commandExecutor, atLeastOnce()).execute(contains("pw-loopback"), anyMap());
        // Should create for both if different
        verify(commandExecutor).execute(contains("pw-loopback --name 'LivingRoom_L'"), anyMap());
        verify(commandExecutor).execute(contains("pw-loopback --name 'LivingRoom_R'"), anyMap());
    }

    @Test
    void testSync_SetsVolume() {
        // Mock nodes exist
        when(commandExecutor.executeAndGetOutput(contains("pw-link -i"), anyMap()))
                .thenReturn(List.of("LivingRoom_L:input_FL", "LivingRoom_R:input_FR"));

        audioInterface.sync(zone, source, zoneState);

        verify(commandExecutor).execute(contains("pw-cli set-param 'LivingRoom_L' Props"), anyMap());
        verify(commandExecutor, atLeastOnce()).execute(contains("0.500000"), anyMap());
    }

    @Test
    void testSync_LinksStereo() {
        // Mock successful linking
        when(commandExecutor.executeAndGetOutput(contains("pw-link -i"), anyMap()))
                .thenReturn(List.of("LivingRoom_L:input_FL", "LivingRoom_R:input_FR"));

        audioInterface.sync(zone, source, zoneState);

        // Check Source L -> Zone L
        verify(commandExecutor, atLeastOnce()).execute(contains("pw-link 'TV_L:capture_FL' 'LivingRoom_L:playback_FL'"),
                anyMap());
        // Actually we try multiple suffixes. We can't easily verify WHICH one was
        // called unless we mock throwing exceptions for the others.
        // But we can verify that *some* link command was called with the node names.
        verify(commandExecutor, atLeastOnce()).execute(contains("pw-link 'TV_L:"), anyMap());
    }

    @Test
    void testSync_MonoZone() {
        // Setup Mono Zone (L output name == R output name)
        zone.getRightOutput().setName("LivingRoom_L");

        when(commandExecutor.executeAndGetOutput(contains("pw-link -i"), anyMap()))
                .thenReturn(List.of("LivingRoom_L:input_MONO"));

        audioInterface.sync(zone, source, zoneState);

        // Source L -> Zone
        // Source R -> Zone
        verify(commandExecutor, never()).execute(contains("pw-loopback"), anyMap()); // exists

        // Verify linking
        // Should attempt to link TV_R to LivingRoom_L
        verify(commandExecutor, atLeastOnce()).execute(contains("pw-link 'TV_R:"), anyMap());
    }
}
