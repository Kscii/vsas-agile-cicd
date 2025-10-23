package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.security.PasswordHasher;

public class RegisterCommandInteractiveTest {

  private Path dataDir;
  private Path usersTsv;

  @BeforeEach
  void setup() throws Exception {
    dataDir = Path.of("data");
    usersTsv = dataDir.resolve("users.tsv");
    if (Files.exists(usersTsv)) Files.delete(usersTsv);
    File d = dataDir.toFile();
    if (d.exists()) d.delete();
    PasswordPrompt.setTestProvider(null);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (Files.exists(usersTsv)) Files.delete(usersTsv);
    File d = dataDir.toFile();
    if (d.exists()) d.delete();
    PasswordPrompt.setTestProvider(null);
  }

  @Test
  void register_withoutPassword_promptsTwice_andSucceeds_whenMatch() throws Exception {
    // Arrange test provider to return same password twice
    final char[][] answers = {"Secret1!".toCharArray(), "Secret1!".toCharArray()};
    final int[] idx = {0};
    PasswordPrompt.setTestProvider(
        (out, prompt) -> {
          if (out != null) {
            out.print(prompt);
            out.flush();
          }
          return answers[Math.min(idx[0]++, answers.length - 1)];
        });

    var outBuf = new ByteArrayOutputStream();
    var errBuf = new ByteArrayOutputStream();
    var cmd =
        new RegisterCommand(new PrintStream(outBuf), new PrintStream(errBuf), new PasswordHasher());

    int code =
        cmd.run(
            new String[] {
              "--username", "bob",
              "--email", "bob@example.com",
              "--phone", "0400",
              "--id-key", "K-INT-1"
            });

    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Registered user bob"));
    assertTrue(Files.exists(usersTsv));

    String content = Files.readString(usersTsv, StandardCharsets.UTF_8);
    assertTrue(
        content.startsWith("username\temail\tphone\tidKey\trole\tpasswordHash\tsalt\tcreatedAt"),
        "header must be present");
    assertTrue(content.contains("bob\t"));
    assertFalse(content.contains("Secret1!"), "plaintext password must not appear");

    // Validate hash/salt formats on last row
    String[] lines = content.split("\\R");
    String last = lines[lines.length - 1];
    String[] cols = last.split("\\t", -1);
    assertEquals(8, cols.length);
    assertTrue(Pattern.compile("^[0-9a-f]{64}$").matcher(cols[5]).matches());
    assertTrue(Pattern.compile("^[0-9a-f]{32,64}$").matcher(cols[6]).matches());
  }

  @Test
  void register_withoutPassword_fails_whenMismatch() {
    // Arrange provider to return different values
    final char[][] answers = {"A1!".toCharArray(), "B2!".toCharArray()};
    final int[] idx = {0};
    PasswordPrompt.setTestProvider(
        (out, prompt) -> {
          if (out != null) {
            out.print(prompt);
            out.flush();
          }
          return answers[Math.min(idx[0]++, answers.length - 1)];
        });

    var outBuf = new ByteArrayOutputStream();
    var errBuf = new ByteArrayOutputStream();
    var cmd =
        new RegisterCommand(new PrintStream(outBuf), new PrintStream(errBuf), new PasswordHasher());

    int code =
        cmd.run(
            new String[] {
              "--username", "charlie",
              "--email", "charlie@example.com",
              "--phone", "0401",
              "--id-key", "K-INT-2"
            });

    assertNotEquals(0, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Passwords do not match"));

    boolean hasCharlie = false;
    if (Files.exists(usersTsv)) {
      try {
        String content = Files.readString(usersTsv, StandardCharsets.UTF_8);
        hasCharlie = content.contains("charlie\t");
      } catch (java.io.IOException ignored) {
        hasCharlie = false;
      }
    }
    assertFalse(hasCharlie, "users.tsv should not contain 'charlie'");
  }
}
