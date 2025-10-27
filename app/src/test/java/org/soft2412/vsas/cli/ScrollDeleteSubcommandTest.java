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

class ScrollDeleteSubcommandTest {

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

    sessionDir = Files.createTempDirectory("vsas-session-del-");
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
  void missingId_showsUsage_exit2() {
    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("guest", "", "", "ID-guest", "USER", "", "")),
        "login should succeed");

    outBuf.reset();
    errBuf.reset();

    int code = new ScrollDeleteSubcommand().run(new String[] {"--yes"});
    assertEquals(2, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Usage"));
  }

  @Test
  void notLoggedIn_exit1() {
    int code = new ScrollDeleteSubcommand().run(new String[] {"--id", "S_NOTHING"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("please login"));
  }

  @Test
  void notFound_exit1() throws Exception {
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("alice", "", "", "ID-1", "USER", "", "")));

    int code = new ScrollDeleteSubcommand().run(new String[] {"--id", "S_MISSING"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Not found"));
  }

  @Test
  void forbiddenWhenNotOwner_exit1() throws Exception {

    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("bob", "", "", "ID-OTHER", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-del-", ".bin");
    Files.writeString(payload, "DATA", StandardCharsets.UTF_8);

    Scroll s =
        new Scroll(
            "S1",
            "Doc",
            "ID-OWNER",
            Instant.parse("2025-01-01T00:00:00Z").toString(),
            payload.toString(),
            0L,
            0L);
    assertTrue(repo.save(s));

    int code = new ScrollDeleteSubcommand().run(new String[] {"--id", "S1"});
    assertEquals(1, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("only the uploader"));
    assertTrue(repo.findById("S1").isPresent());
    assertTrue(Files.exists(payload));
  }

  @Test
  void abortByPrompt_exit0_andNoDeletion() throws Exception {
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("carol", "", "", "ID-2", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-del2-", ".bin");
    Files.writeString(payload, "X", StandardCharsets.UTF_8);

    Scroll s =
        new Scroll(
            "S2",
            "Doc2",
            "ID-2",
            Instant.parse("2025-01-02T00:00:00Z").toString(),
            payload.toString(),
            0L,
            0L);
    assertTrue(repo.save(s));

    System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));
    int code = new ScrollDeleteSubcommand().run(new String[] {"--id", "S2"});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Aborted"));

    assertTrue(repo.findById("S2").isPresent());
    assertTrue(Files.exists(payload));
  }

  @Test
  void successWithYesFlag_exit0_removedFromRepo_andPayloadDeletedIfExists() throws Exception {
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("dave", "", "", "ID-3", "USER", "", "")));

    ScrollRepository repo = new FileScrollRepository();
    Path payload = Files.createTempFile("vsas-del3-", ".bin");
    Files.writeString(payload, "DELME", StandardCharsets.UTF_8);

    Scroll s =
        new Scroll(
            "S3",
            "Doc3",
            "ID-3",
            Instant.parse("2025-01-03T00:00:00Z").toString(),
            payload.toString(),
            0L,
            0L);
    assertTrue(repo.save(s));

    int code = new ScrollDeleteSubcommand().run(new String[] {"--id", "S3", "--yes"});
    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Deleted"));

    assertTrue(repo.findById("S3").isEmpty());

    assertFalse(Files.exists(payload), "payload file should be deleted by deleteById");
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
