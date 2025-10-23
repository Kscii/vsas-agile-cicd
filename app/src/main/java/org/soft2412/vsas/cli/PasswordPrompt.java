package org.soft2412.vsas.cli;

import java.io.Console;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Masked password prompt with a test seam and best-effort fallback when Console is unavailable.
 *
 * <p>Usage:
 *
 * <pre>
 * char[] secret = PasswordPrompt.read(System.out, "Password: ");
 * // ... use secret, then zero it
 * </pre>
 */
public final class PasswordPrompt {

  /** Test hook: if set, always used. */
  private static volatile Provider OVERRIDE;

  /** Minimal provider interface for test injection. */
  public interface Provider {
    /** Return a non-null char[] (may be empty). */
    char[] readMasked(PrintStream out, String prompt) throws IOException;
  }

  /** Set a test provider; pass null to clear. */
  public static void setTestProvider(Provider p) {
    OVERRIDE = p;
  }

  /** Read a masked password; returns non-null char[] (may be empty). */
  public static char[] read(PrintStream out, String prompt) throws IOException {
    Provider p = OVERRIDE;
    if (p != null) {
      return p.readMasked(out, prompt);
    }

    Console console = System.console();
    if (console != null) {
      if (out != null) {
        out.print(prompt);
        out.flush();
      }
      char[] pwd = console.readPassword(); // masked by JVM
      return pwd != null ? pwd : new char[0];
    }

    // Fallback path: attempt to disable echo via stty if running on a real TTY.
    // If that fails (e.g., CI), read with echo-on to avoid blocking.
    if (out != null) {
      out.print(prompt);
      out.flush();
    }
    boolean echoOff = false;
    try {
      echoOff = stty("-echo");
      return readLineChars();
    } finally {
      if (echoOff) {
        try {
          System.out.println(); // mimic Console.readPassword() newline
          stty("echo");
        } catch (Exception ignore) {
        }
      }
    }
  }

  private static boolean stty(String arg) {
    try {
      Process p =
          new ProcessBuilder("sh", "-c", "stty " + arg + " < /dev/tty")
              .redirectErrorStream(true)
              .start();
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  private static char[] readLineChars() throws IOException {
    StringBuilder sb = new StringBuilder();
    while (true) {
      int ch = System.in.read();
      if (ch == -1 || ch == '\n' || ch == '\r') break;
      sb.append((char) ch);
    }
    return sb.toString().toCharArray();
  }

  private PasswordPrompt() {}
}
