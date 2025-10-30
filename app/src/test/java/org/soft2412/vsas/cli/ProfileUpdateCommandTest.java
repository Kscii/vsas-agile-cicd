package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.service.SessionService;

class ProfileUpdateCommandTest {

  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;
  private PrintStream out;
  private PrintStream err;
  private RecordingRepo repo;
  private SessionService sessions;
  private Path tempDir;
  private Path sessionFile;
  private String previousSessionProperty;

  @BeforeEach
  void setup() throws Exception {
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
    err = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
    repo = new RecordingRepo();

    tempDir = Files.createTempDirectory("vsas-profile-session-");
    sessionFile = tempDir.resolve("session.properties");
    previousSessionProperty = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());
    sessions = new SessionService();
    PasswordPrompt.setTestProvider(null);
  }

  @AfterEach
  void tearDown() throws Exception {
    PasswordPrompt.setTestProvider(null);
    if (previousSessionProperty == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionProperty);
    }
    Files.deleteIfExists(sessionFile);
    Files.deleteIfExists(tempDir);
    out.close();
    err.close();
  }

  @Test
  void run_requiresAuthenticatedUser() {
    int code = newCommand().run(new String[] {"update", "--email", "alice@new"});

    assertEquals(1, code);
    assertFalse(repo.updateCalled);
    assertTrue(stderr().toLowerCase().contains("permission"), "should mention permission");
  }

  @Test
  void rejectsInvocationWithoutFields() {
    loginAlice();

    int code = newCommand().run(new String[] {"update"});

    assertEquals(2, code);
    assertFalse(repo.updateCalled);
    assertTrue(stderr().contains("specify at least one field"));
  }

  @Test
  void updatesEmailAndPhoneWhenProvided() throws Exception {
    loginAlice();

    int code = newCommand().run(new String[] {"update", "--email", "alice@new", "--phone", "0499"});

    assertEquals(0, code);
    assertTrue(repo.updateCalled);
    assertEquals("alice", repo.lastUsername);
    assertEquals("alice@new", repo.lastEmail);
    assertEquals("0499", repo.lastPhone);
    assertNull(repo.lastPassword, "password should be unchanged");
    assertTrue(stdout().contains("Updated: alice."));
    assertTrue(Files.exists(sessionFile), "session should remain when password unchanged");
  }

  @Test
  void passwordPromptMismatchFails() throws Exception {
    loginAlice();
    PasswordPrompt.setTestProvider(
        new PasswordPrompt.Provider() {
          private int calls = 0;

          @Override
          public char[] readMasked(PrintStream ignoredOut, String prompt) {
            calls++;
            return calls == 1 ? "alpha".toCharArray() : "beta".toCharArray();
          }
        });

    int code = newCommand().run(new String[] {"update", "--password"});

    assertEquals(1, code);
    assertFalse(repo.updateCalled);
    assertTrue(stderr().contains("Passwords do not match"));
    assertTrue(Files.exists(sessionFile), "session file should still exist");
  }

  @Test
  void passwordChangeHashesAndLogsOut() throws Exception {
    loginAlice();
    PasswordPrompt.setTestProvider(
        new PasswordPrompt.Provider() {
          @Override
          public char[] readMasked(PrintStream ignoredOut, String prompt) {
            return "NewPass123".toCharArray();
          }
        });

    int code = newCommand().run(new String[] {"update", "--password"});

    assertEquals(0, code);
    assertTrue(repo.updateCalled);
    assertNotNull(repo.lastPassword);
    assertArrayEquals("NewPass123".toCharArray(), repo.lastPassword);
    assertTrue(stdout().contains("Updated: alice."));
    assertFalse(Files.exists(sessionFile), "logout should remove session file");
  }

  @Test
  void repositoryFailureSurfacesError() throws Exception {
    loginAlice();
    repo.updateResult = false;

    int code = newCommand().run(new String[] {"update", "--email", "alice@new"});

    assertEquals(3, code);
    assertTrue(repo.updateCalled);
    assertTrue(stderr().contains("unable to update profile"));
    assertTrue(Files.exists(sessionFile), "session should remain on failure");
  }

  @Test
  void passwordFlagWithInlineValueRejected() throws Exception {
    loginAlice();

    int code = newCommand().run(new String[] {"update", "--password", "secret"});

    assertEquals(2, code);
    assertFalse(repo.updateCalled);
    assertTrue(stderr().contains("--password does not take a value"));
  }

  private ProfileUpdateCommand newCommand() {
    return new ProfileUpdateCommand(out, err, repo, sessions);
  }

  private void loginAlice() {
    User user =
        new User(
            "alice", "a@x", "0400", "K-001", "USER", "h".repeat(64), "s".repeat(32), Instant.now());
    assertTrue(sessions.login(user));
    assertTrue(Files.exists(sessionFile));
  }

  private String stdout() {
    return outBuf.toString(StandardCharsets.UTF_8);
  }

  private String stderr() {
    return errBuf.toString(StandardCharsets.UTF_8);
  }

  private static final class RecordingRepo implements UserRepository {
    boolean updateCalled;
    boolean updateResult = true;
    String lastUsername;
    String lastEmail;
    String lastPhone;
    char[] lastPassword;

    @Override
    public Optional<User> findByUsername(String username) {
      return Optional.empty();
    }

    @Override
    public Optional<User> findByIdKey(String idKey) {
      return Optional.empty();
    }

    @Override
    public boolean existsIdKey(String idKey) {
      return false;
    }

    @Override
    public boolean save(User user) {
      return false;
    }

    @Override
    public boolean updateProfile(
        String username, String newEmail, String newPhone, char[] newPassword) {
      this.updateCalled = true;
      this.lastUsername = username;
      this.lastEmail = newEmail;
      this.lastPhone = newPhone;
      this.lastPassword =
          newPassword == null ? null : Arrays.copyOf(newPassword, newPassword.length);
      return updateResult;
    }

    @Override
    public boolean deleteByUsername(String username) {
      return false;
    }
  }
}
