package org.soft2412.vsas.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileUserRepository;
import org.soft2412.vsas.repo.UserRepository;
import org.soft2412.vsas.security.PasswordHasher;
import org.soft2412.vsas.service.SessionService;

public final class LoginCommand implements Command {

  private final PrintStream out;
  private final PrintStream err;
  private final PasswordHasher hasher;
  private final SessionService sessions;
  private final UserRepository users;

  public LoginCommand() {
    this(
        System.out,
        System.err,
        new PasswordHasher(),
        new SessionService(),
        new FileUserRepository());
  }

  LoginCommand(PrintStream out, PrintStream err, PasswordHasher hasher) {
    this(out, err, hasher, new SessionService(), new FileUserRepository());
  }

  LoginCommand(
      PrintStream out,
      PrintStream err,
      PasswordHasher hasher,
      SessionService sessions,
      UserRepository users) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.hasher = Objects.requireNonNull(hasher, "hasher");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.users = Objects.requireNonNull(users, "users");
  }

  @Override
  public int run(String[] args) {
    String username = null;
    String password = null;

    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        String a = args[i];
        switch (a) {
          case "--username":
            if (i + 1 < args.length) username = args[++i];
            break;
          case "--password":
            if (i + 1 < args.length) password = args[++i];
            break;
          default:
            // ignore unknowns
        }
      }
    }

    if (username == null || username.trim().isEmpty()) {
      err.println("Error: missing required flags. Usage: login --username <u> [--password <p>]");
      return 2;
    }

    if (password == null || password.trim().isEmpty()) {
      try {
        char[] p = PasswordPrompt.read(out, "Password: ");
        password = new String(p);
        Arrays.fill(p, '\0');
        if (password.trim().isEmpty()) {
          err.println("Error: password cannot be empty");
          return 1;
        }
      } catch (IOException ioe) {
        err.println("Error: password prompt failed");
        return 3;
      }
    }

    char[] pwdChars = null;
    try {
      Optional<User> row = users.findByUsername(username);
      if (row.isEmpty()) {
        err.println("Invalid credentials");
        return 1;
      }
      User u = row.get();

      String hashHex = u.passwordHash() == null ? "" : u.passwordHash().trim();
      String saltHex = u.salt() == null ? "" : u.salt().trim();
      if (hashHex.length() != 64) {
        err.println("Invalid credentials");
        return 1;
      }

      byte[] saltBytes;
      try {
        saltBytes = PasswordHasher.hexToBytes(saltHex);
      } catch (IllegalArgumentException e) {
        err.println("Invalid credentials");
        return 1;
      }

      pwdChars = password.toCharArray();
      String computedHex = hasher.hashToHex(pwdChars, saltBytes);

      boolean ok =
          hasher.constantTimeEquals(
              PasswordHasher.hexToBytes(computedHex), PasswordHasher.hexToBytes(hashHex));

      if (!ok) {
        err.println("Invalid credentials");
        return 1;
      }

      User sessionUser =
          new User(
              u.username(),
              u.email(),
              u.phone(),
              u.idKey(),
              u.role(),
              u.passwordHash(),
              u.salt(),
              u.createdAt());
      if (!sessions.login(sessionUser)) {
        err.println("Error: unable to persist session");
        return 3;
      }

      out.println("Login success");
      return 0;

    } catch (Exception e) {
      err.println("Error: cannot read users file");
      return 3;
    } finally {
      if (pwdChars != null) Arrays.fill(pwdChars, '\0');
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
}
