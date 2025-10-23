package org.soft2412.vsas.cli;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;

public final class ListCommand implements Command {
  private final ScrollRepository scrolls = new FileScrollRepository();

  // Accept yyyy-MM-dd only for --from/--to
  private static final DateTimeFormatter FROM_TO_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

  @Override
  public int run(String[] args) {
    // ---- Parse flags (no behavior change yet; filters will be applied in next
    // commit) ----
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
                return 1;
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
                return 1;
              }
            }
            break;
          default:
            // ignore unknowns for forward-compat
        }
      }
    }

    // ---- Load all (filters will be applied in next commit) ----
    List<Scroll> all = scrolls.findAll();
    if (all.isEmpty()) {
      // Keep legacy output for now to stay green with existing tests
      System.out.println("no scrolls");
      return 0;
    }

    System.out.println("id | name | uploader | uploadDate");
    for (Scroll s : all) {
      System.out.println(
          s.id() + " | " + s.name() + " | " + s.uploaderIdKey() + " | " + s.uploadDate());
    }
    return 0;
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
