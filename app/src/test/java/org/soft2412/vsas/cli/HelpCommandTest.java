package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HelpCommandTest {

  private PrintStream originalOut;
  private PrintStream originalErr;
  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;

  @BeforeEach
  void setUp() {
    originalOut = System.out;
    originalErr = System.err;
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  void summaryListsTopLevelCommands() {
    CommandRegistry registry = CommandRegistry.withBuiltins();

    int code = new HelpCommand(registry).run(new String[0]);

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Available commands:"), "summary header should appear");
    assertTrue(out.contains("register -"), "register command should be listed");
    assertTrue(out.contains("help -"), "help command should be listed");
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertEquals("", err);
  }

  @Test
  void detailedHelpIncludesUsageFlagsAndExitCodes() {
    CommandRegistry registry = CommandRegistry.withBuiltins();

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
