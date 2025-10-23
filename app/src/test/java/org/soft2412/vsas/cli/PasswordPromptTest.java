package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class PasswordPromptTest {

  @AfterEach
  void tearDown() {
    PasswordPrompt.setTestProvider(null);
  }

  @Test
  void read_usesTestProvider_andPrintsPrompt() throws Exception {
    ByteArrayOutputStream outBuf = new ByteArrayOutputStream();

    PasswordPrompt.setTestProvider(
        (out, prompt) -> {
          if (out != null) {
            out.print(prompt);
            out.flush();
          }
          return "S3cr3t!".toCharArray();
        });

    char[] got =
        PasswordPrompt.read(new PrintStream(outBuf, true, StandardCharsets.UTF_8), "Password: ");
    assertNotNull(got);
    assertEquals("S3cr3t!", new String(got));
    assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Password:"));
  }
}
