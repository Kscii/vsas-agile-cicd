package org.soft2412.vsas.model;

public final class User {
  private final String username;
  private final String email;
  private final String phone;
  private final String idKey;
  private final String role;
  private final String passwordHash;
  private final String salt;

  public User(
      String username,
      String email,
      String phone,
      String idKey,
      String role,
      String passwordHash,
      String salt) {
    this.username = username;
    this.email = email;
    this.phone = phone;
    this.idKey = idKey;
    this.role = role;
    this.passwordHash = passwordHash;
    this.salt = salt;
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
}
