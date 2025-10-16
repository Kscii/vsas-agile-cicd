package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.security.PasswordHasher;

/**
 * Black-box tests for LoginCommand: - success with correct password using salted hash verification
 * - failure with wrong password
 *
 * <p>Tests prepare a data/users.tsv file under the project working directory with a single user row
 * and known salt/hash.
 */
public class LoginCommandTest {

  private final PasswordHasher hasher = new PasswordHasher();
  private Path dataDir;
  private Path usersTsv;

  @BeforeEach
  void setup() throws Exception {
    dataDir = Path.of("data");
    usersTsv = dataDir.resolve("users.tsv");
    Files.createDirectories(dataDir);

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
    // Clean up test data directory
    if (Files.exists(usersTsv)) Files.delete(usersTsv);
    File d = dataDir.toFile();
    if (d.exists()) d.delete();
  }

  @Test
  void login_success_whenPasswordMatchesHashed() {
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    LoginCommand cmd = new LoginCommand(new PrintStream(outBuf), new PrintStream(errBuf), hasher);

    int code = cmd.run(new String[] {"--username", "alice", "--password", "P@ssw0rd!"});

    assertEquals(0, code, "exit code should be 0");
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("Login success"), "stdout should contain success message");
    assertTrue(errBuf.toString(StandardCharsets.UTF_8).isEmpty(), "stderr should be empty");
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
  }
}
