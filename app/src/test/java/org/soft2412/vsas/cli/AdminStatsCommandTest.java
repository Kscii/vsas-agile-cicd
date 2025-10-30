package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

class AdminStatsCommandTest {

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

    sessionDir = Files.createTempDirectory("vsas-session-adminstats-");
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
  void adminStats_shouldPrintPerScrollTable() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("admin", "", "", "A-1", "ADMIN", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path p1 = Files.createTempFile("scroll1-", ".bin");
    Files.writeString(p1, "file1", StandardCharsets.UTF_8);
    Path p2 = Files.createTempFile("scroll2-", ".bin");
    Files.writeString(p2, "file2", StandardCharsets.UTF_8);

    repo.save(new Scroll("S-A", "DocA", "U-1", Instant.now().toString(), p1.toString(), 1L, 2L));
    repo.save(new Scroll("S-B", "DocB", "U-2", Instant.now().toString(), p2.toString(), 2L, 3L));

    int code = new AdminStatsCommand().run(new String[] {});
    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);

    assertTrue(out.contains("DocA"));
    assertTrue(out.contains("DocB"));
    assertTrue(out.contains("UPLOADS"));
    assertTrue(out.contains("DOWNLOADS"));
  }

  @Test
  void adminStats_byUploader_shouldPrintAggregatedTable() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("root", "", "", "ROOT", "ADMIN", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path p1 = Files.createTempFile("agg1-", ".bin");
    Path p2 = Files.createTempFile("agg2-", ".bin");
    repo.save(new Scroll("S-X", "FileX", "U-A", Instant.now().toString(), p1.toString(), 1L, 1L));
    repo.save(new Scroll("S-Y", "FileY", "U-A", Instant.now().toString(), p2.toString(), 2L, 2L));

    int code = new AdminStatsCommand().run(new String[] {"--by", "uploader"});
    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("UPLOADER"));
    assertTrue(out.contains("UPLOADS"));
    assertTrue(out.contains("DOWNLOADS"));
    assertTrue(out.contains("U-A"));
  }

  @Test
  void nonAdmin_shouldFailWithPermissionError() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("alice", "", "", "U-NORMAL", "USER", "", "")));

    int code = new AdminStatsCommand().run(new String[] {});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("permission"));
  }

  @Test
  void emptyRepository_shouldPrintNoDataMessage() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("admin", "", "", "A-2", "ADMIN", "", "")));

    int code = new AdminStatsCommand().run(new String[] {});
    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("No data"));
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
