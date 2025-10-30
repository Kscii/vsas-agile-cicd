package org.soft2412.vsas.model;

import java.time.Instant;
import java.util.Objects;

/** Simple value type representing a bookmarked scroll for a specific user. */
public final class Bookmark {
  private final String userIdKey;
  private final String scrollId;
  private final Instant addedAt;

  public Bookmark(String userIdKey, String scrollId, Instant addedAt) {
    this.userIdKey = Objects.requireNonNull(userIdKey, "userIdKey");
    this.scrollId = Objects.requireNonNull(scrollId, "scrollId");
    this.addedAt = Objects.requireNonNull(addedAt, "addedAt");
  }

  public String userIdKey() {
    return userIdKey;
  }

  public String scrollId() {
    return scrollId;
  }

  public Instant addedAt() {
    return addedAt;
  }
}
