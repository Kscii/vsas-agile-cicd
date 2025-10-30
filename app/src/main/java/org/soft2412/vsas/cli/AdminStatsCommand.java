package org.soft2412.vsas.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.ScrollUsage;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.service.SessionService;

public final class AdminStatsCommand implements Command {

  private final PrintStream out;
  private final PrintStream err;
  private final SessionService sessions;
  private final FileScrollRepository scrolls;

  public AdminStatsCommand() {
    this(System.out, System.err, new SessionService(), new FileScrollRepository());
  }

  public AdminStatsCommand(
      PrintStream out, PrintStream err, SessionService sessions, FileScrollRepository scrolls) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
  }

  @Override
  public int run(String[] args) {
    Optional<User> current = sessions.currentUser();
    if (current.isEmpty() || !"ADMIN".equalsIgnoreCase(nvl(current.get().role()))) {
      err.println("Permission denied: admin only.");
      return 1;
    }

    boolean byUploader = false;
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        if ("--by".equals(args[i]) && i + 1 < args.length && "uploader".equals(args[i + 1])) {
          byUploader = true;
          break;
        }
        if ("--by=uploader".equals(args[i])) {
          byUploader = true;
          break;
        }
      }
    }

    if (byUploader) {
      return printByUploader();
    } else {
      return printPerScroll();
    }
  }

  private int printPerScroll() {
    List<Scroll> all = scrolls.findAll();
    if (all.isEmpty()) {
      out.println("No data");
      return 0;
    }

    out.printf("%-10s %-28s %-16s %10s %12s%n", "ID", "NAME", "UPLOADER", "UPLOADS", "DOWNLOADS");
    out.println("------------------------------------------------------------------");
    for (Scroll s : all) {
      out.printf(
          "%-10s %-28s %-16s %10d %12d%n",
          nvl(s.id()),
          shorten(nvl(s.name()), 28),
          nvl(s.uploaderIdKey()),
          s.uploadCount(),
          s.downloadCount());
    }
    return 0;
  }

  private int printByUploader() {
    Map<String, ScrollUsage> agg = scrolls.aggregateByUploader();
    if (agg.isEmpty()) {
      out.println("No data");
      return 0;
    }

    out.printf("%-16s %10s %12s%n", "UPLOADER", "UPLOADS", "DOWNLOADS");
    out.println("----------------------------------------------");
    for (ScrollUsage su : agg.values()) {
      out.printf(
          "%-16s %10d %12d%n", nvl(su.getUploaderIdKey()), su.getUploads(), su.getDownloads());
    }
    return 0;
  }

  @Override
  public String name() {
    return "admin stats";
  }

  @Override
  public String description() {
    return "Show scroll usage stats (admin only). Default per scroll; use --by uploader to group.";
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }

  private static String shorten(String s, int max) {
    if (s == null) return "";
    if (s.length() <= max) return s;
    if (max <= 3) return s.substring(0, max);
    return s.substring(0, max - 3) + "...";
  }
}
