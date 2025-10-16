package org.soft2412.vsas.repo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
}
