package org.soft2412.vsas.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PreviewCommandTest {

  private Path dataDir;
  private Path filesDir;
  private Path scrollsTsv;

  @BeforeEach
  void setup() throws Exception {
    dataDir = Path.of("data");
    filesDir = dataDir.resolve("files");
    scrollsTsv = dataDir.resolve("scrolls.tsv");
    // clean slate
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
    Files.createDirectories(filesDir);
  }

  @AfterEach
  void cleanup() throws Exception {
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

  private static void writeScrollRow(
      Path tsv, String id, String name, String uploader, String uploadDate, String filePath, long dl)
      throws Exception {
    String line =
        String.join(
                "\t",
                id,
                name == null ? "" : name,
                uploader,
                uploadDate,
                filePath == null ? "" : filePath,
                Long.toString(dl))
            + System.lineSeparator();
    Files.writeString(tsv, line, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
  }

  @Test
  void preview_knownId_printsMetadataAndSnippet_exit0() throws Exception {
    // Prepare data file and content
    String id = "s1";
    Path f = filesDir.resolve(id + ".bin");
    byte[] content = new byte[] {0x48, 0x65, 0x6c, 0x6c, 0x6f, '\n', 0x57, 0x6f, 0x72, 0x6c, 0x64, 0x00};
    Files.write(f, content);
    writeScrollRow(
        scrollsTsv,
        id,
        "Greeting",
        "U-1",
        Instant.parse("2025-01-01T00:00:00Z").toString(),
        f.toString(),
        0L);

    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    try {
      ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
      ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
      System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

      int code = new PreviewCommand().run(new String[] {"--id", id});

      assertEquals(0, code);
      String out = outBuf.toString(StandardCharsets.UTF_8);
      assertTrue(out.contains("id: " + id));
      assertTrue(out.contains("name: Greeting"));
      assertTrue(out.contains("uploader: U-1"));
      assertTrue(out.toLowerCase().contains("size: "));
      assertTrue(out.contains("text: "));
      assertTrue(out.contains("hex:  "));
      // Verify a couple of expected hex bytes
      assertTrue(out.contains("48") || out.contains("48 65"));
      assertEquals("", errBuf.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }

  @Test
  void preview_unknownId_printsError_exit1() throws Exception {
    // write a different id
    writeScrollRow(
        scrollsTsv,
        "other",
        "X",
        "U-9",
        Instant.parse("2025-01-01T00:00:00Z").toString(),
        "",
        0L);

    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    try {
      ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
      ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
      System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

      int code = new PreviewCommand().run(new String[] {"--id", "missing"});

      assertEquals(1, code);
      assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("unknown id"));
      assertEquals("", outBuf.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }

  @Test
  void preview_missingId_returnsUsageExit2() throws Exception {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    try {
      ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
      ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
      System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

      int code = new PreviewCommand().run(new String[] {});

      assertEquals(2, code);
      String err = errBuf.toString(StandardCharsets.UTF_8).toLowerCase();
      assertTrue(err.contains("missing required option --id"));
      assertEquals("", outBuf.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }

  @Test
  void preview_noFile_printsNoPreviewAvailable_exit0() throws Exception {
    writeScrollRow(
        scrollsTsv,
        "s2",
        "NoFile",
        "U-2",
        Instant.parse("2025-01-02T00:00:00Z").toString(),
        "",
        0L);

    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    try {
      ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
      ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
      System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

      int code = new PreviewCommand().run(new String[] {"--id", "s2"});

      assertEquals(0, code);
      String out = outBuf.toString(StandardCharsets.UTF_8);
      assertTrue(out.contains("no preview available"));
      assertEquals("", errBuf.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }

  @Test
  void preview_usageErrors_return2() throws Exception {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    try {
      ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
      ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
      System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));

      int c1 = new PreviewCommand().run(new String[] {"--id"});
      assertEquals(2, c1);
      assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("missing value"));

      errBuf.reset();
      outBuf.reset();
      int c2 = new PreviewCommand().run(new String[] {"--foo"});
      assertEquals(2, c2);
      assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("unknown option"));

      errBuf.reset();
      outBuf.reset();
      int c3 = new PreviewCommand().run(new String[] {"extra"});
      assertEquals(2, c3);
      assertTrue(errBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("unexpected argument"));
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
    }
  }
}
