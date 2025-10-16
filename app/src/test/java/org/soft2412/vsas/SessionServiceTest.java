package org.soft2412.vsas;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.service.SessionService;

public class SessionServiceTest {
  private String previousSessionProperty;
  private Path sessionPath;

  @BeforeEach
  public void setup() throws IOException {
    cleanupDataDir();
    Files.createDirectories(Path.of("data"));
    sessionPath = Path.of("data", "session.properties");
    previousSessionProperty = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionPath.toString());
  }

  @AfterEach
  public void teardown() throws IOException {
    cleanupDataDir();
    if (previousSessionProperty == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionProperty);
    }
  }

  @Test
  public void currentUserParsesSessionProperties() throws IOException {
    Properties props = new Properties();
    props.setProperty("username", "bob");
    props.setProperty("idKey", "u-99");
    props.setProperty("role", "user");
    props.setProperty("issuedAt", "2025-10-16T00:00:00Z");
    try (Writer writer = Files.newBufferedWriter(sessionPath, StandardCharsets.UTF_8)) {
      props.store(writer, "test");
    }
    SessionService s = new SessionService();
    assertTrue(s.currentUser().isPresent());
    assertEquals("bob", s.currentUser().get().username());
  }

  @Test
  public void logoutDeletesSessionFile() throws IOException {
    Files.createDirectories(sessionPath.getParent());
    Files.writeString(sessionPath, "username=alice", StandardCharsets.UTF_8);
    assertTrue(Files.exists(sessionPath));
    new SessionService().logout();
    assertFalse(Files.exists(sessionPath));
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
