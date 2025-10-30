package org.soft2412.vsas;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.cli.CommandDispatcher;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.BookmarkRepository;
import org.soft2412.vsas.repo.FileBookmarkRepository;
import org.soft2412.vsas.service.SessionService;

public class ListCommandTest {
  private final PrintStream originalOut = System.out;
  private ByteArrayOutputStream out;
  private String previousSessionPath;
  private Path sessionDir;
  private Path sessionFile;

  private static final String TABLE_FMT = "%-12s  %-30s  %-14s  %-20s";

  @BeforeEach
  public void setup() throws IOException {
    out = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    cleanupDataDir();

    sessionDir = Files.createTempDirectory("vsas-session-list-");
    sessionFile = sessionDir.resolve("session.properties");
    previousSessionPath = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());
  }

  @AfterEach
  public void teardown() throws IOException {
    System.setOut(originalOut);

    if (previousSessionPath == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionPath);
    }
    if (sessionFile != null) {
      Files.deleteIfExists(sessionFile);
    }
    if (sessionDir != null) {
      Files.deleteIfExists(sessionDir);
    }

    cleanupDataDir();
  }

  @Test
  public void listShowsEmptyMessageWhenNoData() {
    int code = new CommandDispatcher().dispatch(new String[] {"list"});
    assertEquals(0, code);
    assertTrue(out.toString().contains("no scrolls"));
  }

  @Test
  public void listShowsHeaderAndEntryWhenDataExists() throws IOException {
    // Prepare one TSV row
    Path dataFile = Path.of("data", "scrolls.tsv");
    Files.createDirectories(dataFile.getParent());
    String row =
        String.join("\t", "s10", "Title", "u-1", "2025-01-01T00:00:00Z", "data/files/s10.bin", "0")
            + System.lineSeparator();
    Files.writeString(dataFile, row, StandardCharsets.UTF_8);

    int code = new CommandDispatcher().dispatch(new String[] {"list"});
    assertEquals(0, code);
    String outStr = out.toString();
    String expectedHeader = String.format(TABLE_FMT, "id", "name", "uploader", "uploadDate");
    String expectedRow = String.format(TABLE_FMT, "s10", "Title", "u-1", "2025-01-01T00:00:00Z");
    assertTrue(outStr.contains(expectedHeader));
    assertTrue(outStr.contains(expectedRow));
  }

  @Test
  public void listMarksBookmarkedScrolls() throws IOException {
    Path dataFile = Path.of("data", "scrolls.tsv");
    Files.createDirectories(dataFile.getParent());
    String row =
        String.join(
                "\t", "s11", "Bookmarked", "u-1", "2025-02-01T00:00:00Z", "data/files/s11.bin", "0")
            + System.lineSeparator();
    Files.writeString(dataFile, row, StandardCharsets.UTF_8);

    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("alice", "", "", "u-1", "USER", "", "")), "login should succeed");

    BookmarkRepository bookmarks = new FileBookmarkRepository();
    assertTrue(bookmarks.add("u-1", "s11"));

    int code = new CommandDispatcher().dispatch(new String[] {"list"});
    assertEquals(0, code);
    String outStr = out.toString();
    assertTrue(outStr.contains("[BK]"), "output should include bookmark marker");
  }

  private void cleanupDataDir() throws IOException {
    Path data = Path.of("data");
    if (Files.exists(data)) {
      Files.walk(data)
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignore) {
                }
              });
    }
  }
}
