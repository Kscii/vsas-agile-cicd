package org.soft2412.vsas.repo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.User;

/**
 * Minimal tests for FileUserRepository (Task #9). - First save creates file + header and appends
 * one row - Basic queries: existsIdKey/findByIdKey/findByUsername
 */
public class FileUserRepositoryTest {

  private Path tempDir;
  private Path usersTsv;
  private FileUserRepository repo;

  @BeforeEach
  void setUp() throws Exception {
    tempDir = Files.createTempDirectory("vsas-users-");
    usersTsv = tempDir.resolve("users.tsv");
    repo = new FileUserRepository(usersTsv);
  }

  @AfterEach
  void tearDown() throws Exception {
    // Best-effort cleanup
    try {
      Files.deleteIfExists(usersTsv);
    } catch (Exception ignored) {
    }
    try {
      Files.deleteIfExists(tempDir);
    } catch (Exception ignored) {
    }
  }

  @Test
  void firstSave_createsHeader_andRow() throws Exception {
    User u =
        new User(
            "alice",
            "a@x",
            "0400",
            "K-001",
            "USER",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "0123456789abcdef0123456789abcdef",
            Instant.parse("2025-01-01T00:00:00Z"));

    assertTrue(repo.save(u), "save should return true");
    assertTrue(Files.exists(usersTsv), "users.tsv must be created");

    String content = Files.readString(usersTsv, StandardCharsets.UTF_8);
    String[] lines = content.split("\\R");

    // Header + 1 data row
    assertTrue(lines.length >= 2, "header + one row expected");
    assertEquals("username\temail\tphone\tidKey\trole\tpasswordHash\tsalt\tcreatedAt", lines[0]);
    assertTrue(lines[1].startsWith("alice\t"), "first data row should start with username");
  }

  @Test
  void queries_work_afterMultipleSaves() throws Exception {
    User u1 =
        new User(
            "alice",
            "a@x",
            "0400",
            "K-001",
            "USER",
            "h".repeat(64),
            "s".repeat(32),
            Instant.parse("2025-01-01T00:00:00Z"));
    User u2 =
        new User(
            "bob",
            "b@x",
            "0401",
            "K-002",
            "VISITOR",
            "x".repeat(64),
            "y".repeat(32),
            Instant.parse("2025-01-02T00:00:00Z"));

    assertTrue(repo.save(u1));
    assertTrue(repo.save(u2));

    // existsIdKey
    assertTrue(repo.existsIdKey("K-001"));
    assertTrue(repo.existsIdKey("K-002"));
    assertFalse(repo.existsIdKey("K-999"));

    // findByIdKey
    var byKey = repo.findByIdKey("K-002").orElseThrow();
    assertEquals("bob", byKey.username());
    assertEquals("VISITOR", byKey.role());

    // findByUsername
    var byUser = repo.findByUsername("alice").orElseThrow();
    assertEquals("K-001", byUser.idKey());
    assertEquals("USER", byUser.role());
  }

  @Test
  void updateProfile_updatesContactFields_andKeepsOtherColumns() throws Exception {
    User alice =
        new User(
            "alice",
            "a@x",
            "0400",
            "K-001",
            "USER",
            "h".repeat(64),
            "s".repeat(32),
            Instant.parse("2025-01-01T00:00:00Z"));
    User bob =
        new User(
            "bob",
            "b@x",
            "0401",
            "K-002",
            "VISITOR",
            "x".repeat(64),
            "y".repeat(32),
            Instant.parse("2025-01-02T00:00:00Z"));

    assertTrue(repo.save(alice));
    assertTrue(repo.save(bob));

    assertTrue(repo.updateProfile("alice", "alice@new", "0499", null));

    var updated = repo.findByUsername("alice").orElseThrow();
    assertEquals("alice@new", updated.email());
    assertEquals("0499", updated.phone());
    assertEquals(alice.passwordHash(), updated.passwordHash(), "hash should be unchanged");
    assertEquals(alice.salt(), updated.salt(), "salt should be unchanged");

    var untouched = repo.findByUsername("bob").orElseThrow();
    assertEquals("b@x", untouched.email(), "other rows must remain untouched");
  }

  @Test
  void updateProfile_changesPasswordHashAndSalt() throws Exception {
    User alice =
        new User(
            "alice",
            "a@x",
            "0400",
            "K-001",
            "USER",
            "h".repeat(64),
            "s".repeat(32),
            Instant.parse("2025-01-01T00:00:00Z"));

    assertTrue(repo.save(alice));

    char[] newPwd = "S3cretNew!".toCharArray();
    try {
      assertTrue(repo.updateProfile("alice", null, null, newPwd));
    } finally {
      Arrays.fill(newPwd, '\0');
    }

    var updated = repo.findByUsername("alice").orElseThrow();
    assertNotEquals(alice.passwordHash(), updated.passwordHash());
    assertNotEquals(alice.salt(), updated.salt());
    assertEquals(64, updated.passwordHash().length(), "hash must stay 64 hex chars");
    assertEquals(32, updated.salt().length(), "salt must stay 32 hex chars");
  }

  @Test
  void updateProfile_returnsFalse_whenUserMissing() throws Exception {
    assertFalse(repo.updateProfile("nope", "x@y", "0400", null));
  }

  @Test
  void updateProfile_handlesRowsWithMissingTrailingColumns() throws Exception {
    Files.createDirectories(usersTsv.getParent());
    String header =
        "username\temail\tphone\tidKey\trole\tpasswordHash\tsalt\tcreatedAt"
            + System.lineSeparator();
    String legacyRow =
        String.join("\t", "alice", "a@x", "0400", "K-001", "USER", "h".repeat(64), "s".repeat(32))
            + System.lineSeparator();
    Files.writeString(usersTsv, header + legacyRow, StandardCharsets.UTF_8);

    assertTrue(repo.updateProfile("alice", "alice@new", null, null));

    String[] lines = Files.readString(usersTsv, StandardCharsets.UTF_8).split("\\R");
    assertTrue(lines.length >= 2);
    String[] parts = lines[1].split("\t", -1);
    assertEquals(8, parts.length, "row should expand to full column count");
    assertEquals("alice@new", parts[1]);
    assertEquals("0400", parts[2], "unchanged fields should remain intact");
  }
}
