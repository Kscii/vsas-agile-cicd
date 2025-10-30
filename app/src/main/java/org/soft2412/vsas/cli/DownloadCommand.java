package org.soft2412.vsas.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

public final class DownloadCommand implements Command {
  private final SessionService sessions = new SessionService();
  private final ScrollRepository scrolls = new FileScrollRepository();

  @Override
  public int run(String[] args) {
    Map<String, String> opts = parseOptions(args);
    Optional<User> userOpt = sessions.currentUser();
    if (userOpt.isEmpty()) {
      System.err.println("Login required");
      return 1;
    }
    String id = opts.get("id");
    String outDirOpt = opts.get("out");

    if (id == null || id.isBlank()) {
      System.err.println("download: missing required option --id");
      return 2;
    }

    Optional<Scroll> sOpt = scrolls.findById(id);
    if (sOpt.isEmpty()) {
      System.err.println("Error: scroll not found");
      return 3;
    }
    Scroll s = sOpt.get();
    Path source = Path.of(s.filePath());
    if (!Files.exists(source)) {
      System.err.println("Error: source file not found");
      return 3;
    }

    Path outDir;
    try {
      if (outDirOpt == null) {
        if (!confirm("Use current directory? [y/N] ")) {
          System.err.println("Aborted");
          return 0;
        }
        outDir = Path.of(".");
      } else {
        outDir = Path.of(outDirOpt);
      }

      if (!Files.exists(outDir) || !Files.isDirectory(outDir)) {
        System.err.println("Error: output directory not found");
        return 3;
      }

      String fileName = source.getFileName().toString();
      Path dest = outDir.resolve(fileName);

      if (Files.exists(dest)) {
        if (!confirm("Replace existing file? [y/N] ")) {
          System.err.println("Aborted");
          return 0;
        }
        try {
          Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ioe) {
          System.err.println("Error: cannot write destination");
          return 3;
        }
      } else {
        try {
          Files.copy(source, dest);
        } catch (FileAlreadyExistsException ignore) {
          System.err.println("Error: destination exists");
          return 3;
        } catch (IOException ioe) {
          System.err.println("Error: cannot write destination");
          return 3;
        }
      }

      boolean ok = scrolls.incrementDownloadCount(id);
      if (!ok) {
        System.err.println("Warning: failed to update download count for scroll " + id);
      }

      System.out.println(dest.toAbsolutePath().normalize().toString());
      return 0;
    } catch (Exception e) {
      System.err.println("Error: unexpected failure");
      return 3;
    }
  }

  private static boolean confirm(String prompt) throws IOException {
    System.out.print(prompt);
    System.out.flush();
    BufferedReader br =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String line = br.readLine();
    if (line == null) return false;
    String ans = line.trim().toLowerCase();
    return ans.equals("y") || ans.equals("yes");
  }

  private Map<String, String> parseOptions(String[] args) {
    Map<String, String> m = new HashMap<>();
    if (args == null) return m;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if ("--id".equals(a) && i + 1 < args.length) {
        m.put("id", args[++i]);
      } else if ("--out".equals(a) && i + 1 < args.length) {
        m.put("out", args[++i]);
      }
    }
    return m;
  }

  @Override
  public String name() {
    return "download";
  }

  @Override
  public String description() {
    return "Download a scroll";
  }
}
