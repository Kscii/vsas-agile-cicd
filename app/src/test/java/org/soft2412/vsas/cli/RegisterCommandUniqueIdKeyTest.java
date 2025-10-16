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
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.security.PasswordHasher;

/**
 * A1 Task #14: Duplicate idKey should fail with non-zero exit and clear error message. Uses 4-arg
 * constructor to inject a temp TSV path (no pollution).
 */
public class RegisterCommandUniqueIdKeyTest {

  private Path tempDir;
  private Path usersTsv;
  private UserRepository repo;

  @BeforeEach
  void setup() throws Exception {
    tempDir = Files.createTempDirectory("vsas-users-");
    usersTsv = tempDir.resolve("users.tsv");
    repo = new FileUserRepository(usersTsv);
  }

  @AfterEach
  void teardown() throws Exception {
    Files.deleteIfExists(usersTsv);
    Files.deleteIfExists(tempDir);
  }

  @Test
  void duplicateIdKey_fails_and_doesNotAppend() throws Exception {
    // First registration OK
    var out1 = new ByteArrayOutputStream();
    var err1 = new ByteArrayOutputStream();
    var cmd1 =
        new RegisterCommand(
            new PrintStream(out1), new PrintStream(err1), new PasswordHasher(), repo);

    int c1 =
        cmd1.run(
            new String[] {
              "--username", "alice",
              "--password", "P@ssw0rd!",
              "--email", "alice@example.com",
              "--phone", "0400000000",
              "--id-key", "K-001"
            });
    assertEquals(0, c1);

    // Second registration with same idKey must fail
    var out2 = new ByteArrayOutputStream();
    var err2 = new ByteArrayOutputStream();
    var cmd2 =
        new RegisterCommand(
            new PrintStream(out2), new PrintStream(err2), new PasswordHasher(), repo);

    int c2 =
        cmd2.run(
            new String[] {
              "--username", "bob",
              "--password", "Another#1",
              "--email", "bob@example.com",
              "--phone", "0400000001",
              "--id-key", "K-001" // duplicate
            });
    assertNotEquals(0, c2);
    String errMsg = err2.toString(StandardCharsets.UTF_8);
    assertTrue(errMsg.contains("id-key already exists"), "should show clear duplicate error");

    // Verify no extra row was added
    String content = Files.readString(usersTsv, StandardCharsets.UTF_8);
    String[] lines = content.split("\\R");
    // header + 1 data row
    assertEquals(2, lines.length, "should not append a new row on duplicate idKey");
  }
}
