package org.soft2412.vsas.cli;

import java.io.IOException;
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

      StringBuilder text = new StringBuilder();
      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < read; i++) {
        int b = buf[i] & 0xFF;
        if (b >= 32 && b < 127) {
          char c = (char) b;
          if (c == '\r' || c == '\n') {
            text.append(' ');
          } else {
            text.append(c);
          }
        } else {
          text.append('.');
        }
        if (i > 0) hex.append(' ');
        String hx = Integer.toHexString(b);
        if (hx.length() == 1) hex.append('0');
        hex.append(hx);
      }

      System.out.println("text: " + text);
      System.out.println("hex:  " + hex);
      return 0;
    } catch (Exception e) {
      System.err.println("preview: I/O error");
      return 3;
    }
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
