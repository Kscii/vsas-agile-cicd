package org.soft2412.vsas.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.security.PasswordHasher;
import org.soft2412.vsas.service.SessionService;

/**
 * Login via salted hash verification.
 *
 * <p>Expected TSV file: data/users.tsv with header: username email phone idKey role passwordHash
 * salt createdAt
 *
 * <p>This command stays within the task scope and does not modify repo/service code. It reads the
 * TSV, loads the stored salt/hash, recomputes the hash, and compares in constant time.
 */
public final class LoginCommand implements Command {

  private static final String DEFAULT_USERS_PATH = "data/users.tsv";
  private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

  private final PrintStream out;
  private final PrintStream err;
  private final PasswordHasher hasher;
  private final SessionService sessions;

  public LoginCommand() {
    this(System.out, System.err, new PasswordHasher(), new SessionService());
  }

  // Visible for tests
  LoginCommand(PrintStream out, PrintStream err, PasswordHasher hasher) {
    this(out, err, hasher, new SessionService());
  }

  LoginCommand(PrintStream out, PrintStream err, PasswordHasher hasher, SessionService sessions) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.hasher = Objects.requireNonNull(hasher, "hasher");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
  }

  @Override
  public int run(String[] args) {
    // Minimal flag parsing: --username <u> --password <p>
    String username = null;
    String password = null;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--username":
          if (i + 1 < args.length) username = args[++i];
          break;
        case "--password":
          if (i + 1 < args.length) password = args[++i];
          break;
        default:
          // ignore unknown flags for compatibility
      }
    }

    // Explicit null/blank checks to satisfy static analysis and prevent misuse.
    if (username == null
        || username.trim().isEmpty()
        || password == null
        || password.trim().isEmpty()) {
      err.println("Error: missing required flags. Usage: login --username <u> --password <p>");
      return 2;
    }

    char[] pwdChars = null;
    try {
      Optional<UserRow> rowOpt = findUserByUsername(Path.of(DEFAULT_USERS_PATH), username);
      if (rowOpt.isEmpty()) {
        err.println("Invalid credentials");
        return 1;
      }
      UserRow row = rowOpt.get();

      // Validate stored fields are well-formed hex.
      if (!HEX_64.matcher(row.passwordHashHex).matches()) {
        err.println("Invalid credentials");
        return 1;
      }

      byte[] saltBytes;
      try {
        saltBytes = PasswordHasher.hexToBytes(row.saltHex);
      } catch (IllegalArgumentException e) {
        err.println("Invalid credentials");
        return 1;
      }

      // Use char[] for password and wipe afterward.
      pwdChars = password.toCharArray();
      String computedHex = hasher.hashToHex(pwdChars, saltBytes);

      boolean ok =
          hasher.constantTimeEquals(
              PasswordHasher.hexToBytes(computedHex),
              PasswordHasher.hexToBytes(row.passwordHashHex));

      if (ok) {
        User user = row.toUser(username);
        if (!sessions.login(user)) {
          err.println("Error: unable to persist session");
          return 2;
        }
        out.println("Login success");
        return 0;
      } else {
        err.println("Invalid credentials");
        return 1;
      }
    } catch (IOException ioe) {
      err.println("Error: cannot read users file");
      return 2;
    } finally {
      if (pwdChars != null) {
        Arrays.fill(pwdChars, '\0'); // best-effort wipe
      }
    }
  }

  @Override
  public String name() {
    return "login";
  }

  @Override
  public String description() {
    return "Log in";
  }

  /**
   * Read data/users.tsv, map header columns, and return the row for the given username. Avoids
   * coupling to repository/service types in this task.
   */
  private static Optional<UserRow> findUserByUsername(Path tsvPath, String username)
      throws IOException {
    if (!Files.exists(tsvPath)) return Optional.empty();

    try (BufferedReader br = Files.newBufferedReader(tsvPath, StandardCharsets.UTF_8)) {
      String header = br.readLine();
      if (header == null) return Optional.empty();
      String[] cols = header.split("\t", -1);
      int idxUsername = indexOfCol(cols, "username");
      int idxIdKey = indexOfCol(cols, "idKey");
      int idxRole = indexOfCol(cols, "role");
      int idxPwdHash = indexOfCol(cols, "passwordHash");
      int idxSalt = indexOfCol(cols, "salt");
      if (idxUsername < 0 || idxPwdHash < 0 || idxSalt < 0) return Optional.empty();

      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.split("\t", -1);
        if (parts.length < cols.length) continue;
        if (username.equals(parts[idxUsername])) {
          String hashHex = parts[idxPwdHash];
          String saltHex = parts[idxSalt];
          String idKey = (idxIdKey >= 0 && idxIdKey < parts.length) ? parts[idxIdKey] : "";
          String role = (idxRole >= 0 && idxRole < parts.length) ? parts[idxRole] : "USER";
          return Optional.of(new UserRow(idKey, role, hashHex, saltHex));
        }
      }
      return Optional.empty();
    }
  }

  private static int indexOfCol(String[] headerCols, String target) {
    for (int i = 0; i < headerCols.length; i++) {
      if (target.equals(headerCols[i])) return i;
    }
    return -1;
  }

  /** Minimal projection of the fields we need from TSV. */
  private static final class UserRow {
    final String idKey;
    final String role;
    final String passwordHashHex;
    final String saltHex;

    UserRow(String idKey, String role, String passwordHashHex, String saltHex) {
      this.idKey = idKey;
      this.role = role;
      this.passwordHashHex = passwordHashHex;
      this.saltHex = saltHex;
    }

    User toUser(String username) {
      return new User(
          username,
          "",
          "",
          idKey == null ? "" : idKey,
          role == null || role.isBlank() ? "USER" : role,
          passwordHashHex,
          saltHex);
    }
  }
}
