package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.security.PasswordHasher;

/**
 * Tests for RegisterCommand limited to Task #39: - After register, stored password is a salted
 * one-way hash (not plaintext). - Salt is persisted; header exists if file is newly created.
 */
public class RegisterCommandTest {

  private Path dataDir;
  private Path usersTsv;

  @BeforeEach
  void setup() throws Exception {
    dataDir = Path.of("data");
    usersTsv = dataDir.resolve("users.tsv");
    // Clean slate
    if (Files.exists(usersTsv)) Files.delete(usersTsv);
    File d = dataDir.toFile();
    if (d.exists()) d.delete();
  }

  @AfterEach
  void tearDown() throws Exception {
    if (Files.exists(usersTsv)) Files.delete(usersTsv);
    File d = dataDir.toFile();
    if (d.exists()) d.delete();
  }

  @Test
  void register_persistsHashedPasswordAndSalt_noPlaintext() throws Exception {
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    RegisterCommand cmd =
        new RegisterCommand(new PrintStream(outBuf), new PrintStream(errBuf), new PasswordHasher());

    int code =
        cmd.run(
            new String[] {
              "--username", "alice",
              "--password", "P@ssw0rd!",
              "--email", "alice@example.com",
              "--phone", "0400000000",
              "--id-key", "K-001"
            });

    assertEquals(0, code, "exit code should be 0");
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Registered user alice"));

    assertTrue(Files.exists(usersTsv), "users.tsv should exist");
    String content = Files.readString(usersTsv, StandardCharsets.UTF_8);

    // Header present
    assertTrue(
        content.startsWith("username\temail\tphone\tidKey\trole\tpasswordHash\tsalt\tcreatedAt"),
        "header must be present");

    // Row contains username but not the plaintext password
    assertTrue(content.contains("alice\t"), "username should be present");
    assertFalse(content.contains("P@ssw0rd!"), "plaintext must not be stored");

    // Extract the last row and validate hash/salt formats
    String[] lines = content.split("\\R");
    assertTrue(lines.length >= 2, "there must be at least one data row");
    String last = lines[lines.length - 1];
    String[] cols = last.split("\\t", -1);
    assertEquals(8, cols.length, "row must have 8 columns");

    String passwordHashHex = cols[5];
    String saltHex = cols[6];

    assertTrue(
        Pattern.compile("^[0-9a-f]{64}$").matcher(passwordHashHex).matches(),
        "passwordHash should be 64 hex chars (SHA-256)");
    assertTrue(
        Pattern.compile("^[0-9a-f]{32,64}$").matcher(saltHex).matches(),
        "salt should be 16..32 bytes in hex (32..64 hex chars)");
  }

  @Test
  void register_missingRequiredFlags_returnsNonZero() {
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    RegisterCommand cmd =
        new RegisterCommand(new PrintStream(outBuf), new PrintStream(errBuf), new PasswordHasher());

    int code = cmd.run(new String[] {"--username", "alice"}); // no password

    assertNotEquals(0, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("missing required flags"));
  }
}
