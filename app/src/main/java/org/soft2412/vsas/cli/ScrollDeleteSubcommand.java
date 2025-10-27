package org.soft2412.vsas.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

public final class ScrollDeleteSubcommand {

  public int run(String[] args) {
    String[] safeArgs = args == null ? new String[0] : args;

    SessionService session = new SessionService();
    Optional<User> currentUser = session.currentUser();
    if (currentUser.isEmpty()) {
      System.err.println("Forbidden: please login first.");
      return 1;
    }
    String requesterIdKey = currentUser.get().idKey();

    String id = null;
    boolean yes = false;

    for (int i = 0; i < safeArgs.length; i++) {
      String a = safeArgs[i];
      switch (a) {
        case "--id":
          if (i + 1 >= safeArgs.length) {
            System.err.println("Missing value for --id");
            return 2;
          }
          id = safeArgs[++i].trim();
          break;
        case "--yes":
          yes = true;
          break;
        default:
          System.err.println("Unknown option: " + a);
          return 2;
      }
    }

    if (id == null || id.isEmpty()) {
      System.err.println("Usage: scroll delete --id <sid> [--yes]");
      return 2;
    }

    ScrollRepository repo = new FileScrollRepository();
    Optional<Scroll> os = repo.findById(id);
    if (os.isEmpty()) {
      System.err.println("Not found: " + id);
      return 1;
    }

    Scroll s = os.get();
    if (!s.uploaderIdKey().equals(requesterIdKey)) {
      System.err.println("Forbidden: only the uploader can delete this scroll.");
      return 1;
    }

    if (!yes) {
      System.out.print("Delete scroll " + id + "? This cannot be undone. [y/n] ");
      BufferedReader br =
          new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      String line;
      try {
        line = br.readLine();
      } catch (IOException e) {
        System.err.println("I/O error: " + e.getMessage());
        return 3;
      }
      String ans = line == null ? "" : line.trim().toLowerCase();
      if (!(ans.equals("y") || ans.equals("yes"))) {
        System.out.println("Aborted.");
        return 0;
      }
    }

    boolean ok = repo.deleteById(id);
    if (!ok) {
      System.err.println("Delete failed (persistence).");
      return 3;
    }

    System.out.println("Deleted: " + id);
    return 0;
  }
}
