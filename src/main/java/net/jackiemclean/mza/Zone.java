package net.jackiemclean.mza;

import jakarta.persistence.*;

@Entity
public class Zone {

  @Column(name = "input_name")
  @Id
  private String name;

  private int volume;
  private boolean isMuted;

  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "left_output_name")),
    @AttributeOverride(name = "device", column = @Column(name = "left_output_device"))
  })
  @Embedded
  private Output leftOutput;

  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "right_output_name")),
    @AttributeOverride(name = "device", column = @Column(name = "right_output_device"))
  })
  @Embedded
  private Output rightOutput;

  @ManyToOne private Source source;

  public Zone() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getVolume() {
    return volume;
  }

  public void setVolume(int volume) {
    this.volume = volume;
  }

  public boolean isMuted() {
    return isMuted;
  }

  public void setMuted(boolean muted) {
    isMuted = muted;
  }

  public Output getLeftOutput() {
    return leftOutput;
  }

  public void setLeftOutput(Output leftOutput) {
    this.leftOutput = leftOutput;
  }

  public Output getRightOutput() {
    return rightOutput;
  }

  public void setRightOutput(Output rightOutput) {
    this.rightOutput = rightOutput;
  }

  public Source getSource() {
    return source;
  }

  public void setSource(Source source) {
    this.source = source;
  }
}
