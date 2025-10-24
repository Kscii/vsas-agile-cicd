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

/**
 * Tests for list filters, invalid dates, empty result message, and fixed-width table formatting.
 */
public class ListCommandFiltersAndFormattingTest {

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private ByteArrayOutputStream outBuf;
  private ByteArrayOutputStream errBuf;

  private Path dataFile;
  private static final String TABLE_FMT = "%-12s  %-30s  %-14s  %-20s";

  @BeforeEach
  public void setup() throws IOException {
    outBuf = new ByteArrayOutputStream();
    errBuf = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

    dataFile = Path.of("data", "scrolls.tsv");
    Files.createDirectories(dataFile.getParent());

    // Prepare three rows (no header). Order: id, name, uploaderIdKey, uploadDate,
    // filePath, downloadCount
    String r1 =
        String.join(
                "\t", "s10", "Title One", "u-1", "2025-01-01T00:00:00Z", "data/files/s10.bin", "0")
            + System.lineSeparator();
    String r2 =
        String.join(
                "\t",
                "s11",
                "Quarterly Report",
                "u-2",
                "2025-06-15T12:00:00Z",
                "data/files/s11.bin",
                "5")
            + System.lineSeparator();
    // Long values to test hard cutting/width stability
    String longName = "ThisIsAVeryLongScrollNameThatExceedsThirtyCharacters";
    String longUploader = "uploader-key-exceeds";
    String r3 =
        String.join(
                "\t",
                "s12",
                longName,
                longUploader,
                "2025-12-31T23:59:59Z",
                "data/files/s12.bin",
                "7")
            + System.lineSeparator();

    Files.writeString(dataFile, r1 + r2 + r3, StandardCharsets.UTF_8);
  }

  @AfterEach
  public void teardown() throws IOException {
    System.setOut(originalOut);
    System.setErr(originalErr);

    if (Files.exists(dataFile)) {
      Files.deleteIfExists(dataFile);
    }
    Path dataDir = Path.of("data");
    if (Files.exists(dataDir)) {
      // best-effort clean-up
      Files.walk(dataDir)
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

  @Test
  public void filters_andSemantics_inclusiveDates() {
    // uploader-id + name kw + from/to (inclusive) should yield s10 only
    int code =
        new CommandDispatcher()
            .dispatch(
                new String[] {
                  "list",
                  "--uploader-id",
                  "u-1",
                  "--name",
                  "title",
                  "--from",
                  "2025-01-01",
                  "--to",
                  "2025-12-31"
                });
    assertEquals(0, code);

    String out = outBuf.toString(StandardCharsets.UTF_8);
    String expectedHeader = String.format(TABLE_FMT, "id", "name", "uploader", "uploadDate");
    String expectedRow =
        String.format(TABLE_FMT, "s10", "Title One", "u-1", "2025-01-01T00:00:00Z");
    assertTrue(out.contains(expectedHeader));
    assertTrue(out.contains(expectedRow));
    assertFalse(out.contains("s11"));
    assertFalse(out.contains("s12"));
    assertEquals("", errBuf.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void invalidDate_forFrom_returnsExit1_andMessage() {
    int code = new CommandDispatcher().dispatch(new String[] {"list", "--from", "2025/01/01"});
    assertEquals(1, code);
    String err = errBuf.toString(StandardCharsets.UTF_8);
    assertTrue(err.contains("list: invalid date for --from (expected yyyy-MM-dd)"));
  }

  @Test
  public void emptyAfterFiltering_printsCapitalisedNoScrolls() {
    int code =
        new CommandDispatcher().dispatch(new String[] {"list", "--uploader-id", "__no_such__"});
    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("No scrolls."));
  }

  @Test
  public void fixedWidth_table_isStable_andHardCuts() {
    // No filters: expect fixed-width header followed by rows
    int code = new CommandDispatcher().dispatch(new String[] {"list"});
    assertEquals(0, code);
    String out = outBuf.toString(StandardCharsets.UTF_8);

    // Find the fixed-width row for s12 (which has long fields)
    String[] lines = out.split("\\R");
    String row = null;
    for (String ln : lines) {
      if (ln.startsWith("s12")) {
        row = ln;
        break;
      }
    }
    assertNotNull(row, "expected a row starting with s12");

    // Expect exact fixed width length: W_ID(12)+2 + W_NAME(30)+2 + W_UPLOADER(14)+2
    // + W_DATE(20) = 82
    int expectedLen = 12 + 2 + 30 + 2 + 14 + 2 + 20;
    assertEquals(expectedLen, row.length(), "fixed-width row length should be stable");

    // Check columns by substring and trimming
    String c1 = row.substring(0, 12).trim(); // id
    String c2 = row.substring(14, 14 + 30).trim(); // name
    String c3 = row.substring(46, 46 + 14).trim(); // uploader
    String c4 = row.substring(62, 62 + 20).trim(); // uploadDate

    assertEquals("s12", c1);
    // name was long; verify it starts with original and is cut, and not empty
    assertTrue(
        c2.startsWith("ThisIsAVeryLongScrollNameThat"), "name should be hard-cut to 30 width");
    assertTrue(c3.startsWith("uploader-key"), "uploader should be hard-cut to 14 width");
    assertEquals("2025-12-31T23:59:59Z", c4);
  }
}
