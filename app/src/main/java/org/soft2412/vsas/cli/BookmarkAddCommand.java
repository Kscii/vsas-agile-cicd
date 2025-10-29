package org.soft2412.vsas.cli;

import java.util.Objects;
import java.util.Optional;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.BookmarkRepository;
import org.soft2412.vsas.repo.FileBookmarkRepository;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

public final class BookmarkAddCommand implements Command {
  private final SessionService sessions;
  private final BookmarkRepository bookmarks;
  private final ScrollRepository scrolls;

  public BookmarkAddCommand() {
    this(new SessionService(), new FileBookmarkRepository(), new FileScrollRepository());
  }

  BookmarkAddCommand(
      SessionService sessions, BookmarkRepository bookmarks, ScrollRepository scrolls) {
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.bookmarks = Objects.requireNonNull(bookmarks, "bookmarks");
    this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
  }

  @Override
  public int run(String[] args) {
    Optional<User> current = sessions.currentUser();
    if (current.isEmpty()) {
      System.err.println("Login required");
      return 1;
    }
    User user = current.get();
    String userIdKey = user.idKey();
    String scrollId = null;
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if ("--id".equals(arg)) {
          if (i + 1 >= (args.length)) {
            System.err.println("bookmark add: missing value for --id");
            return 2;
          }
          scrollId = args[++i];
        } else if (arg.startsWith("-")) {
          System.err.println("bookmark add: unknown option " + arg);
          return 2;
        } else {
          System.err.println("bookmark add: unexpected argument '" + arg + "'");
          return 2;
        }
      }
    }
    if (scrollId == null || scrollId.isBlank()) {
      System.err.println("bookmark add: missing required option --id");
      return 2;
    }

    if (bookmarks.exists(userIdKey, scrollId)) {
      System.out.println("Already bookmarked");
      return 0;
    }

    if (scrolls.findById(scrollId).isEmpty()) {
      System.err.println("bookmark add: unknown scroll id");
      return 1;
    }

    if (!bookmarks.add(userIdKey, scrollId)) {
      System.err.println("bookmark add: failed to persist bookmark");
      return 3;
    }

    System.out.println("Bookmarked " + scrollId);
    return 0;
  }

  @Override
  public String name() {
    return "bookmark add";
  }

  @Override
  public String description() {
    return "Add a bookmark for the specified scroll";
  }
}
