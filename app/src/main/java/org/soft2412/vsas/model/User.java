package org.soft2412.vsas.model;

import java.time.Instant;

/**
 * Domain model for a VSAS user (US-A1 Task #8).
 *
 * <p>Fields: username, email, phone, idKey, role, passwordHash, salt, createdAt
 *
 * <p>Notes: - Keeps original constructor/getter style. - Adds createdAt; provides a 7-arg
 * constructor for backward compatibility. - passwordHash and salt are hex strings (hash=64 hex;
 * salt=32..64 hex).
 */
public final class User {
  private final String username;
  private final String email;
  private final String phone;
  private final String idKey;
  private final String role;
  private final String passwordHash;
  private final String salt;
  private final Instant createdAt;

  /**
   * Backward-compatible constructor (original 7-arg signature). createdAt defaults to
   * Instant.now().
   */
  public User(
      String username,
      String email,
      String phone,
      String idKey,
      String role,
      String passwordHash,
      String salt) {
    this(username, email, phone, idKey, role, passwordHash, salt, null);
  }

  /** Full constructor with createdAt. If createdAt is null, it defaults to Instant.now(). */
  public User(
      String username,
      String email,
      String phone,
      String idKey,
      String role,
      String passwordHash,
      String salt,
      Instant createdAt) {
    this.username = username;
    this.email = email;
    this.phone = phone;
    this.idKey = idKey;
    this.role = role;
    this.passwordHash = passwordHash;
    this.salt = salt;
    this.createdAt = (createdAt == null) ? Instant.now() : createdAt;
  }

  public String username() {
    return username;
  }

  public String email() {
    return email;
  }

  public String phone() {
    return phone;
  }

  public String idKey() {
    return idKey;
  }

  public String role() {
    return role;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public String salt() {
    return salt;
  }

  /** Creation timestamp (UTC). */
  public Instant createdAt() {
    return createdAt;
  }

  @Override
  public String toString() {
    return "User{"
        + "username='"
        + username
        + '\''
        + ", email='"
        + email
        + '\''
        + ", phone='"
        + phone
        + '\''
        + ", idKey='"
        + idKey
        + '\''
        + ", role='"
        + role
        + '\''
        + ", passwordHash=<redacted>"
        + ", salt=<redacted>"
        + ", createdAt="
        + createdAt
        + '}';
  }
}
