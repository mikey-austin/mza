package net.jackiemclean.mza;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SourceRepositoryTest {

  @Autowired SourceRepository sourceRepository;

  @Test
  public void testOkFetch() {
    var s = sourceRepository.findByName("pcm_source1");
    assertTrue(s.isPresent());
    assertEquals("pcm_source1", s.get().getName());
  }
}
