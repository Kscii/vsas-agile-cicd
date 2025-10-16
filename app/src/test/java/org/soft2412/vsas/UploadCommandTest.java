package org.soft2412.vsas;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soft2412.vsas.cli.CommandDispatcher;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.service.SessionService;

public class UploadCommandTest {
  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;
  private String previousSessionProperty;
  private Path sessionPath;

  @BeforeEach
  public void setup() throws IOException {
    out = new ByteArrayOutputStream();
    err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    System.setErr(new PrintStream(err));
    cleanupDataDir();
    Files.createDirectories(Path.of("data"));
    previousSessionProperty = System.getProperty("vsas.session.path");
    sessionPath = Path.of("data", "session.properties");
    System.setProperty("vsas.session.path", sessionPath.toString());
  }

  @AfterEach
  public void teardown() throws IOException {
    System.setOut(originalOut);
    System.setErr(originalErr);
    cleanupDataDir();
    if (previousSessionProperty == null) {
      System.clearProperty("vsas.session.path");
    } else {
      System.setProperty("vsas.session.path", previousSessionProperty);
    }
  }

  @Test
  public void uploadSuccess() throws IOException {
    writeSession("alice", "u-123", "user");
    Path tmp = Path.of("data", "tmp");
    Files.createDirectories(tmp);
    Path src = tmp.resolve("sample.bin");
    Files.writeString(src, "hello", StandardCharsets.UTF_8);

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "upload", "--id", "s1", "--name", "My Scroll", "--file", src.toString()
                });
    assertEquals(0, code);
    assertTrue(out.toString().contains("upload: success"));

    assertTrue(Files.exists(Path.of("data", "files", "s1.bin")));
    assertTrue(Files.exists(Path.of("data", "scrolls.tsv")));
  }

  @Test
  public void uploadDuplicateIdFails() throws IOException {
    writeSession("alice", "u-123", "user");
    Path tmp = Path.of("data", "tmp");
    Files.createDirectories(tmp);
    Path src = tmp.resolve("sample.bin");
    Files.writeString(src, "hello", StandardCharsets.UTF_8);

    int first =
        new CommandDispatcher()
            .dispatch(
                new String[] {"upload", "--id", "dup1", "--name", "A", "--file", src.toString()});
    assertEquals(0, first);
    out.reset();
    err.reset();

    int second =
        new CommandDispatcher()
            .dispatch(
                new String[] {"upload", "--id", "dup1", "--name", "B", "--file", src.toString()});
    assertEquals(2, second);
    assertTrue(err.toString().contains("id already exists"));
  }

  @Test
  public void uploadRequiresLogin() throws IOException {
    // Ensure no session
    cleanupDataDir();
    Files.createDirectories(Path.of("data"));

    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "upload", "--id", "s2", "--name", "N", "--file", "data/tmp/missing.bin"
                });
    assertEquals(2, code);
    assertTrue(err.toString().contains("login required"));
  }

  @Test
  public void uploadMissingFileFails() throws IOException {
    writeSession("bob", "u-456", "user");
    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {"upload", "--id", "s3", "--name", "N", "--file", "no/such/file.bin"});
    assertEquals(2, code);
    assertTrue(err.toString().contains("file not found"));
  }

  private void writeSession(String username, String idKey, String role) throws IOException {
    SessionService sessions = new SessionService();
    assertTrue(sessions.login(new User(username, "", "", idKey, role, "", "")), "session write");
  }

  private void cleanupDataDir() throws IOException {
    Path data = Path.of("data");
    if (Files.exists(data)) {
      // Recursively delete contents
      Files.walk(data)
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignore) {
                }
              });
    }
  }
}
