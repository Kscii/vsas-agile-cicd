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

class HelpCommandTest {

  private PrintStream originalOut;
  private PrintStream originalErr;
  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;
  private Path sessionDir;
  private Path sessionFile;
  private String previousSessionPath;

  @BeforeEach
  void setUp() throws Exception {
    originalOut = System.out;
    originalErr = System.err;
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

    sessionDir = Files.createTempDirectory("vsas-help-session-");
    sessionFile = sessionDir.resolve("session.properties");
    previousSessionPath = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());
    Files.deleteIfExists(sessionFile);
  }

  @AfterEach
  void tearDown() throws Exception {
    System.setOut(originalOut);
    System.setErr(originalErr);
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
  void summaryListsTopLevelCommandsForGuest() {
    CommandRegistry registry = CommandRegistry.withBuiltins();

    int code = new HelpCommand(registry).run(new String[0]);

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Available commands:"), "summary header should appear");
    assertTrue(out.contains("register -"), "register command should be listed");
    assertTrue(out.contains("help -"), "help command should be listed");
    assertFalse(out.contains("upload -"), "guest should not see authenticated commands");
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertEquals("", err);
  }

  @Test
  void summaryIncludesAuthenticatedCommandsWhenLoggedIn() throws Exception {
    CommandRegistry registry = CommandRegistry.withBuiltins();
    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("alice", "a@x", "0400", "ID-1", "USER", "", "")),
        "login should succeed for authenticated summary");

    outBuf.reset();
    errBuf.reset();

    int code = new HelpCommand(registry).run(new String[0]);

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("upload -"), "authenticated user should see upload command");
  }

  @Test
  void restrictedCommandHelpDeniedWhenNotLoggedIn() {
    CommandRegistry registry = CommandRegistry.withBuiltins();

    int code = new HelpCommand(registry).run(new String[] {"upload"});

    assertEquals(1, code);
    assertTrue(
        errBuf.toString(StandardCharsets.UTF_8).contains("Command unavailable for current user"));
    assertEquals("", outBuf.toString(StandardCharsets.UTF_8));
  }

  @Test
  void detailedHelpIncludesUsageFlagsAndExitCodes() {
    CommandRegistry registry = CommandRegistry.withBuiltins();
    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("bob", "b@x", "0401", "ID-2", "USER", "", "")),
        "login required for restricted command details");

    outBuf.reset();
    errBuf.reset();

    int code = new HelpCommand(registry).run(new String[] {"upload"});

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Command: upload"));
    assertTrue(out.contains("Usage: upload --id <sid> --name <name> --file <path>"));
    assertTrue(out.contains("Required flags:"));
    assertTrue(out.contains("Exit codes:"));
    assertTrue(out.contains("0 -> success"));
    assertEquals("", errBuf.toString(StandardCharsets.UTF_8));
  }

  @Test
  void groupedCommandHelpIsSupported() {
    CommandRegistry registry = CommandRegistry.withBuiltins();
    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("carol", "c@x", "0402", "ID-3", "USER", "", "")),
        "login required for grouped command detail");

    outBuf.reset();
    errBuf.reset();

    int code = new HelpCommand(registry).run(new String[] {"scroll", "delete"});

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Command: scroll delete"));
    assertTrue(out.contains("Usage: scroll delete --id <sid> [--yes]"));
    assertTrue(out.contains("Exit codes:"));
    assertTrue(out.contains("1 -> validation or permission error"));
  }

  @Test
  void unknownCommandReturnsValidationError() {
    CommandRegistry registry = CommandRegistry.withBuiltins();

    int code = new HelpCommand(registry).run(new String[] {"__missing__"});

    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Unknown command"));
    assertEquals("", outBuf.toString(StandardCharsets.UTF_8));
  }
}
