package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

class DownloadCommandTest {
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

    sessionDir = Files.createTempDirectory("vsas-session-download-");
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
    if (Files.exists(sessionFile)) Files.deleteIfExists(sessionFile);
    if (sessionDir != null && Files.exists(sessionDir)) Files.deleteIfExists(sessionDir);
    cleanupDataDir();
  }

  @Test
  void notLoggedIn_failsWithExit1_andMessage() {
    DownloadCommand cmd = new DownloadCommand();
    int code = cmd.run(new String[] {"--id", "nope"});

    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Login required"));
  }

  @Test
  void success_withOutDir_printsAbsolutePath_andCopies() throws Exception {
    // Login session
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("alice", "", "", "ID-1", "USER", "", "")));

    // Prepare source file and repository entry
    Path tmp = Files.createTempDirectory("vsas-dl-src-");
    Path src = tmp.resolve("s1.bin");
    Files.writeString(src, "payload-123", StandardCharsets.UTF_8);

    Scroll sc =
        new Scroll(
            "s1",
            "Doc",
            "ID-1",
            Instant.parse("2025-01-01T00:00:00Z").toString(),
            src.toString(),
            0L,
            0L);
    ScrollRepository repo = new FileScrollRepository();
    assertTrue(repo.save(sc));

    Path outDir = Files.createTempDirectory("vsas-dl-out-");

    DownloadCommand cmd = new DownloadCommand();
    int code = cmd.run(new String[] {"--id", "s1", "--out", outDir.toString()});

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8).trim();
    Path expected = outDir.resolve("s1.bin").toAbsolutePath().normalize();
    assertTrue(out.endsWith("s1.bin"), "stdout should end with file name");
    assertTrue(Files.exists(expected));
    assertEquals("payload-123", Files.readString(expected, StandardCharsets.UTF_8));
  }

  @Test
  void missingOut_declinePrompt_abortsWithoutWriting() throws Exception {
    // Login
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("bob", "", "", "ID-2", "USER", "", "")));

    // Data
    Path tmp = Files.createTempDirectory("vsas-dl-src2-");
    Path src = tmp.resolve("s2.bin");
    Files.writeString(src, "DATA", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "s2",
            "Doc2",
            "ID-2",
            Instant.parse("2025-01-02T00:00:00Z").toString(),
            src.toString(),
            0L,
            0L);
    assertTrue(new FileScrollRepository().save(sc));

    // Decline using current directory
    System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));
    DownloadCommand cmd = new DownloadCommand();
    int code = cmd.run(new String[] {"--id", "s2"});

    assertEquals(0, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Aborted"));
  }

  @Test
  void missingOut_acceptPrompt_writesToCwd() throws Exception {
    // Login
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("carol", "", "", "ID-3", "USER", "", "")));

    // Data
    Path tmp = Files.createTempDirectory("vsas-dl-src3-");
    Path src = tmp.resolve("s3.bin");
    Files.writeString(src, "X", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "s3",
            "Doc3",
            "ID-3",
            Instant.parse("2025-01-03T00:00:00Z").toString(),
            src.toString(),
            0L,
            0L);
    assertTrue(new FileScrollRepository().save(sc));

    System.setIn(new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)));
    DownloadCommand cmd = new DownloadCommand();
    int code = cmd.run(new String[] {"--id", "s3"});

    assertEquals(0, code);
    Path expected = Path.of(".").toAbsolutePath().normalize().resolve("s3.bin");
    assertTrue(Files.exists(expected));
    assertEquals("X", Files.readString(expected, StandardCharsets.UTF_8));
    Files.deleteIfExists(expected);
  }

  @Test
  void existingDest_declinePrompt_keepsOriginal() throws Exception {
    // Login
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("dave", "", "", "ID-4", "USER", "", "")));

    // Data
    Path tmp = Files.createTempDirectory("vsas-dl-src4-");
    Path src = tmp.resolve("s4.bin");
    Files.writeString(src, "SRC", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "s4",
            "Doc4",
            "ID-4",
            Instant.parse("2025-01-04T00:00:00Z").toString(),
            src.toString(),
            0L,
            0L);
    assertTrue(new FileScrollRepository().save(sc));

    Path outDir = Files.createTempDirectory("vsas-dl-out4-");
    Path dest = outDir.resolve("s4.bin");
    Files.writeString(dest, "OLD", StandardCharsets.UTF_8);

    System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));
    DownloadCommand cmd = new DownloadCommand();
    int code = cmd.run(new String[] {"--id", "s4", "--out", outDir.toString()});

    assertEquals(0, code);
    assertEquals("OLD", Files.readString(dest, StandardCharsets.UTF_8));
  }

  @Test
  void existingDest_acceptPrompt_overwrites() throws Exception {
    // Login
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("erin", "", "", "ID-5", "USER", "", "")));

    // Data
    Path tmp = Files.createTempDirectory("vsas-dl-src5-");
    Path src = tmp.resolve("s5.bin");
    Files.writeString(src, "NEW", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "s5",
            "Doc5",
            "ID-5",
            Instant.parse("2025-01-05T00:00:00Z").toString(),
            src.toString(),
            0L,
            0L);
    assertTrue(new FileScrollRepository().save(sc));

    Path outDir = Files.createTempDirectory("vsas-dl-out5-");
    Path dest = outDir.resolve("s5.bin");
    Files.writeString(dest, "OLD", StandardCharsets.UTF_8);

    System.setIn(new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)));
    DownloadCommand cmd = new DownloadCommand();
    int code = cmd.run(new String[] {"--id", "s5", "--out", outDir.toString()});

    assertEquals(0, code);
    assertEquals("NEW", Files.readString(dest, StandardCharsets.UTF_8));
  }

  @Test
  void scrollNotFound_exit3() throws Exception {
    // Login
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("zoe", "", "", "ID-9", "USER", "", "")));

    DownloadCommand cmd = new DownloadCommand();
    int code =
        cmd.run(
            new String[] {"--id", "missing", "--out", Files.createTempDirectory("x").toString()});

    assertEquals(3, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("scroll not found"));
  }

  @Test
  void sourceMissing_exit3() throws Exception {
    // Login
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("yan", "", "", "ID-10", "USER", "", "")));

    // Repo entry points to a non-existent file
    Scroll sc =
        new Scroll(
            "s10",
            "Doc10",
            "ID-10",
            Instant.parse("2025-01-10T00:00:00Z").toString(),
            Path.of("/nonexistent/path/to/file.bin").toString(),
            0L,
            0L);
    assertTrue(new FileScrollRepository().save(sc));

    DownloadCommand cmd = new DownloadCommand();
    int code =
        cmd.run(new String[] {"--id", "s10", "--out", Files.createTempDirectory("y").toString()});

    assertEquals(3, code);
    assertTrue(
        errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("source file not found"));
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
