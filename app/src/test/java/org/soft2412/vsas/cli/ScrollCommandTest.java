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

class ScrollCommandTest {

  private PrintStream origOut;
  private PrintStream origErr;
  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;
  private Path sessionDir;
  private Path sessionFile;
  private String previousSessionPath;

  @BeforeEach
  void setUp() throws Exception {
    origOut = System.out;
    origErr = System.err;
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf));
    System.setErr(new PrintStream(errBuf));

    sessionDir = Files.createTempDirectory("vsas-scroll-command-");
    sessionFile = sessionDir.resolve("session.properties");
    previousSessionPath = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());
    Files.deleteIfExists(sessionFile);
  }

  @AfterEach
  void tearDown() throws Exception {
    System.setOut(origOut);
    System.setErr(origErr);
    if (previousSessionPath == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionPath);
    }
    if (sessionFile != null) {
      Files.deleteIfExists(sessionFile);
    }
    if (sessionDir != null) {
      Files.deleteIfExists(sessionDir);
    }
  }

  @Test
  void noArgs_printsUsage_exit2() {
    int code = new ScrollCommand().run(new String[] {});
    assertEquals(2, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Usage"));
    assertTrue(out.contains("scroll delete"));
  }

  @Test
  void unknownSubcommand_printsErrorAndUsage_exit2() {
    int code = new ScrollCommand().run(new String[] {"foobar"});
    assertEquals(2, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.toLowerCase().contains("unknown subcommand"));
    assertTrue(out.contains("Usage"));
  }

  @Test
  void deleteWithoutArgs_delegatesToSubcommand_usageExit2() {
    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("alice", "a@x", "0400", "ID-1", "USER", "", "")),
        "login should succeed");

    outBuf.reset();
    errBuf.reset();

    int code = new ScrollCommand().run(new String[] {"delete"});
    assertEquals(2, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("delete") || err.contains("delete"));
  }

  @Test
  void updateWithoutArgs_handlesUpdateBranch_exit2() {
    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("bob", "b@x", "0401", "ID-2", "USER", "", "")),
        "login should succeed");

    outBuf.reset();
    errBuf.reset();

    int code = new ScrollCommand().run(new String[] {"update"});
    assertEquals(2, code);
    String combined =
        outBuf.toString(StandardCharsets.UTF_8) + errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(combined.toLowerCase().contains("update"));
  }
}
