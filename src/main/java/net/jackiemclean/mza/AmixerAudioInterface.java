package net.jackiemclean.mza;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public class AmixerAudioInterface implements AudioInterface {

  private static final Logger LOG = LoggerFactory.getLogger(AmixerAudioInterface.class);

  @Value("${audio.interface.amixer.device:0}")
  private String device;

  @Value("${audio.interface.amixer.commandPath:/usr/bin/amixer}")
  private String amixerCommand;

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
    String leftCmd =
        String.format(
            "%s -c'%s' set '%s %s' %d%%", amixerCommand, device, leftOutput, leftInput, zoneVolume);
    executeAmixerCommand(leftCmd);

    var rightInput = source.getRightInput().getName();
    var rightOutput = zone.getRightOutput().getName();
    String rightCmd =
        String.format(
            "%s -c'%s' set '%s %s' %d%%",
            amixerCommand, device, rightOutput, rightInput, zoneVolume);
    executeAmixerCommand(rightCmd);
  }
}
