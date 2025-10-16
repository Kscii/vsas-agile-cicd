package org.soft2412.vsas.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Password hashing utility.
 *
 * <p>Algorithm: SHA-256 over (salt || UTF-8(password)). - Salt length must be 16..32 bytes
 * (per-user random, SecureRandom). - Digest is returned as lowercase Hex string (length = 64). -
 * Provides constant-time equality check via MessageDigest.isEqual.
 *
 * <p>Notes: - This class is stateless; a SecureRandom instance is held for salt generation only. -
 * Callers are responsible for not storing or logging plaintext passwords.
 */
public final class PasswordHasher {

  private static final String ALG = "SHA-256";
  private final SecureRandom rng;

  public PasswordHasher() {
    this.rng = new SecureRandom();
  }

  /**
   * Generate a random salt.
   *
   * @param length number of bytes (must be 16..32)
   * @return salt bytes
   * @throws IllegalArgumentException if length is out of [16, 32]
   */
  public byte[] generateSalt(int length) {
    if (length < 16 || length > 32) {
      throw new IllegalArgumentException("salt length must be between 16 and 32 bytes");
    }
    byte[] salt = new byte[length];
    rng.nextBytes(salt);
    return salt;
  }

  /**
   * Compute SHA-256(salt || UTF-8(password)) and return Hex digest.
   *
   * @param password password characters (will be UTF-8 encoded)
   * @param salt salt bytes (non-null)
   * @return 64-char lowercase Hex string
   */
  public String hashToHex(char[] password, byte[] salt) {
    Objects.requireNonNull(password, "password");
    Objects.requireNonNull(salt, "salt");

    byte[] pwdBytes = null;
    try {
      MessageDigest md = getDigest();
      md.update(salt);

      // Encode password to UTF-8 (avoid platform default charset).
      // We go via String here for simplicity; we wipe the byte[] afterward.
      pwdBytes = new String(password).getBytes(StandardCharsets.UTF_8);
      md.update(pwdBytes);

      byte[] digest = md.digest();
      String hex = bytesToHex(digest);
      // best effort wipe digest as well
      Arrays.fill(digest, (byte) 0);
      return hex;
    } finally {
      if (pwdBytes != null) {
        Arrays.fill(pwdBytes, (byte) 0); // best effort wipe
      }
    }
  }

  /**
   * Constant-time equality for two byte arrays.
   *
   * @return true if equal, false otherwise
   */
  public boolean constantTimeEquals(byte[] a, byte[] b) {
    if (a == null || b == null) return false;
    return MessageDigest.isEqual(a, b);
  }

  /** Convert bytes to lowercase hex string. */
  public static String bytesToHex(byte[] bytes) {
    char[] out = new char[bytes.length * 2];
    final char[] HEX = "0123456789abcdef".toCharArray();
    int i = 0;
    for (byte b : bytes) {
      int v = b & 0xFF;
      out[i++] = HEX[v >>> 4];
      out[i++] = HEX[v & 0x0F];
    }
    return new String(out);
  }

  /** Parse lowercase/uppercase hex string into bytes. */
  public static byte[] hexToBytes(String hex) {
    Objects.requireNonNull(hex, "hex");
    if ((hex.length() & 1) != 0) {
      throw new IllegalArgumentException("hex length must be even");
    }
    int len = hex.length();
    byte[] out = new byte[len / 2];
    for (int i = 0, j = 0; i < len; i += 2, j++) {
      int hi = Character.digit(hex.charAt(i), 16);
      int lo = Character.digit(hex.charAt(i + 1), 16);
      if (hi < 0 || lo < 0) {
        throw new IllegalArgumentException("invalid hex character at " + i);
      }
      out[j] = (byte) ((hi << 4) + lo);
    }
    return out;
  }

  private static MessageDigest getDigest() {
    try {
      return MessageDigest.getInstance(ALG);
    } catch (NoSuchAlgorithmException e) {
      // Should never happen on a standard JDK
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
