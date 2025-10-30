package org.soft2412.vsas.cli;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.soft2412.vsas.model.Bookmark;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.BookmarkRepository;
import org.soft2412.vsas.repo.FileBookmarkRepository;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

public final class ListCommand implements Command {
  private final ScrollRepository scrolls = new FileScrollRepository();
  private final SessionService sessions = new SessionService();
  private final BookmarkRepository bookmarks = new FileBookmarkRepository();

  private static final DateTimeFormatter FROM_TO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

  // Fixed column widths for stable output
  private static final int W_ID = 12;
  private static final int W_NAME = 30;
  private static final int W_UPLOADER = 14;
  private static final int W_DATE = 20;

  private static final String FIXED_HEADER_FMT =
      "%-" + W_ID + "s  %-" + W_NAME + "s  %-" + W_UPLOADER + "s  %-" + W_DATE + "s";
  private static final String FIXED_ROW_FMT = FIXED_HEADER_FMT;

  @Override
  public int run(String[] args) {
    // ---- Parse flags ----
    String uploaderId = null;
    String scrollId = null;
    String nameKw = null;
    LocalDate fromDate = null;
    LocalDate toDate = null;

    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        String a = args[i];
        switch (a) {
          case "--uploader-id":
            if (i + 1 < args.length) uploaderId = args[++i];
            break;
          case "--scroll-id":
            if (i + 1 < args.length) scrollId = args[++i];
            break;
          case "--name":
            if (i + 1 < args.length) nameKw = args[++i];
            break;
          case "--from":
            if (i + 1 < args.length) {
              String v = args[++i];
              try {
                fromDate = LocalDate.parse(v, FROM_TO_FMT);
              } catch (DateTimeParseException ex) {
                System.err.println("list: invalid date for --from (expected yyyy-MM-dd)");
                return 2;
              }
            }
            break;
          case "--to":
            if (i + 1 < args.length) {
              String v = args[++i];
              try {
                toDate = LocalDate.parse(v, FROM_TO_FMT);
              } catch (DateTimeParseException ex) {
                System.err.println("list: invalid date for --to (expected yyyy-MM-dd)");
                return 2;
              }
            }
            break;
          default:
            // ignore unknowns for forward-compat
        }
      }
    }

    // ---- Load all ----
    List<Scroll> all = scrolls.findAll();
    if (all.isEmpty()) {
      System.out.println("no scrolls");
      return 0;
    }

    // ---- Prepare finals for lambdas ----
    final String uploaderIdF = uploaderId;
    final String scrollIdF = scrollId;
    final String nameNormF = nameKw == null ? null : nameKw.toLowerCase(Locale.ROOT);
    final LocalDate fromDateF = fromDate;
    final LocalDate toDateF = toDate;

    // ---- Apply AND filters (inclusive date range) ----
    List<Scroll> filtered =
        all.stream()
            .filter(s -> uploaderIdF == null || uploaderIdF.equals(s.uploaderIdKey()))
            .filter(s -> scrollIdF == null || scrollIdF.equals(s.id()))
            .filter(
                s -> {
                  if (nameNormF == null) return true;
                  String n = s.name();
                  return n != null && n.toLowerCase(Locale.ROOT).contains(nameNormF);
                })
            .filter(
                s -> {
                  if (fromDateF == null && toDateF == null) return true;
                  LocalDate d = parseUploadDateToLocalDate(s.uploadDate());
                  if (d == null) return false;
                  if (fromDateF != null && d.isBefore(fromDateF)) return false;
                  if (toDateF != null && d.isAfter(toDateF)) return false;
                  return true;
                })
            .collect(Collectors.toList());

    if (filtered.isEmpty()) {
      System.out.println("No scrolls.");
      return 0;
    }

    // ---- Fixed-width table for stable scripting-friendly output ----
    Set<String> bookmarkedIds = bookmarkedScrollIds();
    System.out.println(formatFixedHeader());
    for (Scroll s : filtered) {
      System.out.println(formatFixedRow(s, bookmarkedIds.contains(s.id())));
    }

    return 0;
  }

  private static String formatFixedHeader() {
    return String.format(FIXED_HEADER_FMT, "id", "name", "uploader", "uploadDate");
  }

  private String formatFixedRow(Scroll s, boolean bookmarked) {
    return String.format(
        FIXED_ROW_FMT,
        cut(s.id(), W_ID),
        formatName(s.name(), bookmarked),
        cut(s.uploaderIdKey(), W_UPLOADER),
        cut(s.uploadDate(), W_DATE));
  }

  private String formatName(String originalName, boolean bookmarked) {
    if (!bookmarked) {
      return cut(originalName, W_NAME);
    }
    String marker = "[BK]";
    String base = originalName == null ? "" : originalName.trim();
    if (base.isEmpty()) {
      return cut(marker, W_NAME);
    }
    int allowance = W_NAME - marker.length() - 1;
    if (allowance <= 0) {
      return cut(marker, W_NAME);
    }
    String prefix = base.length() > allowance ? base.substring(0, allowance) : base;
    return cut(prefix + " " + marker, W_NAME);
  }

  private static String cut(String v, int width) {
    String x = v == null ? "" : v;
    if (x.length() <= width) return x;
    return x.substring(0, width);
  }

  private static LocalDate parseUploadDateToLocalDate(String uploadDate) {
    if (uploadDate == null || uploadDate.isBlank()) return null;
    try {
      Instant ins = Instant.parse(uploadDate);
      return ins.atOffset(ZoneOffset.UTC).toLocalDate();
    } catch (Exception ignore) {
      return null;
    }
  }

  private Set<String> bookmarkedScrollIds() {
    return sessions
        .currentUser()
        .map(User::idKey)
        .filter(id -> id != null && !id.isBlank())
        .map(bookmarks::listByUser)
        .map(
            list ->
                list.stream()
                    .map(Bookmark::scrollId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toSet()))
        .orElseGet(java.util.Collections::emptySet);
  }

  @Override
  public String name() {
    return "list";
  }

  @Override
  public String description() {
    return "List scrolls";
  }
}
