package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private static final String TABLE_FMT = "%-16s  %-28s  %-14s  %-16s  %-6s  %-20s";

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

    SessionService sessions = new SessionService();
    assertTrue(
        sessions.login(new User("root", "", "", "A-0", "ADMIN", "", "")),
        "admin login should succeed");
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
  }

  @Test
  void allUsers_printsHeaderAndRows() throws Exception {
    writeUsersTsv(
        new String[] {
          "username\temail\tphone\tidKey\trole\tpasswordHash\tsalt\tcreatedAt",
          joinUser("alice", "alice@ex.com", "0400", "U-1", "USER", "2025-01-01T00:00:00Z"),
          joinUser("bob", "bob@ex.com", "0401", "U-2", "ADMIN", "2025-01-02T00:00:00Z")
        });

    int code = new CommandDispatcher().dispatch(new String[] {"admin", "users", "list"});
    assertEquals(0, code);
    assertEquals("", errBuf.toString(StandardCharsets.UTF_8));

    String out = outBuf.toString(StandardCharsets.UTF_8);
    String expectedHeader =
        String.format(TABLE_FMT, "username", "email", "phone", "idKey", "role", "createdAt");
    assertTrue(out.contains(expectedHeader), "header should be printed");

    String rowAlice =
        String.format(
            TABLE_FMT, "alice", "alice@ex.com", "0400", "U-1", "user", "2025-01-01T00:00:00Z");
    String rowBob =
        String.format(
            TABLE_FMT, "bob", "bob@ex.com", "0401", "U-2", "admin", "2025-01-02T00:00:00Z");

    assertTrue(out.contains(rowAlice), "should contain alice row");
    assertTrue(out.contains(rowBob), "should contain bob row");
  }

  @Test
  void filters_withUsernameAndIdKeyAndRole() throws Exception {
    writeUsersTsv(
        new String[] {
          "username\temail\tphone\tidKey\trole\tpasswordHash\tsalt\tcreatedAt",
          joinUser("alice", "alice@ex.com", "0400", "U-1", "USER", "2025-01-01T00:00:00Z"),
          joinUser("bob", "bob@ex.com", "0401", "U-2", "ADMIN", "2025-06-01T12:00:00Z"),
          joinUser("bob", "bobby@ex.com", "0402", "U-3", "USER", "2025-07-07T07:07:07Z")
        });

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "admin",
                  "users",
                  "list",
                  "--username",
                  "bob",
                  "--id-key",
                  "U-2",
                  "--role",
                  "admin"
                });
    assertEquals(0, code);
    assertEquals("", errBuf.toString(StandardCharsets.UTF_8));

    String out = outBuf.toString(StandardCharsets.UTF_8);
    String expectedHeader =
        String.format(TABLE_FMT, "username", "email", "phone", "idKey", "role", "createdAt");
    assertTrue(out.contains(expectedHeader), "header should be printed");

    String expectedRow =
        String.format(
            TABLE_FMT, "bob", "bob@ex.com", "0401", "U-2", "admin", "2025-06-01T12:00:00Z");
    assertTrue(out.contains(expectedRow), "should contain only the matching bob/U-2/admin row");

    assertFalse(out.contains("alice"), "alice should be filtered out");
    assertFalse(out.contains("bobby@ex.com"), "non-matching bob row should be filtered out");
  }

  private static String joinUser(
      String username, String email, String phone, String idKey, String role, String createdAt) {
    return String.join("\t", username, email, phone, idKey, role, "aa", "bb", createdAt);
  }

  private void writeUsersTsv(String[] lines) throws Exception {
    Files.write(
        usersTsv,
        (String.join(System.lineSeparator(), lines) + System.lineSeparator())
            .getBytes(StandardCharsets.UTF_8));
  }
}
