package org.soft2412.vsas.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

public class UserTest {

  @Test
  void createdAt_defaultsToNowWhenNull() {
    Instant before = Instant.now();
    User u =
        new User(
            "alice",
            "alice@example.com",
            "0400000000",
            "K-001",
            "USER",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "0123456789abcdef0123456789abcdef",
            null // createdAt -> defaults to now
            );
    assertNotNull(u.createdAt());
    assertFalse(u.createdAt().isBefore(before));
  }

  @Test
  void createdAt_respectedWhenProvided() {
    Instant ts = Instant.parse("2025-01-01T00:00:00Z");
    User u =
        new User(
            "bob",
            "",
            "",
            "K-002",
            "VISITOR",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            ts);
    assertEquals(ts, u.createdAt());
  }
}
