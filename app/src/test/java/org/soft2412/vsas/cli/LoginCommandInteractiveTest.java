package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.soft2412.vsas.security.PasswordHasher;

public class LoginCommandInteractiveTest {

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

    // Prepare one user with salted hash (password: Secret1!)
    char[] password = "Secret1!".toCharArray();
    byte[] salt = hasher.generateSalt(16);
    String saltHex = org.soft2412.vsas.security.PasswordHasher.bytesToHex(salt);
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
                "alice",
                "alice@example.com",
                "0400",
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
    PasswordPrompt.setTestProvider(null);
  }

  @Test
  void login_withoutPassword_promptsOnce_andSucceeds_withCorrectPassword() throws Exception {
    // Arrange provider: prompt exactly once, return the correct password
    final AtomicInteger calls = new AtomicInteger(0);
    PasswordPrompt.setTestProvider(
        (out, prompt) -> {
          calls.incrementAndGet();
          if (out != null) {
            out.print(prompt);
            out.flush();
          }
          return "Secret1!".toCharArray();
        });

    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    LoginCommand cmd = new LoginCommand(new PrintStream(outBuf), new PrintStream(errBuf), hasher);

    int code = cmd.run(new String[] {"--username", "alice"});

    assertEquals(0, code);
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Login success"));
    assertEquals(1, calls.get(), "should prompt exactly once");
    assertTrue(Files.exists(sessionFile));

    Properties props = new Properties();
    props.load(Files.newBufferedReader(sessionFile, StandardCharsets.UTF_8));
    assertEquals("alice", props.getProperty("username"));
    assertEquals("K-001", props.getProperty("idKey"));
    assertEquals("USER", props.getProperty("role"));
    assertNotNull(props.getProperty("issuedAt"));
  }

  @Test
  void login_withoutPassword_promptsOnce_andFails_withWrongPassword() {
    // Arrange provider: prompt exactly once, return a wrong password
    final AtomicInteger calls = new AtomicInteger(0);
    PasswordPrompt.setTestProvider(
        (out, prompt) -> {
          calls.incrementAndGet();
          if (out != null) {
            out.print(prompt);
            out.flush();
          }
          return "totally-wrong".toCharArray();
        });

    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    LoginCommand cmd = new LoginCommand(new PrintStream(outBuf), new PrintStream(errBuf), hasher);

    int code = cmd.run(new String[] {"--username", "alice"});

    assertNotEquals(0, code);
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Invalid credentials"));
    assertEquals(1, calls.get(), "should prompt exactly once");
    assertTrue(Files.notExists(sessionFile));
  }
}
