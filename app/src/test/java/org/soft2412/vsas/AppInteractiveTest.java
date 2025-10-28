package org.soft2412.vsas;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AppInteractiveTest {
  @Test
  void interactiveLoopDispatchesCommands() {
    ByteArrayInputStream in =
        new ByteArrayInputStream(
            "upload --id s1 --name \"My Scroll\" --file path\nexit\n"
                .getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

    RecordingInvoker invoker = new RecordingInvoker(3);
    App app = new App(invoker);

    int exitCode =
        app.run(new String[0], in, new PrintStream(outBuffer), new PrintStream(errBuffer));

    assertEquals(3, exitCode);
    assertEquals(1, invoker.calls.size());
    assertArrayEquals(
        new String[] {"upload", "--id", "s1", "--name", "My Scroll", "--file", "path"},
        invoker.calls.get(0));
    String prompts = outBuffer.toString(StandardCharsets.UTF_8);
    assertTrue(
        prompts.startsWith("Welcome to VSAS CLI."),
        "Should print welcome banner once before prompts");
    assertEquals(2, countOccurrences(prompts, "vsas> "));
    assertEquals("", errBuffer.toString(StandardCharsets.UTF_8));
  }

  @Test
  void interactiveLoopReportsUnmatchedQuotes() {
    ByteArrayInputStream in =
        new ByteArrayInputStream(
            "list --filter \"unfinished\nexit\n".getBytes(StandardCharsets.UTF_8));
    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

    RecordingInvoker invoker = new RecordingInvoker(0);
    App app = new App(invoker);

    int exitCode =
        app.run(new String[0], in, new PrintStream(outBuffer), new PrintStream(errBuffer));

    assertEquals(2, exitCode);
    assertTrue(invoker.calls.isEmpty());
    String outText = outBuffer.toString(StandardCharsets.UTF_8);
    assertTrue(outText.contains("Welcome to VSAS CLI."));
    String errOutput = errBuffer.toString(StandardCharsets.UTF_8);
    assertTrue(errOutput.contains("Invalid input: unmatched quotes."));
  }

  @Test
  void runWithArgsDelegatesToDispatcher() {
    RecordingInvoker invoker = new RecordingInvoker(0);
    App app = new App(invoker);

    String[] args = {"whoami"};
    int exitCode =
        app.run(
            args,
            new ByteArrayInputStream(new byte[0]),
            new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream()));

    assertEquals(0, exitCode);
    assertEquals(1, invoker.calls.size());
    assertArrayEquals(args, invoker.calls.get(0));
  }

  private static int countOccurrences(String text, String needle) {
    int fromIndex = 0;
    int count = 0;
    while (true) {
      int idx = text.indexOf(needle, fromIndex);
      if (idx < 0) {
        return count;
      }
      count++;
      fromIndex = idx + needle.length();
    }
  }

  private static final class RecordingInvoker implements App.CommandInvoker {
    private final java.util.List<String[]> calls = new java.util.ArrayList<>();
    private final int codeToReturn;

    private RecordingInvoker(int codeToReturn) {
      this.codeToReturn = codeToReturn;
    }

    @Override
    public int dispatch(String[] args) {
      calls.add(args);
      return codeToReturn;
    }
  }
}
