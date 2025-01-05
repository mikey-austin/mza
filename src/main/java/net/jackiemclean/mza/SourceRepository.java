package net.jackiemclean.mza;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class SourceRepository {

  private final SourceConfig sourceConfig;

  @Autowired
  public SourceRepository(SourceConfig sourceConfig) {
    this.sourceConfig = sourceConfig;
  }

  public List<Source> findAll() {
    return sourceConfig.getSources();
  }

  public Optional<Source> findByName(String name) {
    return sourceConfig.getSources().stream()
        .filter(source -> source.getName().equals(name))
        .findFirst();
  }
}
