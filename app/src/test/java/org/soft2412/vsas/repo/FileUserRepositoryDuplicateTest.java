package org.soft2412.vsas.repo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.User;

public class FileUserRepositoryDuplicateTest {

  private Path dataDir;
  private Path usersPath;
  private FileUserRepository repo;

  @BeforeEach
  void setUp() throws Exception {
    dataDir = Path.of("data");
    Files.createDirectories(dataDir);
    usersPath = dataDir.resolve("users.tsv");
    Files.deleteIfExists(usersPath);
    repo = new FileUserRepository();
  }

  @AfterEach
  void cleanup() throws Exception {
    if (Files.exists(dataDir)) {
      // delete files bottom-up
      Files.walk(dataDir)
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (Exception ignore) {
                }
              });
    }
  }

  private static User u(String name, String idKey, String role) {
    return new User(name, name + "@ex.com", "0400000000", idKey, role, "aa", "bb");
  }

  @Test
  void duplicateUsername_secondSaveIsAllowed_andAppendsAnotherRow() throws Exception {
    // Given: same username, different idKey
    assertTrue(repo.save(u("alice", "ID-1", "USER")), "first save should succeed");
    assertTrue(
        repo.save(u("alice", "ID-2", "USER")),
        "second save with same username should also succeed");

    // Then: both idKeys are recorded
    assertTrue(repo.existsIdKey("ID-1"));
    assertTrue(repo.existsIdKey("ID-2"));
    assertTrue(
        repo.findByUsername("alice").isPresent(), "finder should return a record for username");

    // And: TSV actually has two data lines (append semantics).
    // Count non-empty, non-header lines
    String content =
        Files.exists(usersPath) ? Files.readString(usersPath, StandardCharsets.UTF_8) : "";
    long dataLines =
        Stream.of(content.split("\\R"))
            .filter(s -> !s.isBlank())
            .filter(s -> !s.toLowerCase().startsWith("username")) // skip header if present
            .count();
    assertEquals(
        2, dataLines, "two rows should be stored for the same username (append semantics)");
  }

  @Test
  void duplicateIdKey_secondSaveIsAllowed_andAppendsAnotherRow() throws Exception {
    // Given: same idKey, different usernames
    assertTrue(repo.save(u("bob", "ID-9", "VISITOR")), "first save should succeed");
    assertTrue(
        repo.save(u("bobby", "ID-9", "VISITOR")),
        "second save with same idKey should also succeed");

    // Then: the idKey is present and both usernames are findable (depending on scan semantics,
    // at least one may be returned by a specific finder)
    assertTrue(repo.existsIdKey("ID-9"));
    assertTrue(
        repo.findByUsername("bob").isPresent() || repo.findByUsername("bobby").isPresent(),
        "at least one of the usernames should be retrievable");

    // And: TSV shows two rows appended
    String content =
        Files.exists(usersPath) ? Files.readString(usersPath, StandardCharsets.UTF_8) : "";
    long dataLines =
        Stream.of(content.split("\\R"))
            .filter(s -> !s.isBlank())
            .filter(s -> !s.toLowerCase().startsWith("username"))
            .count();
    assertEquals(2, dataLines, "two rows should be stored for the same idKey (append semantics)");
  }

  @Test
  void finders_presentAndAbsentPaths() {
    assertTrue(repo.save(u("carl", "K-01", "ADMIN")));

    assertTrue(repo.existsIdKey("K-01"));
    assertFalse(repo.existsIdKey("K-XX"));

    assertEquals("carl", repo.findByIdKey("K-01").orElseThrow().username());
    assertTrue(repo.findByIdKey("K-XX").isEmpty());

    assertEquals("ADMIN", repo.findByUsername("carl").orElseThrow().role());
    assertTrue(repo.findByUsername("nobody").isEmpty());
  }

  @Test
  void headerMissing_emptyFile_returnsEmptyFindersWithoutCrash() throws Exception {
    Files.writeString(usersPath, "", StandardCharsets.UTF_8);
    assertTrue(repo.findByUsername("any").isEmpty());
    assertTrue(repo.findByIdKey("any").isEmpty());
    assertFalse(repo.existsIdKey("any"));
  }
}
