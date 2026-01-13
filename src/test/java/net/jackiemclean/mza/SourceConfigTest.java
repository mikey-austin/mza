package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SourceConfigTest {

  @Autowired
  private SourceConfig sourceConfig;

  @Test
  void testSourcesLoaded() {
    List<Source> sources = sourceConfig.getSources();
    assertNotNull(sources, "Sources should not be null");
    assertFalse(sources.isEmpty(), "Sources should not be empty");
  }
}
