package org.soft2412.vsas.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.security.PasswordHasher;

/**
 * US-A1 Task #13: vsas register --username <u> --password
 *
 * <p>--email <e> --phone <ph> --id-key <k>
 *
 * <p>Scope: - Parse required flags; on success print a message and exit code 0. - Generate per-user
 * salt; hash password via PasswordHasher; persist via UserRepository. - Never store plaintext
 * password. - Header/file creation is handled by the repository (TSV storage).
 *
 * <p>Uniqueness of idKey is enforced by Task #14 (duplicate -> non-zero + clear error).
 */
public final class RegisterCommand implements Command {

  private static final Pattern TAB_OR_NEWLINE = Pattern.compile("[\\t\\r\\n]");

  private final PrintStream out;
  private final PrintStream err;
  private final PasswordHasher hasher;
  private final UserRepository repo;

  public RegisterCommand() {
    this(System.out, System.err, new PasswordHasher(), new FileUserRepository());
  }

  // Back-compat for existing tests (A6)
  RegisterCommand(PrintStream out, PrintStream err, PasswordHasher hasher) {
    this(out, err, hasher, new FileUserRepository());
  }

  // Visible for tests / DI
  RegisterCommand(PrintStream out, PrintStream err, PasswordHasher hasher, UserRepository repo) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.hasher = Objects.requireNonNull(hasher, "hasher");
    this.repo = Objects.requireNonNull(repo, "repo");
  }

  @Override
  public int run(String[] args) {
    // Required by US-A1: username, password (or interactive prompt), email, phone,
    // id-key
    String username = null, password = null, email = null, phone = null, idKey = null;
    String role = "USER"; // optional; default for this story

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--username":
          if (i + 1 < args.length) username = args[++i];
          break;
        case "--password":
          if (i + 1 < args.length) password = args[++i];
          break;
        case "--email":
          if (i + 1 < args.length) email = args[++i];
          break;
        case "--phone":
          if (i + 1 < args.length) phone = args[++i];
          break;
        case "--id-key":
          if (i + 1 < args.length) idKey = args[++i];
          break;
        case "--role":
          if (i + 1 < args.length) role = args[++i];
          break;
        default:
          /* ignore unknown for forward-compat */ }
    }

    if (username == null
        || username.trim().isEmpty()
        || email == null
        || email.trim().isEmpty()
        || phone == null
        || phone.trim().isEmpty()
        || idKey == null
        || idKey.trim().isEmpty()) {
      err.println(
          "Error: missing required flags. Usage: register --username <u> [--password <p>] --email <e> --phone <ph> --id-key <k> [--role user|admin]");
      return 2;
    }

    if (password == null || password.trim().isEmpty()) {
      try {
        char[] p1 = org.soft2412.vsas.cli.PasswordPrompt.read(out, "Password: ");
        char[] p2 = org.soft2412.vsas.cli.PasswordPrompt.read(out, "Confirm password: ");
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
        err.println("Error: password prompt failed");
        return 3;
      }
    }

    // Sanitize fields for TSV safety
    username = sanitize(username);
    email = sanitize(email);
    phone = sanitize(phone);
    idKey = sanitize(idKey);
    role = normalizeRole(role);

    // ---- Task #14: enforce unique idKey BEFORE hashing/persisting ----
    try {
      if (repo.existsIdKey(idKey)) {
        err.println("Error: id-key already exists: " + idKey);
        return 1; // non-zero per acceptance criteria
      }
    } catch (Exception ignored) {
      // Repository interface doesn't throw checked exceptions; keep defensive catch.
    }

    final String nonNullPassword = password; // ensured non-null above
    char[] pwdChars = null;
    try {
      // Salt + hash (never store plaintext)
      byte[] salt = hasher.generateSalt(16);
      pwdChars = nonNullPassword.toCharArray();
      String hashHex = hasher.hashToHex(pwdChars, salt);
      String saltHex = PasswordHasher.bytesToHex(salt);

      // Persist via repository (repo ensures header and file)
      User user = new User(username, email, phone, idKey, role, hashHex, saltHex, Instant.now());
      boolean ok = repo.save(user);
      if (!ok) {
        err.println("Error: cannot persist user");
        return 3;
      }

      // Success path per acceptance criteria
      out.println("Registered user " + username);
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
    return "register";
  }

  @Override
  public String description() {
    return "Register a new user";
  }

  private static String sanitize(String s) {
    return TAB_OR_NEWLINE.matcher(s).replaceAll(" ").trim();
  }

  private static String normalizeRole(String role) {
    if (role == null || role.trim().isEmpty()) {
      return "USER";
    }
    String cleaned = sanitize(role);
    if (cleaned.equalsIgnoreCase("admin")) {
      return "ADMIN";
    }
    if (cleaned.equalsIgnoreCase("user")) {
      return "USER";
    }
    return cleaned.toUpperCase(Locale.ROOT);
  }
}
