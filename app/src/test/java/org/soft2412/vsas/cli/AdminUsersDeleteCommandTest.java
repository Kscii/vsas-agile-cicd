package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.service.SessionService;

class AdminUsersDeleteCommandTest {

  private PrintStream originalOut;
  private PrintStream originalErr;
  private InputStream originalIn;
  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;
  private Path sessionDir;
  private Path sessionFile;
  private String previousSessionProperty;
  private Path dataDir;

  @BeforeEach
  void setup() throws Exception {
    originalOut = System.out;
    originalErr = System.err;
    originalIn = System.in;
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    System.setIn(new ByteArrayInputStream(new byte[0]));

    sessionDir = Files.createTempDirectory("vsas-admin-del-");
    sessionFile = sessionDir.resolve("session.properties");
    previousSessionProperty = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());

    dataDir = Path.of("data");
    cleanupDataDir();
    Files.createDirectories(dataDir);
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
  void cancelConfirmation_keepsUser_exit0() throws Exception {
    loginAdmin();
    userRepo().save(user("victim", "ID-V", "USER"));

    System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));

    int code = new AdminUsersDeleteCommand().run(new String[] {"--username", "victim"});

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("username=victim"), "summary should be shown");
    assertTrue(out.contains("Aborted."), "abort message expected");
    assertTrue(userRepo().findByUsername("victim").isPresent(), "user should remain");
  }

  @Test
  void promptForUsername_thenDelete_exit0() throws Exception {
    loginAdmin();
    userRepo().save(user("victim", "ID-VICTIM", "USER"));

    System.setIn(new ByteArrayInputStream("victim\ny\n".getBytes(StandardCharsets.UTF_8)));

    int code = new AdminUsersDeleteCommand().run(new String[0]);

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Username: "), "should prompt for username");
    assertTrue(out.contains("username=victim"), "summary should include username");
    assertTrue(out.contains("idKey=ID-VICTIM"), "summary should include idKey");
    assertTrue(out.contains("role=USER"), "summary should include role");
    assertTrue(out.contains("Deleted user victim"), "should confirm deletion");
    assertTrue(userRepo().findByUsername("victim").isEmpty(), "user should be removed");
  }

  @Test
  void yesFlagWithoutUsername_stillPromptsForConfirmation_exit0() throws Exception {
    loginAdmin();
    userRepo().save(user("victim", "ID-VYES", "USER"));

    System.setIn(new ByteArrayInputStream("victim\ny\n".getBytes(StandardCharsets.UTF_8)));

    int code = new AdminUsersDeleteCommand().run(new String[] {"--yes"});

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Username: "), "should prompt for username when flag absent");
    assertTrue(
        out.contains("Delete user victim? This cannot be undone. [y/N] "),
        "should still ask for confirmation");
    assertTrue(out.contains("Deleted user victim"), "should confirm deletion");
    assertTrue(userRepo().findByUsername("victim").isEmpty(), "user should be removed");
  }

  @Test
  void deletionBlockedWhenUserOwnsScrolls_exit1() throws Exception {
    loginAdmin();
    userRepo().save(user("victim", "ID-SCROLL", "USER"));
    ScrollRepository scrollRepo = new FileScrollRepository();
    Path payload = Files.createTempFile("payload-", ".bin");
    Files.writeString(payload, "data", StandardCharsets.UTF_8);
    scrollRepo.save(
        new Scroll(
            "S-1",
            "Scroll",
            "ID-SCROLL",
            Instant.parse("2025-01-01T00:00:00Z").toString(),
            payload.toString(),
            1L,
            0L));

    int code = new AdminUsersDeleteCommand().run(new String[] {"--username", "victim", "--yes"});

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("owns scrolls"), "should report ownership guard");
    assertTrue(userRepo().findByUsername("victim").isPresent(), "user should remain");
  }

  @Test
  void selfDeleteBlocked_exit1() {
    User admin = user("root", "ID-ADMIN", "ADMIN");
    userRepo().save(admin);
    login(admin);

    int code = new AdminUsersDeleteCommand().run(new String[] {"--username", "root"});

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.toLowerCase().contains("cannot delete"), "should block self-delete");
  }

  @Test
  void successWithYesFlag_removesUser_exit0() throws Exception {
    loginAdmin();
    userRepo().save(user("victim", "ID-VICTIM", "USER"));

    int code = new AdminUsersDeleteCommand().run(new String[] {"--username", "victim", "--yes"});

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Deleted user victim"), "should confirm deletion");
    assertTrue(userRepo().findByUsername("victim").isEmpty(), "user should be removed");
  }

  @Test
  void nonAdminCannotDelete_exit1() throws Exception {
    login(new User("staff", "", "", "ID-USER", "USER", "", ""));
    userRepo().save(user("victim", "ID-V", "USER"));

    int code = new AdminUsersDeleteCommand().run(new String[] {"--username", "victim", "--yes"});

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("admin role"), "should require admin role");
    assertTrue(userRepo().findByUsername("victim").isPresent(), "user should remain");
  }

  private void loginAdmin() {
    login(new User("admin", "", "", "ID-ADMIN", "ADMIN", "", ""));
  }

  private void login(User user) {
    SessionService service = new SessionService();
    assertTrue(service.login(user), "login should succeed for test setup");
  }

  private UserRepository userRepo() {
    return new FileUserRepository();
  }

  private User user(String username, String idKey, String role) {
    return new User(username, username + "@example.com", "0400000000", idKey, role, "", "");
  }

  private void cleanupDataDir() throws Exception {
    if (!Files.exists(dataDir)) {
      return;
    }
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
