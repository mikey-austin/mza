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
    var s = sourceRepository.findByName("Spotify");
    assertTrue(s.isPresent());
    assertEquals("Spotify", s.get().getName());
  }
}
