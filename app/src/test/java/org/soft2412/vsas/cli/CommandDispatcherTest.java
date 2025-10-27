package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class CommandDispatcherTest {

  @Test
  void noArgs_printsHelpSummary_andReturnsZero() {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

      int code = new CommandDispatcher().dispatch(new String[0]);

      assertEquals(0, code);
      String out = outBuf.toString(StandardCharsets.UTF_8);
      assertTrue(out.contains("Available commands:"), "Should show help summary");
      assertTrue(out.toLowerCase().contains("register"), "Summary should list register");
      assertTrue(out.toLowerCase().contains("help"), "Summary should include help command");
      assertEquals("", errBuf.toString(StandardCharsets.UTF_8), "No stderr");
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
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
