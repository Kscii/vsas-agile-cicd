package org.soft2412.vsas.repo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.soft2412.vsas.model.Scroll;

/**
 * This test does NOT depend on fetchAll() because your FileScrollRepository does not expose it. We
 * verify behavior by reading the TSV file directly.
 */
public class FileScrollRepositoryEdgeTest {

  private Path dataDir;
  private Path scrollsPath;
  private FileScrollRepository repo;

  @BeforeEach
  void setUp() throws Exception {
    dataDir = Path.of("data");
    Files.createDirectories(dataDir);
    scrollsPath = dataDir.resolve("scrolls.tsv");
    Files.deleteIfExists(scrollsPath);
    repo = new FileScrollRepository();
  }

  @AfterEach
  void cleanup() throws Exception {
    if (Files.exists(dataDir)) {
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

  private static Scroll sc(String id, String name, String uploader, String filePath) {
    return new Scroll(
        id, name, uploader, Instant.parse("2025-01-01T00:00:00Z").toString(), filePath, 1L, 0L);
  }

  @Test
  void missingFile_thenFirstSave_createsFile() throws Exception {
    // Precondition: file does not exist
    assertFalse(Files.exists(scrollsPath), "scrolls.tsv should not exist before first save");

    // When: first save
    assertTrue(repo.save(sc("s1", "A", "U1", "/tmp/a")), "first save should succeed");

    // Then: file created with at least one non-header line
    assertTrue(Files.exists(scrollsPath), "scrolls.tsv should be created on first save");
    String raw = Files.readString(scrollsPath, StandardCharsets.UTF_8);
    long dataLines =
        Stream.of(raw.split("\\R"))
            .filter(s -> !s.isBlank())
            .filter(
                s ->
                    !s.toLowerCase().startsWith("id")
                        && !s.toLowerCase().startsWith("scroll")) // skip header if present
            .count();
    assertTrue(dataLines >= 1, "at least one data row should be written");
  }

  @Test
  void twoSaves_appendTwoRows_inOrder() throws Exception {
    assertTrue(repo.save(sc("s1", "A", "U1", "/tmp/a")));
    assertTrue(repo.save(sc("s2", "B", "U2", "/tmp/b")));

    String raw = Files.readString(scrollsPath, StandardCharsets.UTF_8);
    // Collect data (non-header) lines in order
    List<String> lines =
        Stream.of(raw.split("\\R"))
            .filter(s -> !s.isBlank())
            .filter(
                s -> {
                  String lower = s.toLowerCase();
                  return !(lower.startsWith("id")
                      || lower.startsWith("scroll")
                      || lower.startsWith("name")
                      || lower.startsWith("uploader"));
                })
            .toList();

    assertEquals(2, lines.size(), "two appended data rows expected");
    assertTrue(
        lines.get(0).startsWith("s1\t") || lines.get(0).contains("\ts1\t"),
        "first data row should correspond to s1");
    assertTrue(
        lines.get(1).startsWith("s2\t") || lines.get(1).contains("\ts2\t"),
        "second data row should correspond to s2");
  }

  @Test
  void save_nullFields_writesEmptyCells() throws Exception {
    // name and filePath are null - should be written as empty TSV cells
    Scroll x =
        new Scroll(
            "s3", null, "U3", Instant.parse("2025-02-02T00:00:00Z").toString(), null, 0L, 5L);

    assertTrue(repo.save(x));

    String raw = Files.readString(scrollsPath, StandardCharsets.UTF_8);
    // We expect something like: s3\t\tU3\t2025-02-02T00:00:00Z\t\t0\t5
    assertTrue(raw.contains("s3\t"), "row should start with id s3 (or include it as a field)");
    assertTrue(raw.contains("\t\tU3"), "null name should produce an empty TSV cell before U3");
    assertTrue(
        raw.contains("\t2025-02-02T00:00:00Z\t\t0\t5"),
        "null filePath should produce another empty cell and include counters");
  }
}
