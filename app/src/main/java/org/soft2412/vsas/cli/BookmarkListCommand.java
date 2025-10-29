package org.soft2412.vsas.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.soft2412.vsas.model.Bookmark;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.BookmarkRepository;
import org.soft2412.vsas.repo.FileBookmarkRepository;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

public final class BookmarkListCommand implements Command {
  private static final int W_ID = 12;
  private static final int W_NAME = 30;
  private static final int W_UPLOADER = 14;
  private static final int W_ADDED = 25;
  private static final String HEADER_FMT =
      "%-" + W_ID + "s  %-" + W_NAME + "s  %-" + W_UPLOADER + "s  %-" + W_ADDED + "s";

  private final SessionService sessions;
  private final BookmarkRepository bookmarks;
  private final ScrollRepository scrolls;

  public BookmarkListCommand() {
    this(new SessionService(), new FileBookmarkRepository(), new FileScrollRepository());
  }

  BookmarkListCommand(
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
    if (args != null && args.length > 0) {
      System.err.println("bookmark list: unexpected arguments");
      return 2;
    }

    List<Bookmark> list = bookmarks.listByUser(current.get().idKey());
    if (list.isEmpty()) {
      System.out.println("No bookmarks.");
      return 0;
    }

    System.out.println(String.format(HEADER_FMT, "sid", "name", "uploaderIdKey", "addedAt"));
    for (Bookmark bookmark : list) {
      String sid = bookmark.scrollId();
      Scroll scroll = scrolls.findById(sid).orElse(null);
      String name = scroll == null ? "<missing>" : scroll.name();
      String uploader = scroll == null ? "" : scroll.uploaderIdKey();
      String displayName = formatNameWithMarker(name == null ? "" : name);
      System.out.println(
          String.format(
              HEADER_FMT,
              cut(sid, W_ID),
              cut(displayName, W_NAME),
              cut(uploader, W_UPLOADER),
              cut(bookmark.addedAt().toString(), W_ADDED)));
    }

    return 0;
  }

  private String formatNameWithMarker(String base) {
    String safe = base == null ? "" : base.trim();
    String marker = "[BK]";
    if (safe.isEmpty()) {
      return marker;
    }
    int allowed = W_NAME - marker.length() - 1;
    if (allowed <= 0) {
      return marker;
    }
    String prefix = safe.length() > allowed ? safe.substring(0, allowed) : safe;
    return prefix + " " + marker;
  }

  private String cut(String value, int width) {
    String safe = value == null ? "" : value;
    if (safe.length() <= width) {
      return safe;
    }
    return safe.substring(0, width);
  }

  @Override
  public String name() {
    return "bookmark list";
  }

  @Override
  public String description() {
    return "List bookmarks for the current session";
  }
}
