package org.soft2412.vsas.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class PasswordHasherTest {

  private final PasswordHasher hasher = new PasswordHasher();

  @Test
  void generateSalt_withinBounds() {
    byte[] s16 = hasher.generateSalt(16);
    byte[] s32 = hasher.generateSalt(32);
    assertEquals(16, s16.length);
    assertEquals(32, s32.length);

    // two salts should differ with high probability
    byte[] another16 = hasher.generateSalt(16);
    assertFalse(java.util.Arrays.equals(s16, another16));
  }

  @Test
  void generateSalt_invalidLength_throws() {
    assertThrows(IllegalArgumentException.class, () -> hasher.generateSalt(15));
    assertThrows(IllegalArgumentException.class, () -> hasher.generateSalt(33));
  }

  @Test
  void hashToHex_deterministicWithSameSaltAndPassword() {
    byte[] salt = hasher.generateSalt(16);
    char[] pwd = "P@ssw0rd!".toCharArray();

    String h1 = hasher.hashToHex(pwd, salt);
    String h2 = hasher.hashToHex(pwd, salt);
    assertEquals(h1, h2);
    assertEquals(64, h1.length());
    assertTrue(Pattern.compile("^[0-9a-f]{64}$").matcher(h1).matches());
  }

  @Test
  void hashToHex_diffSaltDiffHash() {
    char[] pwd = "same-password".toCharArray();
    byte[] s1 = hasher.generateSalt(16);
    byte[] s2 = hasher.generateSalt(16);

    String h1 = hasher.hashToHex(pwd, s1);
    String h2 = hasher.hashToHex(pwd, s2);
    assertNotEquals(h1, h2);
  }

  @Test
  void constantTimeEquals_correctness() {
    byte[] s = hasher.generateSalt(16);
    char[] pwd = "abc123".toCharArray();
    String hexA = hasher.hashToHex(pwd, s);
    String hexB = hasher.hashToHex(pwd, s);
    String hexC = hasher.hashToHex("other".toCharArray(), s);

    assertTrue(
        hasher.constantTimeEquals(
            PasswordHasher.hexToBytes(hexA), PasswordHasher.hexToBytes(hexB)));

    assertFalse(
        hasher.constantTimeEquals(
            PasswordHasher.hexToBytes(hexA), PasswordHasher.hexToBytes(hexC)));
  }

  @Test
  void hexUtils_roundTrip() {
    byte[] src = new byte[] {0x00, 0x7F, (byte) 0x80, (byte) 0xFF};
    String hex = PasswordHasher.bytesToHex(src);
    byte[] back = PasswordHasher.hexToBytes(hex);
    assertArrayEquals(src, back);
  }
}
