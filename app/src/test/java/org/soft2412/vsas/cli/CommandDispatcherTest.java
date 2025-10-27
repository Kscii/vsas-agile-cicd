package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class CommandDispatcherTest {

  @Test
  void noArgs_printsHelpSummary_andReturnsZero() throws Exception {
    Path sessionFile = Files.createTempFile("vsas-dispatcher-session-", ".properties");
    Files.deleteIfExists(sessionFile);
    String previousSessionPath = System.getProperty("vsas.session.path");
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    try {
      System.setProperty("vsas.session.path", sessionFile.toString());
      System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

      int code = new CommandDispatcher().dispatch(new String[0]);

      assertEquals(0, code);
      String out = outBuf.toString(StandardCharsets.UTF_8);
      assertTrue(out.contains("Available commands:"), "Should show help summary");
      assertTrue(out.toLowerCase().contains("register"), "Summary should list register");
      assertTrue(out.toLowerCase().contains("help"), "Summary should include help command");
      assertFalse(
          out.toLowerCase().contains("upload"),
          "Guest summary should not display authenticated-only commands");
      assertEquals("", errBuf.toString(StandardCharsets.UTF_8), "No stderr");
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
      if (previousSessionPath == null) {
        System.clearProperty("vsas.session.path");
      } else {
        System.setProperty("vsas.session.path", previousSessionPath);
      }
      Files.deleteIfExists(sessionFile);
    }
  }

  @Test
  void unknownCommand_printsError_andReturns2() {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

      int code = new CommandDispatcher().dispatch(new String[] {"__nope__"});

      assertEquals(2, code);
      assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("unknown command"));
      assertEquals("", outBuf.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }
}
