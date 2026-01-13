package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class SourceRepositoryTest {

  @Autowired
  SourceRepository sourceRepository;

  @Test
  public void testOkFetch() {
    var s = sourceRepository.findByName("test_source");
    assertTrue(s.isPresent());
    assertEquals("test_source", s.get().getName());
  }
}
