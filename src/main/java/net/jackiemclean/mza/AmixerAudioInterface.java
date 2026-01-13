package net.jackiemclean.mza;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmixerAudioInterface implements AudioInterface {

  private static final Logger LOG = LoggerFactory.getLogger(AmixerAudioInterface.class);

  private final String device;
  private final String amixerCommand;

  public AmixerAudioInterface(String device, String amixerCommand) {
    this.device = device;
    this.amixerCommand = amixerCommand;
  }

  private void executeAmixerCommand(String command) {
    LOG.debug("Running -> {}", command);
    try {
      ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
      pb.redirectErrorStream(true); // Combine error and output streams
      Process process = pb.start();
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        LOG.debug("Command [{}] produced the following output: {}", command, line);
      }
      process.waitFor();
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute amixer command: " + command, e);
    }
  }

  /** Sync the source of truth (the zone state) with the audio interface. */
  @Override
  public void sync(Zone zone, Source source, ZoneState zoneState) {
    LOG.debug("syncing zone {}: {}", zone, zoneState);
    int zoneVolume = zoneState.isMuted() ? 0 : zoneState.getVolume();

    var leftOutput = zone.getLeftOutput().getName();
    var leftInput = source.getLeftInput().getName();
    var rightOutput = zone.getRightOutput().getName();
    var rightInput = source.getRightInput().getName();

    // Combine both channel commands into a single shell invocation
    String combinedCmd = String.format(
        "%s -c'%s' set '%s %s' %d%%; %s -c'%s' set '%s %s' %d%%",
        amixerCommand, device, leftOutput, leftInput, zoneVolume,
        amixerCommand, device, rightOutput, rightInput, zoneVolume);
    executeAmixerCommand(combinedCmd);
  }
}
