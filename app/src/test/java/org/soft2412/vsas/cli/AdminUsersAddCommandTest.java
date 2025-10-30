package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.security.PasswordHasher;
import org.soft2412.vsas.service.SessionService;

public class AdminUsersAddCommandTest {

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;

  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;

  private Path sessionDir;
  private Path sessionFile;
  private String prevSessionProp;

  @BeforeEach
  void setup() throws Exception {
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

    cleanupDataDir();

    sessionDir = Files.createTempDirectory("vsas-session-admin-add-");
    sessionFile = sessionDir.resolve("session.properties");
    prevSessionProp = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());
  }

  @AfterEach
  void teardown() throws Exception {
    System.setOut(originalOut);
    System.setErr(originalErr);

    PasswordPrompt.setTestProvider(null);

    if (prevSessionProp == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", prevSessionProp);
    }
    if (sessionFile != null) Files.deleteIfExists(sessionFile);
    if (sessionDir != null) Files.deleteIfExists(sessionDir);

    cleanupDataDir();
  }

  @Test
  void success_withPasswordFlag_persists_andPrintsCreated() {
    loginAsAdmin("root", "A-0");

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "add",
                  "--username",
                  "alice",
                  "--id-key",
                  "U-1",
                  "--role",
                  "admin",
                  "--email",
                  "alice@example.com",
                  "--phone",
                  "0400000000",
                  "--password",
                  "Secr3t!"
                });

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Created: alice (admin)"));

    UserRepository repo = new FileUserRepository();
    assertTrue(repo.findByUsername("alice").isPresent(), "user should be persisted");
    assertTrue(repo.existsIdKey("U-1"), "idKey should exist");
  }

  @Test
  void success_withPromptedPassword_matching() {
    loginAsAdmin("root", "A-0");

    setPromptPasswords("pw123", "pw123");

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "add",
                  "--username",
                  "bob",
                  "--id-key",
                  "U-2",
                  "--role",
                  "user",
                  "--email",
                  "bob@example.com",
                  "--phone",
                  "0401000000"
                });

    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Created: bob (user)"));

    UserRepository repo = new FileUserRepository();
    assertTrue(repo.findByUsername("bob").isPresent());
    assertTrue(repo.existsIdKey("U-2"));
  }

  @Test
  void duplicate_username_returnsExit1() {
    loginAsAdmin("root", "A-0");

    seedUser("alice", "U-9", "USER");

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "add",
                  "--username",
                  "alice",
                  "--id-key",
                  "U-10",
                  "--role",
                  "user",
                  "--email",
                  "a@example.com",
                  "--phone",
                  "0400000000",
                  "--password",
                  "x"
                });

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Error: username already exists"));
  }

  @Test
  void duplicate_idKey_returnsExit1() {
    loginAsAdmin("root", "A-0");

    seedUser("unique", "U-1", "USER");

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "add",
                  "--username",
                  "bob",
                  "--id-key",
                  "U-1",
                  "--role",
                  "user",
                  "--email",
                  "b@example.com",
                  "--phone",
                  "0400000000",
                  "--password",
                  "x"
                });

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Error: id-key already exists"));
  }

  @Test
  void invalid_role_returnsExit1() {
    loginAsAdmin("root", "A-0");

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "add",
                  "--username",
                  "carl",
                  "--id-key",
                  "U-3",
                  "--role",
                  "superuser",
                  "--email",
                  "c@example.com",
                  "--phone",
                  "0400000000",
                  "--password",
                  "x"
                });

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Invalid role"), "should reject unknown role");
  }

  @Test
  void invalid_email_returnsExit1() {
    loginAsAdmin("root", "A-0");

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "add",
                  "--username",
                  "dave",
                  "--id-key",
                  "U-4",
                  "--role",
                  "user",
                  "--email",
                  "not-an-email",
                  "--phone",
                  "0400000000",
                  "--password",
                  "x"
                });

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Invalid email"), "should reject invalid email");
  }

  @Test
  void invalid_phone_returnsExit1() {
    loginAsAdmin("root", "A-0");

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "add",
                  "--username",
                  "emma",
                  "--id-key",
                  "U-5",
                  "--role",
                  "user",
                  "--email",
                  "e@example.com",
                  "--phone",
                  "abcd",
                  "--password",
                  "x"
                });

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Invalid phone"), "should reject invalid phone");
  }

  @Test
  void missing_requiredFlags_returnsExit2() {
    loginAsAdmin("root", "A-0");

    int code =
        new CommandDispatcher()
            .dispatch(new String[] {"admin", "users", "add", "--username", "x", "--id-key", "K-1"});

    assertEquals(2, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Usage: admin users add"), "should print usage for missing flags");
  }

  @Test
  void permission_nonAdmin_returnsExit1() {
    loginAsUser("alice", "U-1");

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "add",
                  "--username",
                  "bob",
                  "--id-key",
                  "U-2",
                  "--role",
                  "user",
                  "--password",
                  "x"
                });

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Forbidden: admin role required."));
  }

  @Test
  void prompted_password_mismatch_returnsExit1() {
    loginAsAdmin("root", "A-0");
    setPromptPasswords("a", "b");

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "add",
                  "--username",
                  "zack",
                  "--id-key",
                  "U-99",
                  "--role",
                  "user",
                  "--email",
                  "z@example.com",
                  "--phone",
                  "0400000000"
                });

    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Passwords do not match"));
  }

  private void loginAsAdmin(String username, String idKey) {
    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User(username, "", "", idKey, "ADMIN", "", "", Instant.now())),
        "admin login should succeed");
  }

  private void loginAsUser(String username, String idKey) {
    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User(username, "", "", idKey, "USER", "", "", Instant.now())),
        "user login should succeed");
  }

  private void seedUser(String username, String idKey, String role) {
    UserRepository repo = new FileUserRepository();
    PasswordHasher hasher = new PasswordHasher();
    byte[] salt = hasher.generateSalt(16);
    String hash = hasher.hashToHex("x".toCharArray(), salt);
    String saltHex = PasswordHasher.bytesToHex(salt);
    assertTrue(
        repo.save(
            new User(
                username,
                username + "@ex.com",
                "0400000000",
                idKey,
                role,
                hash,
                saltHex,
                Instant.now())),
        "seed user should be saved");
  }

  private void setPromptPasswords(String first, String second) {
    final char[] p1 = first.toCharArray();
    final char[] p2 = second.toCharArray();
    final AtomicInteger idx = new AtomicInteger(0);
    PasswordPrompt.setTestProvider(
        (out, prompt) -> {
          int i = idx.getAndIncrement();
          if (i == 0) return Arrays.copyOf(p1, p1.length);
          return Arrays.copyOf(p2, p2.length);
        });
  }

  private void cleanupDataDir() throws Exception {
    Path data = Paths.get("data");
    if (Files.exists(data)) {
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
}
