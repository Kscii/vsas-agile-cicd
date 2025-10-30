package org.soft2412.vsas.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.security.PasswordHasher;
import org.soft2412.vsas.service.SessionService;

public final class AdminUsersAddCommand implements Command {

  private static final Pattern TAB_OR_NEWLINE = Pattern.compile("[\\t\\r\\n]");

  private final PrintStream out;
  private final PrintStream err;
  private final SessionService sessions;
  private final UserRepository users;
  private final PasswordHasher hasher;

  public AdminUsersAddCommand() {
    this(
        System.out,
        System.err,
        new SessionService(),
        new FileUserRepository(),
        new PasswordHasher());
  }

  AdminUsersAddCommand(
      PrintStream out,
      PrintStream err,
      SessionService sessions,
      UserRepository users,
      PasswordHasher hasher) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.users = Objects.requireNonNull(users, "users");
    this.hasher = Objects.requireNonNull(hasher, "hasher");
  }

  @Override
  public int run(String[] args) {
    String username = null;
    String idKey = null;
    String roleArg = null;
    String email = null;
    String phone = null;
    String password = null;

    String[] safe = args == null ? new String[0] : args;
    for (int i = 0; i < safe.length; i++) {
      String a = safe[i];
      switch (a) {
        case "--username":
          if (i + 1 >= safe.length) {
            err.println("Missing value for --username");
            return 2;
          }
          username = safe[++i].trim();
          break;
        case "--id-key":
          if (i + 1 >= safe.length) {
            err.println("Missing value for --id-key");
            return 2;
          }
          idKey = safe[++i].trim();
          break;
        case "--role":
          if (i + 1 >= safe.length) {
            err.println("Missing value for --role");
            return 2;
          }
          roleArg = safe[++i].trim();
          break;
        case "--email":
          if (i + 1 >= safe.length) {
            err.println("Missing value for --email");
            return 2;
          }
          email = safe[++i].trim();
          break;
        case "--phone":
          if (i + 1 >= safe.length) {
            err.println("Missing value for --phone");
            return 2;
          }
          phone = safe[++i].trim();
          break;
        case "--password":
          if (i + 1 >= safe.length) {
            err.println("Missing value for --password");
            return 2;
          }
          password = safe[++i];
          break;
        default:
          err.println("Unknown option: " + a);
          return 2;
      }
    }

    Optional<org.soft2412.vsas.model.User> currentOpt = sessions.currentUser();
    if (currentOpt.isEmpty()) {
      err.println("Forbidden: admin login required.");
      return 1;
    }
    User current = currentOpt.get();
    if (!isAdmin(current.role())) {
      err.println("Forbidden: admin role required.");
      return 1;
    }

    if (isBlank(username) || isBlank(idKey) || isBlank(roleArg)) {
      err.println(
          "Usage: admin users add --username <u> --id-key <k> --role user|admin [--email <e>] [--phone <ph>] [--password <p>]");
      return 2;
    }

    String role = normalizeRole(roleArg);
    if (role == null) {
      err.println("Invalid role: " + nullToEmpty(roleArg));
      return 1;
    }

    if (!isBlank(email) && !isValidEmail(email)) {
      err.println("Invalid email: " + email);
      return 1;
    }
    if (!isBlank(phone) && !isValidPhone(phone)) {
      err.println("Invalid phone: " + phone);
      return 1;
    }

    try {
      if (users.findByUsername(username).isPresent()) {
        err.println("Error: username already exists: " + username);
        return 1;
      }
      if (users.existsIdKey(idKey)) {
        err.println("Error: id-key already exists: " + idKey);
        return 1;
      }
    } catch (Exception ignored) {
    }

    if (isBlank(password)) {
      try {
        char[] p1 = PasswordPrompt.read(out, "Password: ");
        char[] p2 = PasswordPrompt.read(out, "Confirm password: ");
        boolean match = Arrays.equals(p1, p2);
        password = new String(p1);
        Arrays.fill(p1, '\0');
        Arrays.fill(p2, '\0');
        if (!match) {
          err.println("Error: Passwords do not match");
          return 1;
        }
        if (password.trim().isEmpty()) {
          err.println("Error: password cannot be empty");
          return 1;
        }
      } catch (IOException ioe) {
        err.println("I/O error: password prompt failed");
        return 3;
      }
    }

    username = sanitize(username);
    idKey = sanitize(idKey);
    role = sanitize(role);
    email = email == null ? "" : sanitize(email);
    phone = phone == null ? "" : sanitize(phone);

    char[] pwdChars = null;
    try {
      byte[] salt = hasher.generateSalt(16);
      pwdChars = password.toCharArray();
      String hashHex = hasher.hashToHex(pwdChars, salt);
      String saltHex = PasswordHasher.bytesToHex(salt);

      User toSave = new User(username, email, phone, idKey, role, hashHex, saltHex, Instant.now());
      boolean ok = users.save(toSave);
      if (!ok) {
        err.println("Error: cannot persist user");
        return 3;
      }

      out.println("Created: " + username + " (" + displayRole(role) + ")");
      return 0;
    } catch (Exception e) {
      err.println("Error: cannot persist user");
      return 3;
    } finally {
      if (pwdChars != null) Arrays.fill(pwdChars, '\0');
    }
  }

  @Override
  public String name() {
    return "admin users add";
  }

  @Override
  public String description() {
    return "Create a user account (admin only)";
  }

  private static boolean isAdmin(String role) {
    return "ADMIN".equalsIgnoreCase(nullToEmpty(role));
  }

  private static String normalizeRole(String v) {
    if (v == null) return null;
    String lower = v.trim().toLowerCase(Locale.ROOT);
    if (lower.isEmpty()) return null;
    if ("admin".equals(lower)) return "ADMIN";
    if ("user".equals(lower)) return "USER";
    return null;
  }

  private static String displayRole(String normalised) {
    if ("ADMIN".equalsIgnoreCase(normalised)) return "admin";
    if ("USER".equalsIgnoreCase(normalised)) return "user";
    return normalised == null ? "" : normalised;
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }

  private static String sanitize(String s) {
    return TAB_OR_NEWLINE.matcher(s).replaceAll(" ").trim();
  }

  private static boolean isValidEmail(String e) {
    if (e == null) return false;
    String v = e.trim();
    if (v.isEmpty()) return false;
    int at = v.indexOf('@');
    if (at <= 0 || at == v.length() - 1) return false;
    if (v.contains(" ")) return false;
    String domain = v.substring(at + 1);
    return domain.contains(".");
  }

  private static boolean isValidPhone(String p) {
    if (p == null) return false;
    String v = p.trim();
    if (v.isEmpty()) return false;
    String digits = v.startsWith("+") ? v.substring(1) : v;
    for (int i = 0; i < digits.length(); i++) {
      char c = digits.charAt(i);
      if (c < '0' || c > '9') return false;
    }
    return digits.length() >= 8 && digits.length() <= 15;
  }
}
