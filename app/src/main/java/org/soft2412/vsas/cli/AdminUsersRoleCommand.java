package org.soft2412.vsas.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.service.SessionService;

public final class AdminUsersRoleCommand implements Command {

  private final PrintStream out;
  private final PrintStream err;
  private final SessionService sessions;
  private final UserRepository users;

  public AdminUsersRoleCommand() {
    this(System.out, System.err, new SessionService(), new FileUserRepository());
  }

  AdminUsersRoleCommand(
      PrintStream out, PrintStream err, SessionService sessions, UserRepository users) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.users = Objects.requireNonNull(users, "users");
  }

  @Override
  public int run(String[] args) {
    String[] safeArgs = args == null ? new String[0] : args;
    String username = null;
    String roleArg = null;

    for (int i = 0; i < safeArgs.length; i++) {
      String arg = safeArgs[i];
      switch (arg) {
        case "--username":
          if (i + 1 >= safeArgs.length) {
            err.println("Missing value for --username");
            return 2;
          }
          username = safeArgs[++i].trim();
          break;
        case "--role":
          if (i + 1 >= safeArgs.length) {
            err.println("Missing value for --role");
            return 2;
          }
          roleArg = safeArgs[++i].trim();
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

    if (isBlank(username)) {
      err.println("Usage: admin users role --username <u> --role admin|user");
      return 2;
    }

    BufferedReader reader = null;
    if (roleArg == null || roleArg.isBlank()) {
      out.print("New role (admin|user): ");
      out.flush();
      try {
        reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line = reader.readLine();
        if (line != null) {
          roleArg = line.trim();
        }
      } catch (IOException e) {
        err.println("I/O error: " + e.getMessage());
        return 3;
      }
    }

    String normalisedRole = normaliseRole(roleArg);
    if (normalisedRole == null) {
      err.println("Invalid role: " + nullToEmpty(roleArg));
      err.println("Usage: admin users role --username <u> --role admin|user");
      return 2;
    }

    Optional<User> targetOpt = users.findByUsername(username);
    if (targetOpt.isEmpty()) {
      err.println("Error: user not found: " + username);
      return 1;
    }
    User target = targetOpt.get();

    String targetRole = target.role();
    if (targetRole != null && targetRole.equalsIgnoreCase(normalisedRole)) {
      out.println("No change");
      return 0;
    }

    boolean updated = users.updateRole(target.username(), normalisedRole);
    if (!updated) {
      err.println("Error: role update failed (persistence).");
      return 3;
    }

    out.println("Role updated: " + target.username() + " -> " + displayRole(normalisedRole));
    return 0;
  }

  @Override
  public String name() {
    return "admin users role";
  }

  @Override
  public String description() {
    return "Set the role for a user (admin only)";
  }

  private static boolean isAdmin(String role) {
    return "ADMIN".equalsIgnoreCase(nullToEmpty(role));
  }

  private static String normaliseRole(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if ("admin".equals(lower)) {
      return "ADMIN";
    }
    if ("user".equals(lower)) {
      return "USER";
    }
    return null;
  }

  private static String displayRole(String normalised) {
    if ("ADMIN".equalsIgnoreCase(normalised)) {
      return "admin";
    }
    if ("USER".equalsIgnoreCase(normalised)) {
      return "user";
    }
    return normalised == null ? "" : normalised;
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
