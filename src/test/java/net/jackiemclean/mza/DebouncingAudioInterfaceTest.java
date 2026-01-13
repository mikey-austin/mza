package net.jackiemclean.mza;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DebouncingAudioInterfaceTest {

    private AudioInterface mockDelegate;
    private ScheduledExecutorService testScheduler;
    private DebouncingAudioInterface debouncer;

    private Zone zone;
    private Source source;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(AudioInterface.class);
        testScheduler = Executors.newSingleThreadScheduledExecutor();

        zone = new Zone();
        zone.setName("TestZone");
        Output lOut = new Output();
        lOut.setName("TestZone_L");
        Output rOut = new Output();
        rOut.setName("TestZone_R");
        zone.setLeftOutput(lOut);
        zone.setRightOutput(rOut);

        source = new Source();
        source.setName("TestSource");
        Input lIn = new Input();
        lIn.setName("TestSource_L");
        Input rIn = new Input();
        rIn.setName("TestSource_R");
        source.setLeftInput(lIn);
        source.setRightInput(rIn);
    }

    @Test
    void testSingleSync_DelegatesAfterQuantum() throws Exception {
        debouncer = new DebouncingAudioInterface(mockDelegate, 100, testScheduler);

        ZoneState state = createState(50, false, "src1");
        debouncer.sync(zone, source, state);

        // Should not call delegate immediately
        verify(mockDelegate, never()).sync(any(), any(), any());

        // Wait for quantum to elapse
        Thread.sleep(150);

        verify(mockDelegate, times(1)).sync(eq(zone), eq(source), any(ZoneState.class));
    }

    @Test
    void testRapidSyncs_CoalescesToOne() throws Exception {
        debouncer = new DebouncingAudioInterface(mockDelegate, 100, testScheduler);

        // Send 5 rapid syncs
        for (int i = 0; i < 5; i++) {
            ZoneState state = createState(10 * (i + 1), false, "src1");
            debouncer.sync(zone, source, state);
            Thread.sleep(10); // small delay, but within quantum
        }

        // Wait for quantum to elapse after last call
        Thread.sleep(150);

        // Should only call delegate once
        verify(mockDelegate, times(1)).sync(eq(zone), eq(source), argThat(state -> state.getVolume() == 50 // last value
        ));
    }

    @Test
    void testMerging_LatestValuesWin() throws Exception {
        debouncer = new DebouncingAudioInterface(mockDelegate, 100, testScheduler);

        // First: set volume
        ZoneState state1 = createState(75, false, "src1");
        debouncer.sync(zone, source, state1);

        // Second: set mute (volume should be preserved from merge)
        ZoneState state2 = createState(75, true, "src1");
        debouncer.sync(zone, source, state2);

        Thread.sleep(150);

        verify(mockDelegate, times(1)).sync(eq(zone), eq(source),
                argThat(state -> state.getVolume() == 75 && state.isMuted()));
    }

    @Test
    void testMultipleZones_Independent() throws Exception {
        debouncer = new DebouncingAudioInterface(mockDelegate, 100, testScheduler);

        Zone zone2 = new Zone();
        zone2.setName("Zone2");
        Output l2 = new Output();
        l2.setName("Zone2_L");
        Output r2 = new Output();
        r2.setName("Zone2_R");
        zone2.setLeftOutput(l2);
        zone2.setRightOutput(r2);

        ZoneState state1 = createState(30, false, "src1");
        ZoneState state2 = createState(60, false, "src1");

        debouncer.sync(zone, source, state1);
        debouncer.sync(zone2, source, state2);

        Thread.sleep(150);

        // Both zones should flush independently
        verify(mockDelegate, times(2)).sync(any(), any(), any());
    }

    @Test
    void testCancelsPreviousFlush() throws Exception {
        debouncer = new DebouncingAudioInterface(mockDelegate, 100, testScheduler);

        ZoneState state1 = createState(20, false, "src1");
        debouncer.sync(zone, source, state1);

        Thread.sleep(50); // Half quantum

        ZoneState state2 = createState(80, false, "src1");
        debouncer.sync(zone, source, state2);

        Thread.sleep(150); // Full quantum from second call

        // Only one call, with second value
        verify(mockDelegate, times(1)).sync(eq(zone), eq(source), argThat(state -> state.getVolume() == 80));
    }

    @Test
    void testGracefulShutdown_FlushesAll() throws Exception {
        debouncer = new DebouncingAudioInterface(mockDelegate, 1000, testScheduler); // Long quantum

        ZoneState state = createState(50, false, "src1");
        debouncer.sync(zone, source, state);

        // Call destroy before quantum elapses
        debouncer.destroy();

        // Should have flushed during destroy
        verify(mockDelegate, times(1)).sync(eq(zone), eq(source), any(ZoneState.class));
    }

    @Test
    void testSourceChange_Merged() throws Exception {
        debouncer = new DebouncingAudioInterface(mockDelegate, 100, testScheduler);

        ZoneState state1 = createState(50, false, "source1");
        debouncer.sync(zone, source, state1);

        Source source2 = new Source();
        source2.setName("NewSource");
        Input l2 = new Input();
        l2.setName("NewSource_L");
        Input r2 = new Input();
        r2.setName("NewSource_R");
        source2.setLeftInput(l2);
        source2.setRightInput(r2);

        ZoneState state2 = createState(50, false, "source2");
        debouncer.sync(zone, source2, state2);

        Thread.sleep(150);

        // Should use latest source context
        verify(mockDelegate, times(1)).sync(eq(zone), eq(source2),
                argThat(state -> "source2".equals(state.getSourceName())));
    }

    private ZoneState createState(int volume, boolean muted, String sourceName) {
        ZoneState state = new ZoneState();
        state.setName(zone.getName());
        state.setVolume(volume);
        state.setMuted(muted);
        state.setSourceName(sourceName);
        return state;
    }
}
