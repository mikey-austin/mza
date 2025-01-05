package net.jackiemclean.mza;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmixerAudioInterface implements AudioInterface {

  private static final Logger LOG = LoggerFactory.getLogger(AmixerAudioInterface.class);

  private void executeAmixerCommand(String command) {
    LOG.debug("Running -> {}", command);
    try {
      Process process = Runtime.getRuntime().exec(command);
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        System.out.println(line);
      }
      process.waitFor();
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute amixer command: " + command, e);
    }
  }

  /**
   * Percentages are more natural to configure however ALSA expects an integer on a logarithmic
   * scale between 0 and 172.
   */
  private int percentageToAlsaVolume(int percentage) {
    if (percentage <= 0) return 0; // Mute
    if (percentage >= 100) return 172; // Max
    return (int) (172 * Math.pow(percentage / 100.0, 2)); // Logarithmic scale
  }

  /** Sync the source of truth (the zone state) with the audio interface. */
  @Override
  public void sync(Zone zone, Source source, ZoneState zoneState) {
    LOG.debug("syncing zone {}: {}", zone, zoneState);
    int zoneVolume = zoneState.isMuted() ? 0 : percentageToAlsaVolume(zoneState.getVolume());

    var leftOutput = zone.getLeftOutput().getName();
    var leftInput = source.getLeftInput().getName();
    String leftCmd =
        String.format("amixer set '%s Input %s' %d", leftOutput, leftInput, zoneVolume);
    executeAmixerCommand(leftCmd);

    var rightInput = source.getRightInput().getName();
    var rightOutput = zone.getRightOutput().getName();
    String rightCmd =
        String.format("amixer set '%s Input %s' %d", rightOutput, rightInput, zoneVolume);
    executeAmixerCommand(rightCmd);
  }
}
