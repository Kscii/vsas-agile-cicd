package org.soft2412.vsas;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.cli.CommandDispatcher;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.service.SessionService;

public class WhoAmICommandTest {
  private final PrintStream originalOut = System.out;
  private ByteArrayOutputStream out;
  private String previousSessionProperty;
  private Path sessionPath;

  @BeforeEach
  public void setup() throws IOException {
    out = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    cleanupDataDir();
    Files.createDirectories(Path.of("data"));
    sessionPath = Path.of("data", "session.properties");
    previousSessionProperty = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionPath.toString());
  }

  @AfterEach
  public void teardown() throws IOException {
    System.setOut(originalOut);
    cleanupDataDir();
    if (previousSessionProperty == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionProperty);
    }
  }

  @Test
  public void printsGuestWhenNoSession() {
    int code = new CommandDispatcher().dispatch(new String[] {"whoami"});
    assertEquals(0, code);
    assertTrue(out.toString().contains("guest"));
  }

  @Test
  public void printsUsernameWhenLoggedIn() throws IOException {
    writeSession("alice", "u-1", "admin");
    int code = new CommandDispatcher().dispatch(new String[] {"whoami"});
    assertEquals(0, code);
    assertTrue(out.toString().contains("alice (role=admin)"));
  }

  private void writeSession(String username, String idKey, String role) throws IOException {
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User(username, "", "", idKey, role, "", "")), "session write");
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
