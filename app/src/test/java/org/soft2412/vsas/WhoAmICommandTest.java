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

public class WhoAmICommandTest {
  private final PrintStream originalOut = System.out;
  private ByteArrayOutputStream out;

  @BeforeEach
  public void setup() throws IOException {
    out = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    cleanupDataDir();
    Files.createDirectories(Path.of("data"));
  }

  @AfterEach
  public void teardown() throws IOException {
    System.setOut(originalOut);
    cleanupDataDir();
  }

  @Test
  public void printsGuestWhenNoSession() {
    int code = new CommandDispatcher().dispatch(new String[] {"whoami"});
    assertEquals(0, code);
    assertTrue(out.toString().contains("guest"));
  }

  @Test
  public void printsUsernameWhenLoggedIn() throws IOException {
    writeSession("alice", "u-1", "admin");
    int code = new CommandDispatcher().dispatch(new String[] {"whoami"});
    assertEquals(0, code);
    assertTrue(out.toString().contains("alice (role=admin)"));
  }

  private void writeSession(String username, String idKey, String role) throws IOException {
    Path data = Path.of("data");
    Files.createDirectories(data);
    String json =
        "{\"username\":\"" + username + "\",\"idKey\":\"" + idKey + "\",\"role\":\"" + role + "\"}";
    Files.writeString(data.resolve("session.json"), json, StandardCharsets.UTF_8);
  }

  private void cleanupDataDir() throws IOException {
    Path data = Path.of("data");
    if (Files.exists(data)) {
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
