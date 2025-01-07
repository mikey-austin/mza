package net.jackiemclean.mza;

import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

  @Autowired private SourceRepository sourceRepository;

  @GetMapping
  public Collection<Source> getAllSources() {
    return sourceRepository.findAll();
  }
}
