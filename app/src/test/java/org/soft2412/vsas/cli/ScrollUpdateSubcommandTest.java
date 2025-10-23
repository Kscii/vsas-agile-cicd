package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

class ScrollUpdateSubcommandTest {

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

    sessionDir = Files.createTempDirectory("vsas-session-update-");
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
  void missingIdOrNoFields_printsUsage_exit2() {
    int c1 = new ScrollUpdateSubcommand().run(new String[] {"--name", "N"});
    assertEquals(2, c1);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("usage"));

    outBuf.reset();
    errBuf.reset();

    SessionService s = new SessionService();
    assertTrue(s.login(new User("u", "", "", "U1", "USER", "", "")));
    int c2 = new ScrollUpdateSubcommand().run(new String[] {"--id", "S"});
    assertEquals(2, c2);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("nothing to update"));
  }

  @Test
  void notLoggedIn_exit1() {
    int code = new ScrollUpdateSubcommand().run(new String[] {"--id", "S", "--name", "X"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("please login"));
  }

  @Test
  void notFound_exit1() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("alice", "", "", "ID-1", "USER", "", "")));
    int code = new ScrollUpdateSubcommand().run(new String[] {"--id", "MISSING", "--name", "N"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Not found"));
  }

  @Test
  void forbidden_whenNotOwner_exit1() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("bob", "", "", "ID-OTHER", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-upd-", ".bin");
    Files.writeString(payload, "DATA", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "S1",
            "Doc",
            "ID-OWNER",
            Instant.parse("2025-01-01T00:00:00Z").toString(),
            payload.toString(),
            0L);
    assertTrue(repo.save(sc));

    int code = new ScrollUpdateSubcommand().run(new String[] {"--id", "S1", "--name", "NEW"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("only the uploader"));
    Scroll cur = repo.findById("S1").orElseThrow();
    assertEquals("Doc", cur.name());
    assertEquals("DATA", Files.readString(Path.of(cur.filePath()), StandardCharsets.UTF_8));
  }

  @Test
  void metadataOnly_updateName_success_exit0_andPersisted() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("carol", "", "", "ID-2", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-upd2-", ".bin");
    Files.writeString(payload, "X", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "S2",
            "OldName",
            "ID-2",
            Instant.parse("2025-01-02T00:00:00Z").toString(),
            payload.toString(),
            5L);
    assertTrue(repo.save(sc));

    int code =
        new ScrollUpdateSubcommand().run(new String[] {"--id", "S2", "--name", "NewName"});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Updated"));

    Scroll cur = repo.findById("S2").orElseThrow();
    assertEquals("NewName", cur.name());
    assertEquals(5L, cur.downloadCount());
    assertEquals(payload.toString(), cur.filePath());
    assertEquals("X", Files.readString(Path.of(cur.filePath()), StandardCharsets.UTF_8));
  }

  @Test
  void replaceFile_promptDecline_aborts_exit0_noChange() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("dave", "", "", "ID-3", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-upd3-", ".bin");
    Files.writeString(payload, "OLD", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "S3",
            "Doc3",
            "ID-3",
            Instant.parse("2025-01-03T00:00:00Z").toString(),
            payload.toString(),
            0L);
    assertTrue(repo.save(sc));

    Path newSrc = Files.createTempFile("vsas-upd3-src-", ".bin");
    Files.writeString(newSrc, "NEW", StandardCharsets.UTF_8);

    System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));
    int code =
        new ScrollUpdateSubcommand()
            .run(new String[] {"--id", "S3", "--file", newSrc.toString()});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Aborted"));

    assertEquals("OLD", Files.readString(payload, StandardCharsets.UTF_8));
  }

  @Test
  void replaceFile_promptAccept_success_exit0_andFileChanged() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("erin", "", "", "ID-4", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-upd4-", ".bin");
    Files.writeString(payload, "OLD", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "S4",
            "Doc4",
            "ID-4",
            Instant.parse("2025-01-04T00:00:00Z").toString(),
            payload.toString(),
            2L);
    assertTrue(repo.save(sc));

    Path newSrc = Files.createTempFile("vsas-upd4-src-", ".bin");
    Files.writeString(newSrc, "NEW", StandardCharsets.UTF_8);

    System.setIn(new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)));
    int code =
        new ScrollUpdateSubcommand()
            .run(new String[] {"--id", "S4", "--file", newSrc.toString()});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Updated"));

    assertEquals("NEW", Files.readString(payload, StandardCharsets.UTF_8));
    Scroll cur = repo.findById("S4").orElseThrow();
    assertEquals("Doc4", cur.name());
    assertEquals(2L, cur.downloadCount());
    assertEquals(payload.toString(), cur.filePath());
  }

  @Test
  void replaceFile_withYesFlag_skipsPrompt_andSucceeds() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("frank", "", "", "ID-5", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-upd5-", ".bin");
    Files.writeString(payload, "A", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "S5",
            "Doc5",
            "ID-5",
            Instant.parse("2025-01-05T00:00:00Z").toString(),
            payload.toString(),
            9L);
    assertTrue(repo.save(sc));

    Path newSrc = Files.createTempFile("vsas-upd5-src-", ".bin");
    Files.writeString(newSrc, "B", StandardCharsets.UTF_8);

    int code =
        new ScrollUpdateSubcommand()
            .run(new String[] {"--id", "S5", "--file", newSrc.toString(), "--yes"});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Updated"));
    assertEquals("B", Files.readString(payload, StandardCharsets.UTF_8));
  }

  @Test
  void replaceFile_sourceMissing_exit1() throws Exception {
    SessionService s = new SessionService();
    assertTrue(s.login(new User("gina", "", "", "ID-6", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-upd6-", ".bin");
    Files.writeString(payload, "Z", StandardCharsets.UTF_8);
    Scroll sc =
        new Scroll(
            "S6",
            "Doc6",
            "ID-6",
            Instant.parse("2025-01-06T00:00:00Z").toString(),
            payload.toString(),
            0L);
    assertTrue(repo.save(sc));

    Path missing = Path.of("/path/not/exist/file.bin");
    int code =
        new ScrollUpdateSubcommand()
            .run(new String[] {"--id", "S6", "--file", missing.toString()});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("file not found"));
    assertEquals("Z", Files.readString(payload, StandardCharsets.UTF_8));
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
