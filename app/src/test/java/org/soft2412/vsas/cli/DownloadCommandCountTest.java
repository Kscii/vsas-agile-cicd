package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

class DownloadCommandCountTest {

  private PrintStream originalOut;
  private PrintStream originalErr;
  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;

  private String previousSessionProperty;
  private Path sessionDir;
  private Path sessionFile;

  @BeforeEach
  void setup() throws Exception {
    originalOut = System.out;
    originalErr = System.err;
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf));
    System.setErr(new PrintStream(errBuf));

    sessionDir = Files.createTempDirectory("vsas-session-dlcount-");
    sessionFile = sessionDir.resolve("session.properties");
    previousSessionProperty = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());

    cleanupDataDir();
  }

  @AfterEach
  void teardown() throws Exception {
    System.setOut(originalOut);
    System.setErr(originalErr);

    if (previousSessionProperty == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionProperty);
    }
    Files.deleteIfExists(sessionFile);
    Files.deleteIfExists(sessionDir);

    cleanupDataDir();
  }

  @Test
  void downloadSuccess_shouldIncrementDownloadCount() throws Exception {
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("alice", "", "", "ID-A", "USER", "", "")));

    Path srcDir = Files.createTempDirectory("vsas-dlcount-src-");
    Path src = srcDir.resolve("s100.bin");
    Files.writeString(src, "PAYLOAD", StandardCharsets.UTF_8);

    Scroll s =
        new Scroll(
            "S100",
            "TestFile",
            "ID-A",
            Instant.parse("2025-01-01T00:00:00Z").toString(),
            src.toString(),
            1L,
            0L);
    ScrollRepository repo = new FileScrollRepository();
    assertTrue(repo.save(s));

    Path outDir = Files.createTempDirectory("vsas-dlcount-out-");
    DownloadCommand cmd = new DownloadCommand();
    int code = cmd.run(new String[] {"--id", "S100", "--out", outDir.toString()});

    assertEquals(0, code);
    Scroll after = repo.findById("S100").orElseThrow();
    assertEquals(1L, after.uploadCount());
    assertEquals(1L, after.downloadCount(), "downloadCount should be incremented to 1");
  }

  @Test
  void abortedDownload_shouldNotIncrementDownloadCount() throws Exception {
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("bob", "", "", "ID-B", "USER", "", "")));

    Path src = Files.createTempFile("vsas-dlcount-abort-", ".bin");
    Files.writeString(src, "X", StandardCharsets.UTF_8);

    Scroll s =
        new Scroll(
            "S200",
            "AbortFile",
            "ID-B",
            Instant.parse("2025-01-02T00:00:00Z").toString(),
            src.toString(),
            1L,
            0L);
    ScrollRepository repo = new FileScrollRepository();
    assertTrue(repo.save(s));

    System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));
    DownloadCommand cmd = new DownloadCommand();
    int code = cmd.run(new String[] {"--id", "S200"});

    assertEquals(0, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Aborted"));

    Scroll after = repo.findById("S200").orElseThrow();
    assertEquals(1L, after.uploadCount());
    assertEquals(0L, after.downloadCount(), "aborted download must NOT change count");
  }

  private void cleanupDataDir() throws Exception {
    Path data = Path.of("data");
    if (!Files.exists(data)) return;
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
