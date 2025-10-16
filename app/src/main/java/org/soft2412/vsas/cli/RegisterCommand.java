package org.soft2412.vsas.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import org.soft2412.vsas.security.PasswordHasher;

/**
 * Register a user by writing a TSV row with salted password hash.
 *
 * This command is limited to Task #39 scope:
 * - Generate per-user random salt (16 bytes).
 * - Compute SHA-256(salt || UTF-8(password)) as hex digest.
 * - Persist only passwordHash (hex) and salt (hex); never store plaintext.
 * - Create data/users.tsv if missing and ensure header exists.
 *
 * It intentionally does NOT implement idKey uniqueness or full validation;
 * those belong to a different story/task.
 */
public final class RegisterCommand implements Command {

  private static final String DEFAULT_USERS_PATH = "data/users.tsv";
  private static final String[] HEADER = new String[] {
      "username", "email", "phone", "idKey", "role", "passwordHash", "salt", "createdAt"
  };
  private static final Pattern TAB_OR_NEWLINE = Pattern.compile("[\\t\\r\\n]");

  private final PrintStream out;
  private final PrintStream err;
  private final PasswordHasher hasher;

  public RegisterCommand() {
    this(System.out, System.err, new PasswordHasher());
  }

  // Visible for tests
  RegisterCommand(PrintStream out, PrintStream err, PasswordHasher hasher) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.hasher = Objects.requireNonNull(hasher, "hasher");
  }

  @Override
  public int run(String[] args) {
    // Minimal flags required for this task:
    // --username <u> --password <p>
    // Optional (carried through into TSV as-is or empty):
    // --email <e> --phone <ph> --id-key <k> --role <r>
    String username = null, password = null, email = "", phone = "", idKey = "", role = "USER";
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--username":
          if (i + 1 < args.length)
            username = args[++i];
          break;
        case "--password":
          if (i + 1 < args.length)
            password = args[++i];
          break;
        case "--email":
          if (i + 1 < args.length)
            email = args[++i];
          break;
        case "--phone":
          if (i + 1 < args.length)
            phone = args[++i];
          break;
        case "--id-key":
          if (i + 1 < args.length)
            idKey = args[++i];
          break;
        case "--role":
          if (i + 1 < args.length)
            role = args[++i];
          break;
        default:
          // ignore unknown flags for compatibility
      }
    }

    // Explicit null/blank checks to silence static analysis and prevent misuse.
    if (username == null || username.trim().isEmpty()
        || password == null || password.trim().isEmpty()) {
      err.println(
          "Error: missing required flags. Usage: vsas register --username <u> --password <p> [--email <e> --phone <ph> --id-key <k> --role <r>]");
      return 2;
    }

    // Sanitize fields to keep TSV well-formed (no tabs/newlines).
    username = sanitize(username);
    email = sanitize(email);
    phone = sanitize(phone);
    idKey = sanitize(idKey);
    role = sanitize(role);

    char[] pwdChars = null;
    try {
      // Ensure data directory and header
      Path usersPath = Path.of(DEFAULT_USERS_PATH);
      ensureHeader(usersPath);

      // Salt + hash
      byte[] salt = hasher.generateSalt(16);
      pwdChars = password.toCharArray(); // explicit non-null use
      String hashHex = hasher.hashToHex(pwdChars, salt);
      String saltHex = PasswordHasher.bytesToHex(salt);

      // Persist a single TSV row
      String createdAt = Instant.now().toString();
      String row = String.join("\t",
          nvl(username), nvl(email), nvl(phone), nvl(idKey), nvl(role),
          hashHex, saltHex, createdAt) + "\n";

      Files.writeString(usersPath, row, StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.APPEND);

      out.println("Registered user " + username);
      return 0;
    } catch (IOException ioe) {
      err.println("Error: cannot persist user");
      return 2;
    } finally {
      if (pwdChars != null)
        Arrays.fill(pwdChars, '\0'); // best-effort wipe
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
    return s == null ? "" : TAB_OR_NEWLINE.matcher(s).replaceAll(" ").trim();
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }

  private static void ensureHeader(Path usersPath) throws IOException {
    Path dir = usersPath.getParent();
    if (dir != null && !Files.exists(dir))
      Files.createDirectories(dir);

    if (!Files.exists(usersPath)) {
      String header = String.join("\t", HEADER) + "\n";
      Files.writeString(usersPath, header, StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE_NEW);
    }
  }
}
