package org.soft2412.vsas;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;

public class FileScrollRepositoryTest {
  @BeforeEach
  public void setup() throws IOException {
    cleanupDataDir();
  }

  @AfterEach
  public void teardown() throws IOException {
    cleanupDataDir();
  }

  @Test
  public void saveAndFindWorks() {
    Scroll s = new Scroll("idx", "T", "u-1", "2024-01-01T00:00:00Z", "data/files/idx.bin", 0L);
    ScrollRepository repo = new FileScrollRepository();
    assertTrue(repo.save(s));
    assertTrue(repo.existsId("idx"));
    assertTrue(repo.findById("idx").isPresent());
    List<Scroll> all = repo.findAll();
    assertFalse(all.isEmpty());
  }

  @Test
  public void findAllEmptyWhenNoFile() {
    ScrollRepository repo = new FileScrollRepository();
    assertTrue(repo.findAll().isEmpty());
  }

  @Test
  public void parseIgnoresBadRows() throws IOException {
    Path f = Path.of("data", "scrolls.tsv");
    Files.createDirectories(f.getParent());
    Files.writeString(f, "bad-row\n", StandardCharsets.UTF_8);
    assertTrue(new FileScrollRepository().findAll().isEmpty());
  }

  private void cleanupDataDir() throws IOException {
    Path data = Path.of("data");
    if (Files.exists(data)) {
      Files.walk(data)
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignore) {
                }
              });
    }
  }
}
