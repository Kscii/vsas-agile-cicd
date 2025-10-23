package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScrollCommandTest {

  private PrintStream origOut;
  private PrintStream origErr;
  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;

  @BeforeEach
  void setUp() {
    origOut = System.out;
    origErr = System.err;
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf));
    System.setErr(new PrintStream(errBuf));
  }

  @AfterEach
  void tearDown() {
    System.setOut(origOut);
    System.setErr(origErr);
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
    int code = new ScrollCommand().run(new String[] {"delete"});
    assertEquals(2, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("delete") || err.contains("delete"));
  }

  @Test
  void updateWithoutArgs_handlesUpdateBranch_exit2() {
    int code = new ScrollCommand().run(new String[] {"update"});
    assertEquals(2, code);
    String combined =
        outBuf.toString(StandardCharsets.UTF_8) + errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(combined.toLowerCase().contains("update"));
  }
}
