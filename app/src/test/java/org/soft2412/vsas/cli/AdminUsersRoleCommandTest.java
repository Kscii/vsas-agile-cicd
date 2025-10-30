package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.service.SessionService;

class AdminUsersRoleCommandTest {

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

    sessionDir = Files.createTempDirectory("vsas-admin-role-");
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

    if (sessionFile != null) {
      Files.deleteIfExists(sessionFile);
    }
    if (sessionDir != null) {
      Files.deleteIfExists(sessionDir);
    }

    cleanupDataDir();
  }

  @Test
  void changeRole_updatesRepository_exit0() {
    loginAdmin();
    userRepo().save(user("victim", "ID-VICTIM", "USER"));

    int code =
        new AdminUsersRoleCommand().run(new String[] {"--username", "victim", "--role", "admin"});

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Role updated: victim -> admin"), "success message expected");
    assertEquals(
        "ADMIN",
        userRepo().findByUsername("victim").orElseThrow().role(),
        "role should be updated");
  }

  @Test
  void missingRoleFlag_promptsAndUpdates_exit0() {
    loginAdmin();
    userRepo().save(user("victim", "ID-VICTIM", "USER"));
    System.setIn(new ByteArrayInputStream("admin\n".getBytes(StandardCharsets.UTF_8)));

    int code = new AdminUsersRoleCommand().run(new String[] {"--username", "victim"});

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("New role (admin|user): "), "prompt should be shown");
    assertTrue(out.contains("Role updated: victim -> admin"), "success message expected");
    assertEquals(
        "ADMIN",
        userRepo().findByUsername("victim").orElseThrow().role(),
        "role should be updated");
  }

  @Test
  void sameRole_printsNoChange_exit0() {
    loginAdmin();
    userRepo().save(user("victim", "ID-VICTIM", "ADMIN"));

    int code =
        new AdminUsersRoleCommand().run(new String[] {"--username", "victim", "--role", "admin"});

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("No change"), "no-change message expected");
    assertEquals(
        "ADMIN",
        userRepo().findByUsername("victim").orElseThrow().role(),
        "role should remain unchanged");
  }

  @Test
  void invalidRole_exit2() {
    loginAdmin();
    userRepo().save(user("victim", "ID-VICTIM", "USER"));

    int code =
        new AdminUsersRoleCommand()
            .run(new String[] {"--username", "victim", "--role", "moderator"});

    assertEquals(2, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Invalid role"), "invalid message expected");
    assertEquals(
        "USER",
        userRepo().findByUsername("victim").orElseThrow().role(),
        "role should be unchanged");
  }

  @Test
  void nonAdmin_forbidden_exit1() {
    login(new User("staff", "", "", "ID-USER", "USER", "", ""));
    userRepo().save(user("victim", "ID-VICTIM", "USER"));

    int code =
        new AdminUsersRoleCommand().run(new String[] {"--username", "victim", "--role", "admin"});

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("admin role"), "permission message expected");
    assertEquals(
        "USER",
        userRepo().findByUsername("victim").orElseThrow().role(),
        "role should be unchanged");
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
