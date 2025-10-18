package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class CommandDispatcherTest {

  @Test
  void noArgs_printsUsage_andReturnsZero() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    int code = new CommandDispatcher().dispatch(new String[0]);
    assertEquals(0, code);
    assertTrue(out.toString().contains("Usage:"));
  }

  @Test
  void unknownCommand_printsError_andReturns2() {
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    int code = new CommandDispatcher().dispatch(new String[]{"undefined"});
    assertEquals(2, code);
    assertTrue(err.toString().toLowerCase().contains("unknown command"));
  }
}
