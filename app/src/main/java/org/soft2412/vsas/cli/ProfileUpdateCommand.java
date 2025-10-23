package org.soft2412.vsas.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.service.SessionService;

/**
 * CLI handler for {@code profile update}.
 *
 * <p>Allows the logged-in user to update email/phone/password. Requires at least one field flag,
 * enforces password confirmation, and invalidates the current session after a password change.
 */
public final class ProfileUpdateCommand implements Command {

  private final PrintStream out;
  private final PrintStream err;
  private final UserRepository repo;
  private final SessionService sessions;

  public ProfileUpdateCommand() {
    this(System.out, System.err, new FileUserRepository(), new SessionService());
  }

  ProfileUpdateCommand(
      PrintStream out, PrintStream err, UserRepository repo, SessionService sessions) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.repo = Objects.requireNonNull(repo, "repo");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
  }

  @Override
  public int run(String[] args) {
    if (args == null || args.length == 0) {
      printUsage();
      return 2;
    }
    if (!"update".equals(args[0])) {
      err.println("Error: unsupported profile subcommand: " + args[0]);
      printUsage();
      return 2;
    }

    String newEmail = null;
    String newPhone = null;
    boolean changePassword = false;

    for (int i = 1; i < args.length; i++) {
      switch (args[i]) {
        case "--email":
          if (i + 1 < args.length) {
            newEmail = args[++i];
          } else {
            err.println("Error: --email requires a value");
            return 2;
          }
          break;
        case "--phone":
          if (i + 1 < args.length) {
            newPhone = args[++i];
          } else {
            err.println("Error: --phone requires a value");
            return 2;
          }
          break;
        case "--password":
          if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
            err.println("Error: --password does not take a value");
            return 2;
          }
          changePassword = true;
          break;
        default:
          // Ignore unknown flags for forward compatibility.
      }
    }

    if (newEmail == null && newPhone == null && !changePassword) {
      err.println("Error: specify at least one field (--email, --phone, or --password)");
      return 2;
    }

    if (newEmail != null && newEmail.trim().isEmpty()) {
      err.println("Error: email cannot be blank");
      return 2;
    }
    if (newPhone != null && newPhone.trim().isEmpty()) {
      err.println("Error: phone cannot be blank");
      return 2;
    }

    Optional<String> usernameOpt = sessions.currentUser().map(u -> u.username());
    if (usernameOpt.isEmpty() || usernameOpt.get().isBlank()) {
      err.println("Error: permission denied (login required)");
      return 1;
    }
    String username = usernameOpt.get();

    char[] passwordToSet = null;
    if (changePassword) {
      try {
        char[] first = PasswordPrompt.read(out, "Password: ");
        char[] second = PasswordPrompt.read(out, "Confirm password: ");
        boolean match = Arrays.equals(first, second);
        Arrays.fill(second, '\0');
        if (!match) {
          Arrays.fill(first, '\0');
          err.println("Error: Passwords do not match");
          return 1;
        }
        if (isBlank(first)) {
          Arrays.fill(first, '\0');
          err.println("Error: password cannot be empty");
          return 1;
        }
        passwordToSet = Arrays.copyOf(first, first.length);
        Arrays.fill(first, '\0');
      } catch (IOException ioe) {
        err.println("Error: password prompt failed");
        return 2;
      }
    }

    try {
      boolean ok = repo.updateProfile(username, newEmail, newPhone, passwordToSet);
      if (!ok) {
        err.println("Error: unable to update profile");
        return 2;
      }
    } finally {
      if (passwordToSet != null) {
        Arrays.fill(passwordToSet, '\0');
      }
    }

    out.println("Updated: " + username + ".");
    if (changePassword) {
      sessions.logout();
    }
    return 0;
  }

  @Override
  public String name() {
    return "profile";
  }

  @Override
  public String description() {
    return "Manage your profile (update)";
  }

  private void printUsage() {
    out.println("Usage: profile update [--email <e>] [--phone <ph>] [--password]");
  }

  private static boolean isBlank(char[] chars) {
    if (chars == null || chars.length == 0) {
      return true;
    }
    for (char c : chars) {
      if (!Character.isWhitespace(c)) {
        return false;
      }
    }
    return true;
  }
}
