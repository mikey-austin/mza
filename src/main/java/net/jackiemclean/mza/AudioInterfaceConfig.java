package net.jackiemclean.mza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AudioInterfaceConfig {

  private static final Logger LOG = LoggerFactory.getLogger(AudioInterfaceConfig.class);

  @Value("${audio.interface.debounce.enabled:true}")
  private boolean debounceEnabled;

  @Value("${audio.interface.debounce.quantumMs:500}")
  private long debounceQuantumMs;

  private AudioInterface wrapWithDebounce(AudioInterface raw) {
    if (debounceEnabled) {
      LOG.info("Wrapping audio interface with debouncing (quantum={}ms)", debounceQuantumMs);
      return new DebouncingAudioInterface(raw, debounceQuantumMs);
    }
    return raw;
  }

  @Bean
  @ConditionalOnProperty(name = "audio.interface.backend", havingValue = "DUMMY")
  public AudioInterface dummyInterface() {
    AudioInterface raw = new AudioInterface() {
      @Override
      public void sync(Zone zone, Source source, ZoneState zoneState) {
        LOG.info("Syncing {} with state {}", zone, zoneState);
      }
    };
    return wrapWithDebounce(raw);
  }

  @Bean
  @ConditionalOnProperty(name = "audio.interface.backend", havingValue = "AMIXER")
  public AudioInterface amixer(
      @Value("${audio.interface.amixer.device:0}") String device,
      @Value("${audio.interface.amixer.commandPath:/usr/bin/amixer}") String amixerCommand) {
    return wrapWithDebounce(new AmixerAudioInterface(device, amixerCommand));
  }

  @Bean
  @ConditionalOnProperty(name = "audio.interface.backend", havingValue = "PIPEWIRE")
  public AudioInterface pipewire(
      @Value("${audio.interface.pipewire.runtime-dir:#{null}}") String pipewireRuntimeDir,
      @Value("${audio.interface.pipewire.pw-dump-command:/usr/bin/pw-dump}") String pwDumpCommand,
      @Value("${audio.interface.pipewire.pw-link-command:/usr/bin/pw-link}") String pwLinkCommand,
      @Value("${audio.interface.pipewire.pw-cli-command:/usr/bin/pw-cli}") String pwCliCommand,
      @Value("${audio.interface.pipewire.source-link-prefix:}") String sourceLinkPrefix,
      @Value("${audio.interface.pipewire.zone-link-prefix:input.}") String zoneLinkPrefix,
      @Value("${audio.interface.pipewire.zone-props-prefix:output.}") String zonePropsPrefix,
      CommandExecutor commandExecutor) {
    return wrapWithDebounce(new PipewireAudioInterface(
        pipewireRuntimeDir,
        pwDumpCommand,
        pwLinkCommand,
        pwCliCommand,
        sourceLinkPrefix,
        zoneLinkPrefix,
        zonePropsPrefix,
        commandExecutor));
  }

  @Bean
  @ConditionalOnProperty(name = "audio.interface.backend", havingValue = "PIPEWIRE")
  public CommandExecutor commandExecutor() {
    return new ShellCommandExecutor();
  }
}
