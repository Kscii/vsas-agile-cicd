package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.service.SessionService;

class LogoutCommandTest {
  private PrintStream originalOut;
  private PrintStream originalErr;
  private ByteArrayOutputStream outCapture;
  private ByteArrayOutputStream errCapture;
  private Path sessionDir;
  private Path sessionFile;
  private String previousSessionProperty;

  @BeforeEach
  void setup() throws Exception {
    originalOut = System.out;
    originalErr = System.err;
    outCapture = new ByteArrayOutputStream();
    errCapture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outCapture));
    System.setErr(new PrintStream(errCapture));

    sessionDir = Files.createTempDirectory("vsas-session-logout-");
    sessionFile = sessionDir.resolve("session.properties");
    previousSessionProperty = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());

    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("alice", "", "", "ID-1", "USER", "", "")), "session should start");
    assertTrue(Files.exists(sessionFile), "session file must exist before logout");
  }

  @AfterEach
  void teardown() throws Exception {
    System.setOut(originalOut);
    System.setErr(originalErr);
    if (previousSessionProperty == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionProperty);
    }
    if (Files.exists(sessionFile)) {
      Files.delete(sessionFile);
    }
    if (sessionDir != null && Files.exists(sessionDir)) {
      Files.delete(sessionDir);
    }
  }

  @Test
  void logoutClearsSessionFileAndPrintsMessage() {
    LogoutCommand cmd = new LogoutCommand(new SessionService());
    int code = cmd.run(new String[0]);

    assertEquals(0, code);
    assertTrue(outCapture.toString(StandardCharsets.UTF_8).contains("Logout success"));
    assertTrue(errCapture.toString(StandardCharsets.UTF_8).isEmpty());
    assertTrue(Files.notExists(sessionFile), "session file should be removed");
  }
}
