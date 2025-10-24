package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.security.PasswordHasher;

/**
 * Black-box tests for LoginCommand: - success with correct password using salted hash verification
 * - failure with wrong password - interactive prompt path (single prompt) - missing username fails
 * fast with usage (no prompt)
 */
public class LoginCommandTest {

  private final PasswordHasher hasher = new PasswordHasher();
  private Path dataDir;
  private Path usersTsv;
  private Path sessionDir;
  private Path sessionFile;
  private String previousSessionProperty;

  @BeforeEach
  void setup() throws Exception {
    dataDir = Path.of("data");
    usersTsv = dataDir.resolve("users.tsv");
    Files.createDirectories(dataDir);

    sessionDir = Files.createTempDirectory("vsas-session-");
    sessionFile = sessionDir.resolve("session.properties");
    previousSessionProperty = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());

    // Prepare one user with salted hash
    String username = "alice";
    char[] password = "P@ssw0rd!".toCharArray();
    byte[] salt = hasher.generateSalt(16);
    String saltHex = PasswordHasher.bytesToHex(salt);
    String hashHex = hasher.hashToHex(password, salt);

    String header =
        String.join(
                "\t",
                "username",
                "email",
                "phone",
                "idKey",
                "role",
                "passwordHash",
                "salt",
                "createdAt")
            + "\n";
    String row =
        String.join(
                "\t",
                username,
                "alice@example.com",
                "0400000000",
                "K-001",
                "USER",
                hashHex,
                saltHex,
                "2025-10-16T00:00:00Z")
            + "\n";

    Files.writeString(usersTsv, header + row, StandardCharsets.UTF_8);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (Files.exists(usersTsv)) Files.delete(usersTsv);
    File d = dataDir.toFile();
    if (d.exists()) d.delete();

    if (Files.exists(sessionFile)) {
      Files.delete(sessionFile);
    }
    if (sessionDir != null && Files.exists(sessionDir)) {
      Files.delete(sessionDir);
    }
    if (previousSessionProperty == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionProperty);
    }

    // Ensure provider cleared between tests
    PasswordPrompt.setTestProvider(null);
  }

  @Test
  void login_success_whenPasswordMatchesHashed() throws Exception {
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    LoginCommand cmd = new LoginCommand(new PrintStream(outBuf), new PrintStream(errBuf), hasher);

    int code = cmd.run(new String[] {"--username", "alice", "--password", "P@ssw0rd!"});

    assertEquals(0, code, "exit code should be 0");
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Login success"), "stdout should contain success message");
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).isEmpty(), "stderr should be empty");

    assertTrue(Files.exists(sessionFile), "session file should be written");
    Properties props = new Properties();
    props.load(Files.newBufferedReader(sessionFile, StandardCharsets.UTF_8));
    assertEquals("alice", props.getProperty("username"));
    assertEquals("K-001", props.getProperty("idKey"));
    assertEquals("USER", props.getProperty("role"));
    assertNotNull(props.getProperty("issuedAt"));
  }

  @Test
  void login_fail_whenPasswordWrong() {
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    LoginCommand cmd = new LoginCommand(new PrintStream(outBuf), new PrintStream(errBuf), hasher);

    int code = cmd.run(new String[] {"--username", "alice", "--password", "wrong-pass"});

    assertNotEquals(0, code, "exit code should be non-zero");
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("Invalid credentials"), "stderr should contain invalid message");
    assertTrue(Files.notExists(sessionFile), "session file should not be created");
  }

  @Test
  void login_promptsOnce_whenPasswordOmitted_andUsernameProvided() throws Exception {
    // Arrange: provide a single canned password via PasswordPrompt
    final char[] canned = "P@ssw0rd!".toCharArray();
    final AtomicInteger calls = new AtomicInteger(0);
    PasswordPrompt.setTestProvider(
        (o, prompt) -> {
          calls.incrementAndGet();
          return canned.clone();
        });

    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    LoginCommand cmd = new LoginCommand(new PrintStream(outBuf), new PrintStream(errBuf), hasher);

    // Act
    int code = cmd.run(new String[] {"--username", "alice"});

    // Assert
    assertEquals(0, code);
    assertEquals(1, calls.get(), "should prompt exactly once");
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Login success"));
  }

  @Test
  void login_missingUsername_failsFast_withUsage_andNoPrompt() {
    final AtomicInteger calls = new AtomicInteger(0);
    PasswordPrompt.setTestProvider(
        (o, prompt) -> {
          calls.incrementAndGet();
          return "x".toCharArray();
        });

    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    LoginCommand cmd = new LoginCommand(new PrintStream(outBuf), new PrintStream(errBuf), hasher);

    int code = cmd.run(new String[] {}); // no args

    assertEquals(2, code, "should return exit code 2 for usage error");
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(
        err.contains("Error: missing required flags. Usage: login --username <u> [--password <p>]"),
        "should print usage message");
    assertEquals(0, calls.get(), "should not prompt for password when username is missing");
  }
}
