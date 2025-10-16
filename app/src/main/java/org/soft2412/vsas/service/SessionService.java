package org.soft2412.vsas.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.soft2412.vsas.model.User;

/**
 * Persistent session helper backed by {@code ${user.home}/.vsas/session.properties}.
 *
 * <p>The location can be overridden for tests by setting the {@code vsas.session.path} system
 * property to an absolute or relative file path.
 */
public final class SessionService {
  static final String SESSION_PATH_PROPERTY = "vsas.session.path";

  private final Path sessionPath;

  public SessionService() {
    this(resolveDefaultSessionPath());
  }

  SessionService(Path sessionPath) {
    this.sessionPath = Objects.requireNonNull(sessionPath, "sessionPath");
  }

  /** Load the current user from the session properties file, if present. */
  public Optional<User> currentUser() {
    try {
      if (!Files.exists(sessionPath)) {
        return Optional.empty();
      }
      Properties props = new Properties();
      try (BufferedReader reader = Files.newBufferedReader(sessionPath, StandardCharsets.UTF_8)) {
        props.load(reader);
      }
      String username = trimToNull(props.getProperty("username"));
      if (username == null) {
        return Optional.empty();
      }
      String idKey = trimToNull(props.getProperty("idKey"));
      String role = trimToNull(props.getProperty("role"));
      String issuedAt = trimToNull(props.getProperty("issuedAt"));
      Instant issued = issuedAt == null ? Instant.now() : parseInstant(issuedAt);
      return Optional.of(
          new User(
              username,
              "",
              "",
              idKey == null ? "" : idKey,
              role == null ? "user" : role,
              "",
              "",
              issued));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /**
   * Persist a new session for the supplied user. The {@code issuedAt} timestamp is generated at the
   * time of writing.
   *
   * @return {@code true} if the session was written successfully, {@code false} otherwise.
   */
  public boolean login(User user) {
    if (user == null || user.username() == null || user.username().isBlank()) {
      return false;
    }
    try {
      Path parent = sessionPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Properties props = new Properties();
      props.setProperty("username", user.username());
      props.setProperty("idKey", nullToEmpty(user.idKey()));
      props.setProperty("role", nullToEmpty(user.role()));
      props.setProperty("issuedAt", Instant.now().toString());
      try (BufferedWriter writer = Files.newBufferedWriter(sessionPath, StandardCharsets.UTF_8)) {
        props.store(writer, "VSAS session");
      }
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /** Remove the session file if it exists. */
  public void logout() {
    try {
      Files.deleteIfExists(sessionPath);
    } catch (IOException ignore) {
    }
  }

  private static Path resolveDefaultSessionPath() {
    String override = trimToNull(System.getProperty(SESSION_PATH_PROPERTY));
    if (override != null) {
      return Path.of(override);
    }
    String home = trimToNull(System.getProperty("user.home"));
    Path base = home == null ? Path.of(".") : Path.of(home);
    return base.resolve(".vsas").resolve("session.properties");
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static Instant parseInstant(String value) {
    try {
      return Instant.parse(value);
    } catch (Exception e) {
      return Instant.now();
    }
  }
}
