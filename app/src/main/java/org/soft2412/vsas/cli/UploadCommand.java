package org.soft2412.vsas.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

public final class UploadCommand implements Command {
  private final SessionService sessions = new SessionService();
  private final ScrollRepository scrolls = new FileScrollRepository();

  @Override
  public int run(String[] args) {
    Map<String, String> opts = parseOptions(args);

    Optional<User> userOpt = sessions.currentUser();
    if (userOpt.isEmpty()) {
      System.err.println("upload: login required");
      return 1;
    }
    User user = userOpt.get();

    String id = opts.get("id");
    String name = opts.get("name");
    String file = opts.get("file");

    if (id == null
        || id.isBlank()
        || name == null
        || name.isBlank()
        || file == null
        || file.isBlank()) {
      System.err.println("upload: missing required options --id, --name, --file");
      return 2;
    }

    Path src = Path.of(file);
    if (!Files.exists(src)) {
      System.err.println("upload: file not found");
      return 1;
    }

    if (scrolls.existsId(id)) {
      System.err.println("upload: id already exists");
      return 1;
    }

    try {
      // Ensure target dirs
      Path filesDir = Path.of("data", "files");
      Files.createDirectories(filesDir);
      Path dest = filesDir.resolve(id + ".bin");
      Files.copy(src, dest);

      // Save metadata
      String uploadDate =
          DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
      Scroll scroll = new Scroll(id, name, user.idKey(), uploadDate, dest.toString(), 1L, 0L);
      if (!scrolls.save(scroll)) {
        System.err.println("upload: failed to save metadata");
        return 3;
      }

      System.out.println("upload: success");
      return 0;
    } catch (Exception e) {
      System.err.println("upload: unexpected error");
      return 3;
    }
  }

  private Map<String, String> parseOptions(String[] args) {
    Map<String, String> m = new HashMap<>();
    if (args == null) return m;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if ("--id".equals(a) && i + 1 < args.length) {
        m.put("id", args[++i]);
      } else if ("--name".equals(a) && i + 1 < args.length) {
        m.put("name", args[++i]);
      } else if ("--file".equals(a) && i + 1 < args.length) {
        m.put("file", args[++i]);
      }
    }
    return m;
  }

  @Override
  public String name() {
    return "upload";
  }

  @Override
  public String description() {
    return "Upload a scroll";
  }
}
