package org.soft2412.vsas.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;

public final class PreviewCommand implements Command {
  private final ScrollRepository scrolls = new FileScrollRepository();

  @Override
  public int run(String[] args) {
    try {
      ParseResult pr = parseOptions(args);
      if (pr.error != null) {
        System.err.println(pr.error);
        return 2; // usage
      }
      String id = pr.id;
      if (id == null || id.isBlank()) {
        System.err.println("preview: missing required option --id");
        return 2; // usage
      }

      Optional<Scroll> sOpt = scrolls.findById(id);
      if (sOpt.isEmpty()) {
        System.err.println("preview: unknown id");
        return 1; // validation
      }

      Scroll s = sOpt.get();
      System.out.println("id: " + s.id());
      System.out.println("name: " + s.name());
      System.out.println("uploader: " + s.uploaderIdKey());
      System.out.println("uploadDate: " + s.uploadDate());

      String filePath = s.filePath();
      if (filePath == null || filePath.isBlank()) {
        System.out.println("no preview available");
        return 0;
      }

      Path p = Path.of(filePath);
      if (!Files.exists(p)) {
        System.out.println("no preview available");
        return 0;
      }

      long size;
      try {
        size = Files.size(p);
      } catch (IOException e) {
        System.err.println("preview: I/O error");
        return 3; // IO
      }
      System.out.println("size: " + size + " bytes");

      byte[] buf = new byte[64];
      int read;
      try (var in = Files.newInputStream(p)) {
        read = in.read(buf);
      } catch (IOException e) {
        System.err.println("preview: I/O error");
        return 3; // IO
      }

      if (read <= 0) {
        System.out.println("no preview available");
        return 0;
      }

      // Determine if snippet is printable UTF-8 (tolerating a truncated trailing code point);
      // if so, render as text using only complete characters, otherwise as hex
      int textLen = printableUtf8PrefixLength(buf, read);
      if (textLen > 0) {
        // Safe to decode the complete prefix as UTF-8 and replace newlines with spaces
        String snippet =
            new String(buf, 0, textLen, StandardCharsets.UTF_8)
                .replace('\r', ' ')
                .replace('\n', ' ');
        System.out.println("text: " + snippet);
      } else {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < read; i++) {
          int b = buf[i] & 0xFF;
          if (i > 0) hex.append(' ');
          String hx = Integer.toHexString(b);
          if (hx.length() == 1) hex.append('0');
          hex.append(hx);
        }
        System.out.println("hex:  " + hex);
      }
      return 0;
    } catch (Exception e) {
      System.err.println("preview: I/O error");
      return 3;
    }
  }

  private boolean isPrintableUtf8Prefix(byte[] bytes, int len) {
    try {
      CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder();
      dec.onMalformedInput(CodingErrorAction.REPORT);
      dec.onUnmappableCharacter(CodingErrorAction.REPORT);
      CharBuffer cb = dec.decode(ByteBuffer.wrap(bytes, 0, len));
      for (int i = 0; i < cb.length(); i++) {
        char c = cb.get(i);
        // Allow common whitespace, disallow other control characters
        if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
          return false;
        }
      }
      return true;
    } catch (CharacterCodingException e) {
      return false;
    }
  }

  // Returns the length of the largest prefix that:
  // - ends on a complete UTF-8 code point boundary, and
  // - decodes to printable text (as per isPrintableUtf8Prefix)
  // Returns 0 if the prefix is not printable UTF-8.
  private int printableUtf8PrefixLength(byte[] bytes, int len) {
    if (bytes == null || len <= 0) return 0;
    int prefix = lastCompleteUtf8Prefix(bytes, len);
    if (prefix <= 0) return 0;
    return isPrintableUtf8Prefix(bytes, prefix) ? prefix : 0;
  }

  // Compute the length of the largest prefix that ends at a UTF-8 code point boundary.
  // If the buffer ends with a truncated UTF-8 sequence, drop the incomplete trailing bytes.
  private int lastCompleteUtf8Prefix(byte[] bytes, int len) {
    if (len <= 0) return 0;
    int i = len - 1;
    int min = Math.max(0, len - 4);
    // Move back over trailing continuation bytes (10xxxxxx)
    while (i >= min && (bytes[i] & 0xC0) == 0x80) {
      i--;
    }
    if (i < 0) {
      // All of the last up to 4 bytes are continuation; drop them
      // This means we ended mid-codepoint; return prefix excluding those trailing bytes
      int trailing = len - 1 - i; // equals len
      return len - trailing;
    }
    int start = i; // potential leading byte of the last code point
    int avail = len - start;
    int need = utf8CharLen(bytes[start] & 0xFF);
    if (need == 0) {
      // Invalid leading byte; do not attempt to trim — let decoder decide
      return len;
    }
    if (need > 1 && avail < need) {
      // Truncated final code point; cut it off
      return start;
    }
    // No truncation at the end
    return len;
  }

  // Determine the expected length of a UTF-8 sequence from its first byte.
  // Returns 0 if the byte cannot start a valid UTF-8 sequence.
  private int utf8CharLen(int b0) {
    if ((b0 & 0x80) == 0x00) return 1; // 0xxxxxxx
    if ((b0 & 0xE0) == 0xC0) return 2; // 110xxxxx
    if ((b0 & 0xF0) == 0xE0) return 3; // 1110xxxx
    if ((b0 & 0xF8) == 0xF0) return 4; // 11110xxx
    return 0; // continuation or invalid as leading
  }

  private ParseResult parseOptions(String[] args) {
    ParseResult r = new ParseResult();
    if (args == null) return r;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if ("--id".equals(a)) {
        if (i + 1 < args.length) {
          r.id = args[++i];
        } else {
          r.error = "preview: missing value for --id";
          return r;
        }
      } else if (a.startsWith("-")) {
        r.error = "preview: unknown option " + a;
        return r;
      } else {
        r.error = "preview: unexpected argument '" + a + "'";
        return r;
      }
    }
    return r;
  }

  private static final class ParseResult {
    String id;
    String error;
  }

  @Override
  public String name() {
    return "preview";
  }

  @Override
  public String description() {
    return "Preview a scroll";
  }
}
