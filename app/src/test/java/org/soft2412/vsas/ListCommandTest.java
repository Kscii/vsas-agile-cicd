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

public class ListCommandTest {
  private final PrintStream originalOut = System.out;
  private ByteArrayOutputStream out;

  @BeforeEach
  public void setup() throws IOException {
    out = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out));
    cleanupDataDir();
  }

  @AfterEach
  public void teardown() throws IOException {
    System.setOut(originalOut);
    cleanupDataDir();
  }

  @Test
  public void listShowsEmptyMessageWhenNoData() {
    int code = new CommandDispatcher().dispatch(new String[] {"list"});
    assertEquals(0, code);
    assertTrue(out.toString().contains("no scrolls"));
  }

  @Test
  public void listShowsHeaderAndEntryWhenDataExists() throws IOException {
    // Prepare one TSV row
    Path dataFile = Path.of("data", "scrolls.tsv");
    Files.createDirectories(dataFile.getParent());
    String row =
        String.join("\t", "s10", "Title", "u-1", "2025-01-01T00:00:00Z", "data/files/s10.bin", "0")
            + System.lineSeparator();
    Files.writeString(dataFile, row, StandardCharsets.UTF_8);

    int code = new CommandDispatcher().dispatch(new String[] {"list"});
    assertEquals(0, code);
    String outStr = out.toString();
    assertTrue(outStr.contains("id | name | uploader | uploadDate"));
    assertTrue(outStr.contains("s10 | Title | u-1 | 2025-01-01T00:00:00Z"));
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
