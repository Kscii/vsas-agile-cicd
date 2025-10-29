package org.soft2412.vsas.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.BookmarkRepository;
import org.soft2412.vsas.repo.FileBookmarkRepository;
import org.soft2412.vsas.service.SessionService;

public final class BookmarkRemoveCommand implements Command {
  private final SessionService sessions;
  private final BookmarkRepository bookmarks;

  public BookmarkRemoveCommand() {
    this(new SessionService(), new FileBookmarkRepository());
  }

  BookmarkRemoveCommand(SessionService sessions, BookmarkRepository bookmarks) {
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.bookmarks = Objects.requireNonNull(bookmarks, "bookmarks");
  }

  @Override
  public int run(String[] args) {
    Optional<User> current = sessions.currentUser();
    if (current.isEmpty()) {
      System.err.println("Login required");
      return 1;
    }
    String scrollId = null;
    boolean yes = false;
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        switch (arg) {
          case "--id":
            if (i + 1 >= args.length) {
              System.err.println("bookmark remove: missing value for --id");
              return 2;
            }
            scrollId = args[++i];
            break;
          case "--yes":
            yes = true;
            break;
          default:
            System.err.println("bookmark remove: unknown option " + arg);
            return 2;
        }
      }
    }
    if (scrollId == null || scrollId.isBlank()) {
      System.err.println("bookmark remove: missing required option --id");
      return 2;
    }

    String userIdKey = current.get().idKey();
    if (!bookmarks.exists(userIdKey, scrollId)) {
      System.err.println("bookmark remove: not bookmarked");
      return 1;
    }

    if (!yes) {
      System.out.print("Remove bookmark " + scrollId + "? [y/N] ");
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      String line;
      try {
        line = reader.readLine();
      } catch (IOException e) {
        System.err.println("bookmark remove: I/O error");
        return 3;
      }
      String answer = line == null ? "" : line.trim().toLowerCase();
      if (!(answer.equals("y") || answer.equals("yes"))) {
        System.out.println("Aborted.");
        return 0;
      }
    }

    if (!bookmarks.remove(userIdKey, scrollId)) {
      System.err.println("bookmark remove: failed to persist bookmark removal");
      return 3;
    }

    System.out.println("Removed bookmark " + scrollId);
    return 0;
  }

  @Override
  public String name() {
    return "bookmark remove";
  }

  @Override
  public String description() {
    return "Remove an existing bookmark";
  }
}
