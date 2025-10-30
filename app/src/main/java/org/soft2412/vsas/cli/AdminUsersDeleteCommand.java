package org.soft2412.vsas.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.service.SessionService;

public final class AdminUsersDeleteCommand implements Command {

  private final PrintStream out;
  private final PrintStream err;
  private final SessionService sessions;
  private final UserRepository users;
  private final ScrollRepository scrolls;

  public AdminUsersDeleteCommand() {
    this(
        System.out,
        System.err,
        new SessionService(),
        new FileUserRepository(),
        new FileScrollRepository());
  }

  AdminUsersDeleteCommand(
      PrintStream out,
      PrintStream err,
      SessionService sessions,
      UserRepository users,
      ScrollRepository scrolls) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.users = Objects.requireNonNull(users, "users");
    this.scrolls = Objects.requireNonNull(scrolls, "scrolls");
  }

  @Override
  public int run(String[] args) {
    String[] safeArgs = args == null ? new String[0] : args;
    String username = null;
    boolean yes = false;
    boolean usernameFromFlag = false;

    for (int i = 0; i < safeArgs.length; i++) {
      String arg = safeArgs[i];
      switch (arg) {
        case "--username":
          if (i + 1 >= safeArgs.length) {
            err.println("Missing value for --username");
            return 2;
          }
          username = safeArgs[++i].trim();
          usernameFromFlag = true;
          break;
        case "--yes":
          yes = true;
          break;
        default:
          err.println("Unknown option: " + arg);
          return 2;
      }
    }

    Optional<User> currentOpt = sessions.currentUser();
    if (currentOpt.isEmpty()) {
      err.println("Forbidden: admin login required.");
      return 1;
    }
    User current = currentOpt.get();
    if (!isAdmin(current.role())) {
      err.println("Forbidden: admin role required.");
      return 1;
    }

    BufferedReader reader = null;
    if (username == null || username.isBlank()) {
      out.print("Username: ");
      out.flush();
      try {
        reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line = reader.readLine();
        if (line != null) {
          username = line.trim();
        }
      } catch (IOException e) {
        err.println("I/O error: " + e.getMessage());
        return 3;
      }
    }

    if (username == null || username.isBlank()) {
      err.println("Usage: admin users delete --username <u> [--yes]");
      return 2;
    }

    if (current.username() != null && current.username().equalsIgnoreCase(username)) {
      err.println("Error: administrators cannot delete their own session account.");
      return 1;
    }

    Optional<User> targetOpt = users.findByUsername(username);
    if (targetOpt.isEmpty()) {
      err.println("Error: user not found: " + username);
      return 1;
    }
    User target = targetOpt.get();

    if (current.idKey() != null
        && !current.idKey().isBlank()
        && current.idKey().equalsIgnoreCase(nullToEmpty(target.idKey()))) {
      err.println("Error: administrators cannot delete their own session account.");
      return 1;
    }

    String targetIdKey = nullToEmpty(target.idKey());
    if (!targetIdKey.isEmpty()) {
      try {
        if (ownsScrolls(targetIdKey)) {
          err.println("Error: user still owns scrolls; deletion blocked.");
          return 1;
        }
      } catch (Exception e) {
        err.println("I/O error: " + e.getMessage());
        return 3;
      }
    }

    out.println(
        "username="
            + nullToEmpty(target.username())
            + ", idKey="
            + targetIdKey
            + ", role="
            + nullToEmpty(target.role()));

    boolean skipConfirmation = yes && usernameFromFlag;
    if (!skipConfirmation) {
      if (reader == null) {
        reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      }
      out.print(
          "Delete user " + nullToEmpty(target.username()) + "? This cannot be undone. [y/N] ");
      out.flush();
      String answer;
      try {
        answer = reader.readLine();
      } catch (IOException e) {
        err.println("I/O error: " + e.getMessage());
        return 3;
      }
      String normalised = answer == null ? "" : answer.trim().toLowerCase(Locale.ROOT);
      if (!(normalised.equals("y") || normalised.equals("yes"))) {
        out.println("Aborted.");
        return 0;
      }
    }

    boolean deleted = users.deleteByUsername(target.username());
    if (!deleted) {
      err.println("Error: delete failed (persistence).");
      return 3;
    }

    out.println("Deleted user " + nullToEmpty(target.username()) + ".");
    return 0;
  }

  @Override
  public String name() {
    return "admin users delete";
  }

  @Override
  public String description() {
    return "Delete a user (admin only)";
  }

  private boolean ownsScrolls(String idKey) {
    for (Scroll scroll : scrolls.findAll()) {
      if (idKey.equalsIgnoreCase(nullToEmpty(scroll.uploaderIdKey()))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAdmin(String role) {
    return "ADMIN".equalsIgnoreCase(nullToEmpty(role));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
