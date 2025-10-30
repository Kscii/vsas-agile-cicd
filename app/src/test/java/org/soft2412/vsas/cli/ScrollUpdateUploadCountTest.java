package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

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

class ScrollUpdateUploadCountTest {

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

    sessionDir = Files.createTempDirectory("vsas-session-upcount-");
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
  void updateNameOnly_shouldNotIncrementUploadCount() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("alice", "", "", "ID-UP1", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-upcount-", ".bin");
    Files.writeString(payload, "ORIG", StandardCharsets.UTF_8);

    Scroll sc =
        new Scroll(
            "SUP-1",
            "OldName",
            "ID-UP1",
            Instant.parse("2025-01-10T00:00:00Z").toString(),
            payload.toString(),
            1L,
            3L);
    assertTrue(repo.save(sc));

    int code =
        new ScrollUpdateSubcommand().run(new String[] {"--id", "SUP-1", "--name", "NewName"});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Updated"));

    Scroll after = repo.findById("SUP-1").orElseThrow();
    assertEquals("NewName", after.name());
    assertEquals(1L, after.uploadCount(), "name-only update must NOT change uploadCount");
    assertEquals(3L, after.downloadCount(), "downloadCount must stay");
  }

  @Test
  void updateFile_shouldIncrementUploadCount() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("bob", "", "", "ID-UP2", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-upcount2-", ".bin");
    Files.writeString(payload, "OLD", StandardCharsets.UTF_8);

    Scroll sc =
        new Scroll(
            "SUP-2",
            "Doc",
            "ID-UP2",
            Instant.parse("2025-01-11T00:00:00Z").toString(),
            payload.toString(),
            1L,
            0L);
    assertTrue(repo.save(sc));

    Path newSrc = Files.createTempFile("vsas-upcount2-src-", ".bin");
    Files.writeString(newSrc, "NEW", StandardCharsets.UTF_8);

    int code =
        new ScrollUpdateSubcommand()
            .run(new String[] {"--id", "SUP-2", "--file", newSrc.toString(), "--yes"});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Updated"));

    Scroll after = repo.findById("SUP-2").orElseThrow();
    assertEquals(2L, after.uploadCount(), "file-update must increment uploadCount to 2");
    assertEquals(0L, after.downloadCount());
    assertEquals("Doc", after.name());
    assertEquals("NEW", Files.readString(payload, StandardCharsets.UTF_8));
  }

  @Test
  void updateFile_missingSource_shouldNotChangeUploadCount() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("carol", "", "", "ID-UP3", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-upcount3-", ".bin");
    Files.writeString(payload, "ORIG", StandardCharsets.UTF_8);

    Scroll sc =
        new Scroll(
            "SUP-3",
            "Doc3",
            "ID-UP3",
            Instant.parse("2025-01-12T00:00:00Z").toString(),
            payload.toString(),
            1L,
            1L);
    assertTrue(repo.save(sc));

    Path missing = Path.of("/no/such/file.bin");
    int code =
        new ScrollUpdateSubcommand()
            .run(new String[] {"--id", "SUP-3", "--file", missing.toString()});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("file not found"));

    Scroll after = repo.findById("SUP-3").orElseThrow();
    assertEquals(1L, after.uploadCount(), "should NOT change uploadCount on error");
    assertEquals(1L, after.downloadCount());
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
