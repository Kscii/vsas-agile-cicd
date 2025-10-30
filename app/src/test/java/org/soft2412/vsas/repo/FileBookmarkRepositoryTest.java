package org.soft2412.vsas.repo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.Bookmark;

class FileBookmarkRepositoryTest {

  private Path tempDir;
  private Path dataFile;
  private FileBookmarkRepository repo;

  @BeforeEach
  void setUp() throws Exception {
    tempDir = Files.createTempDirectory("vsas-bookmarks-");
    dataFile = tempDir.resolve("bookmarks.tsv");
    repo = new FileBookmarkRepository(dataFile);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (tempDir != null && Files.exists(tempDir)) {
      Files.walk(tempDir)
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

  @Test
  void addCreatesFileAndPersistsBookmark() throws Exception {
    assertTrue(repo.add("U-1", "S-1"));

    assertTrue(Files.exists(dataFile));
    List<Bookmark> bookmarks = repo.listByUser("U-1");
    assertEquals(1, bookmarks.size());
    assertEquals("S-1", bookmarks.get(0).scrollId());
  }

  @Test
  void addDuplicateIsIdempotent() {
    assertTrue(repo.add("U-1", "S-1"));
    assertTrue(repo.add("U-1", "S-1"));

    List<Bookmark> bookmarks = repo.listByUser("U-1");
    assertEquals(1, bookmarks.size());
  }

  @Test
  void existsReturnsTrueWhenPresent() {
    assertTrue(repo.add("U-1", "S-1"));
    assertTrue(repo.exists("U-1", "S-1"));
    assertFalse(repo.exists("U-1", "missing"));
  }

  @Test
  void removeDeletesBookmark() {
    assertFalse(repo.remove("U-1", "S-1"), "removing missing bookmark should return false");

    assertTrue(repo.add("U-1", "S-1"));
    assertTrue(repo.add("U-1", "S-2"));

    assertTrue(repo.remove("U-1", "S-1"));
    assertFalse(repo.exists("U-1", "S-1"));
    assertTrue(repo.exists("U-1", "S-2"));
  }

  @Test
  void listByUserSortedByAddedAt() {
    assertTrue(repo.add("U-1", "S-1"));
    try {
      Thread.sleep(5L);
    } catch (InterruptedException ignore) {
    }
    assertTrue(repo.add("U-1", "S-2"));

    List<Bookmark> list = repo.listByUser("U-1");
    assertEquals(List.of("S-1", "S-2"), list.stream().map(Bookmark::scrollId).toList());
  }

  @Test
  void addRejectsBlankArguments() {
    assertFalse(repo.add("", "S-1"));
    assertFalse(repo.add("U-1", ""));
    assertFalse(Files.exists(dataFile));
  }

  @Test
  void sanitizeRemovesTabsAndNewlines() throws Exception {
    assertTrue(repo.add("U-\t1", "S-\n1"));
    List<String> lines = Files.readAllLines(dataFile);
    assertTrue(lines.get(0).contains("userIdKey"));
    String payload = lines.get(1);
    String[] columns = payload.split("\t");
    assertTrue(columns.length >= 3);
    assertEquals("U- 1", columns[0]);
    assertEquals("S- 1", columns[1]);
    assertFalse(columns[0].contains("\t"));
    assertFalse(columns[1].contains("\n"));
  }

  @Test
  void existsReturnsFalseForBlankInputOrMissingFile() {
    assertFalse(repo.exists("", "S-1"));
    assertFalse(repo.exists("U-1", ""));
    assertFalse(repo.exists("U-1", "S-1"));
  }

  @Test
  void listReturnsEmptyWhenFileMissingOrUserUnknown() {
    assertTrue(repo.listByUser("U-1").isEmpty());
    assertTrue(repo.add("U-2", "S-1"));
    assertTrue(repo.listByUser("U-1").isEmpty());
  }
}
