package net.jackiemclean.mza;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/**
 * This class represents an audio source.
 *
 * <p>Sources contain inputs and represent logical sources like TVs, PCMs, etc.
 */
@Entity
public class Source {

  @Column(name = "source_name")
  @Id
  private String name;

  @JsonProperty("left")
  @JsonAlias("leftInput")
  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "left_input_name")),
  })
  @Embedded
  private Input leftInput;

  @JsonProperty("right")
  @JsonAlias("rightInput")
  @AttributeOverrides({
    @AttributeOverride(name = "name", column = @Column(name = "right_input_name")),
  })
  @Embedded
  private Input rightInput;

  public Source() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Input getLeftInput() {
    return leftInput;
  }

  public void setLeftInput(Input leftInput) {
    this.leftInput = leftInput;
  }

  public void setLeft(Input left) {
    this.leftInput = left;
  }

  public Input getRightInput() {
    return rightInput;
  }

  public void setRightInput(Input rightInput) {
    this.rightInput = rightInput;
  }

  public void setRight(Input right) {
    this.rightInput = right;
  }
}
