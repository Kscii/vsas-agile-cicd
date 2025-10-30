package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.service.SessionService;

public class AdminUsersListCommandTest {

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;

  private Path dataDir;
  private Path usersTsv;

  private Path sessionDir;
  private Path sessionFile;
  private String prevSessionPathProp;

  @BeforeEach
  void setup() throws Exception {
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

    dataDir = Path.of("data");
    Files.createDirectories(dataDir);
    usersTsv = dataDir.resolve("users.tsv");
    Files.deleteIfExists(usersTsv);

    sessionDir = Files.createTempDirectory("vsas-session-admin-list-");
    sessionFile = sessionDir.resolve("session.properties");
    prevSessionPathProp = System.getProperty("vsas.session.path");
    System.setProperty("vsas.session.path", sessionFile.toString());
  }

  @AfterEach
  void teardown() throws Exception {
    System.setOut(originalOut);
    System.setErr(originalErr);

    if (prevSessionPathProp == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", prevSessionPathProp);
    }

    if (sessionFile != null) {
      Files.deleteIfExists(sessionFile);
    }
    if (sessionDir != null) {
      Files.deleteIfExists(sessionDir);
    }

    if (Files.exists(dataDir)) {
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

    Path upperData = Path.of("..", "data");
    if (Files.exists(upperData)) {
      Files.walk(upperData)
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

  @Test
  void allUsers_printsHeaderAndRows() throws Exception {
    writeUsersBothPlaces(
        new String[] {
          "username\temail\tphone\tidKey\trole\tpasswordHash\tsalt\tcreatedAt",
          joinUser("alice", "alice@ex.com", "0400", "U-1", "USER", "2025-01-01T00:00:00Z"),
          joinUser("bob", "bob@ex.com", "0401", "U-2", "ADMIN", "2025-01-02T00:00:00Z")
        });

    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("root", "", "", "A-0", "ADMIN", "", "")));

    AdminUsersListCommand cmd = new AdminUsersListCommand(System.out, System.err, sessions);
    int code = cmd.run(new String[] {});
    assertEquals(0, code);

    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertEquals("", err);

    String out = outBuf.toString(StandardCharsets.UTF_8);

    assertTrue(out.toLowerCase().contains("username"));
    assertTrue(out.toLowerCase().contains("email"));
    assertTrue(out.toLowerCase().contains("phone"));
    assertTrue(out.toLowerCase().contains("idkey"));
    assertTrue(out.toLowerCase().contains("role"));
    assertTrue(out.toLowerCase().contains("createdat"));

    assertTrue(out.contains("alice"));
    assertTrue(out.contains("alice@ex.com"));
    assertTrue(out.contains("U-1"));
    assertTrue(out.contains("user"));

    assertTrue(out.contains("bob"));
    assertTrue(out.contains("bob@ex.com"));
    assertTrue(out.contains("U-2"));
    assertTrue(out.contains("admin"));
  }

  @Test
  void filters_withUsernameAndIdKeyAndRole() throws Exception {
    writeUsersBothPlaces(
        new String[] {
          "username\temail\tphone\tidKey\trole\tpasswordHash\tsalt\tcreatedAt",
          joinUser("alice", "alice@ex.com", "0400", "U-1", "USER", "2025-01-01T00:00:00Z"),
          joinUser("bob", "bob@ex.com", "0401", "U-2", "ADMIN", "2025-06-01T12:00:00Z"),
          joinUser("bob", "bobby@ex.com", "0402", "U-3", "USER", "2025-07-07T07:07:07Z")
        });

    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User("root", "", "", "A-0", "ADMIN", "", "")));

    AdminUsersListCommand cmd = new AdminUsersListCommand(System.out, System.err, sessions);
    int code = cmd.run(new String[] {"--username", "bob", "--id-key", "U-2", "--role", "admin"});
    assertEquals(0, code);

    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertEquals("", err);

    String out = outBuf.toString(StandardCharsets.UTF_8);

    assertTrue(out.toLowerCase().contains("username"));
    assertTrue(out.contains("bob"));
    assertTrue(out.contains("bob@ex.com"));
    assertTrue(out.contains("U-2"));
    assertTrue(out.contains("admin"));

    assertFalse(out.contains("alice@ex.com"));
    assertFalse(out.contains("bobby@ex.com"));
    assertFalse(out.contains("U-3"));
  }

  private static String joinUser(
      String username, String email, String phone, String idKey, String role, String createdAt) {
    return String.join("\t", username, email, phone, idKey, role, "aa", "bb", createdAt);
  }

  private void writeUsersBothPlaces(String[] lines) throws Exception {
    Files.write(
        usersTsv,
        (String.join(System.lineSeparator(), lines) + System.lineSeparator())
            .getBytes(StandardCharsets.UTF_8));
    Path upperData = Path.of("..", "data");
    Files.createDirectories(upperData);
    Path upperUsers = upperData.resolve("users.tsv");
    Files.write(
        upperUsers,
        (String.join(System.lineSeparator(), lines) + System.lineSeparator())
            .getBytes(StandardCharsets.UTF_8));
  }
}
