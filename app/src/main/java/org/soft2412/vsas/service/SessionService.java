package org.soft2412.vsas.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.soft2412.vsas.model.User;

public final class SessionService {
  public Optional<User> currentUser() {
    try {
      Path s = Path.of("data", "session.json");
      if (!Files.exists(s)) return Optional.empty();
      String json = Files.readString(s, StandardCharsets.UTF_8);
      String username = extract(json, "username");
      String idKey = extract(json, "idKey");
      String role = extract(json, "role");
      if (username == null || username.isEmpty()) return Optional.empty();
      // other fields not relevant here
      return Optional.of(new User(username, "", "", idKey, role == null ? "user" : role, "", ""));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public boolean login(String username, String password) {
    return false;
  }

  public void logout() {
    try {
      Path s = Path.of("data", "session.json");
      Files.deleteIfExists(s);
    } catch (Exception ignore) {
    }
  }

  private String extract(String json, String key) {
    String q = "\"" + key + "\"";
    int i = json.indexOf(q);
    if (i < 0) return "";
    int colon = json.indexOf(":", i + q.length());
    if (colon < 0) return "";
    int start = colon + 1;
    while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
    if (start < json.length() && json.charAt(start) == '"') {
      int end = start + 1;
      StringBuilder sb = new StringBuilder();
      for (; end < json.length(); end++) {
        char c = json.charAt(end);
        if (c == '\\') {
          if (end + 1 < json.length()) {
            char n = json.charAt(end + 1);
            sb.append(n);
            end++;
          }
        } else if (c == '"') {
          break;
        } else {
          sb.append(c);
        }
      }
      return sb.toString();
    } else {
      int end = start;
      while (end < json.length()
          && (Character.isLetterOrDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
      return json.substring(start, end).trim();
    }
  }
}
