package org.soft2412.vsas;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.service.SessionService;

public class SessionServiceTest {
  @BeforeEach
  public void setup() throws IOException {
    cleanupDataDir();
    Files.createDirectories(Path.of("data"));
  }

  @AfterEach
  public void teardown() throws IOException {
    cleanupDataDir();
  }

  @Test
  public void currentUserParsesSessionJson() throws IOException {
    String json = "{\"username\":\"bob\",\"idKey\":\"u-99\",\"role\":\"user\"}";
    Files.writeString(Path.of("data", "session.json"), json, StandardCharsets.UTF_8);
    SessionService s = new SessionService();
    assertTrue(s.currentUser().isPresent());
    assertEquals("bob", s.currentUser().get().username());
  }

  @Test
  public void logoutDeletesSessionFile() throws IOException {
    Path f = Path.of("data", "session.json");
    Files.createDirectories(f.getParent());
    Files.writeString(f, "{}", StandardCharsets.UTF_8);
    assertTrue(Files.exists(f));
    new SessionService().logout();
    assertFalse(Files.exists(f));
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
