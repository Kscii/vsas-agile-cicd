package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.BookmarkRepository;
import org.soft2412.vsas.repo.FileBookmarkRepository;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

class BookmarkCommandsTest {

  private PrintStream originalOut;
  private PrintStream originalErr;
  private InputStream originalIn;
  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;
  private String previousSessionProperty;
  private Path sessionDir;
  private Path sessionFile;

  @BeforeEach
  void setup() throws Exception {
    originalOut = System.out;
    originalErr = System.err;
    originalIn = System.in;
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

    cleanupDataDir();

    sessionDir = Files.createTempDirectory("vsas-session-bookmark-");
    sessionFile = sessionDir.resolve("session.properties");
    previousSessionProperty = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());
  }

  @AfterEach
  void teardown() throws Exception {
    System.setOut(originalOut);
    System.setErr(originalErr);
    System.setIn(originalIn);

    if (previousSessionProperty == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionProperty);
    }
    if (Files.exists(sessionFile)) {
      Files.deleteIfExists(sessionFile);
    }
    if (sessionDir != null && Files.exists(sessionDir)) {
      Files.deleteIfExists(sessionDir);
    }

    cleanupDataDir();
  }

  @Test
  void addRequiresLogin() {
    int code = new BookmarkAddCommand().run(new String[] {"--id", "S-1"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Login required"));
  }

  @Test
  void addSuccessThenDuplicateIsIdempotent() {
    loginUser("U-1");
    createScroll("S-1", "Example", "U-1");

    int first = new BookmarkAddCommand().run(new String[] {"--id", "S-1"});
    assertEquals(0, first);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Bookmarked S-1"));

    outBuf.reset();
    errBuf.reset();

    int second = new BookmarkAddCommand().run(new String[] {"--id", "S-1"});
    assertEquals(0, second);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Already bookmarked"));
  }

  @Test
  void addUnknownScrollReturnsExit1() {
    loginUser("U-1");
    int code = new BookmarkAddCommand().run(new String[] {"--id", "MISSING"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("unknown scroll id"));
  }

  @Test
  void listRequiresLogin() {
    int code = new BookmarkListCommand().run(new String[0]);
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Login required"));
  }

  @Test
  void listRejectsUnexpectedArguments() {
    loginUser("U-1");
    int code = new BookmarkListCommand().run(new String[] {"extra"});
    assertEquals(2, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("unexpected arguments"));
  }

  @Test
  void listPrintsBookmarksWithMarker() {
    loginUser("U-1");
    createScroll("S-1", "Example", "U-2");
    BookmarkRepository repo = new FileBookmarkRepository();
    assertTrue(repo.add("U-1", "S-1"));

    int code = new BookmarkListCommand().run(new String[0]);
    assertEquals(0, code);
    String output = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("[BK]"));
    assertTrue(output.contains("S-1"));
  }

  @Test
  void removeRequiresLogin() {
    int code = new BookmarkRemoveCommand().run(new String[] {"--id", "S-1"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Login required"));
  }

  @Test
  void removeAbortedByPromptLeavesBookmark() {
    loginUser("U-1");
    createScroll("S-1", "Example", "U-1");
    BookmarkRepository repo = new FileBookmarkRepository();
    assertTrue(repo.add("U-1", "S-1"));

    System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));
    int code = new BookmarkRemoveCommand().run(new String[] {"--id", "S-1"});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Aborted"));
    assertTrue(repo.exists("U-1", "S-1"));
  }

  @Test
  void removeWithYesFlagDeletesBookmark() {
    loginUser("U-1");
    createScroll("S-1", "Example", "U-1");
    BookmarkRepository repo = new FileBookmarkRepository();
    assertTrue(repo.add("U-1", "S-1"));

    int code = new BookmarkRemoveCommand().run(new String[] {"--id", "S-1", "--yes"});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Removed bookmark"));
    assertFalse(repo.exists("U-1", "S-1"));
  }

  @Test
  void removeNotBookmarkedReturnsExit1() {
    loginUser("U-1");

    int code = new BookmarkRemoveCommand().run(new String[] {"--id", "S-1", "--yes"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("not bookmarked"));
  }

  @Test
  void addMissingIdReturnsUsageError() {
    loginUser("U-1");
    int code = new BookmarkAddCommand().run(new String[0]);
    assertEquals(2, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("missing required option --id"));
  }

  @Test
  void removeMissingIdReturnsUsageError() {
    loginUser("U-1");
    int code = new BookmarkRemoveCommand().run(new String[0]);
    assertEquals(2, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("missing required option --id"));
  }

  private void loginUser(String idKey) {
    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("user-" + idKey, "", "", idKey, "USER", "", "")),
        "login should succeed");
  }

  private void createScroll(String scrollId, String name, String uploaderId) {
    ScrollRepository repo = new FileScrollRepository();
    assertTrue(
        repo.save(
            new Scroll(
                scrollId,
                name,
                uploaderId,
                Instant.parse("2025-01-01T00:00:00Z").toString(),
                "data/files/" + scrollId + ".bin",
                1L,
                0L)));
  }

  private void cleanupDataDir() throws Exception {
    Path data = Path.of("data");
    if (!Files.exists(data)) {
      return;
    }
    Files.walk(data)
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
