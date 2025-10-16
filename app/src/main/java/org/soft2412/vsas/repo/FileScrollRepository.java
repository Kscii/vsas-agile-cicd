package org.soft2412.vsas.repo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;

public final class FileScrollRepository implements ScrollRepository {
  private final Path metaDir = Path.of("data", "scrolls");

  public FileScrollRepository() {}

  @Override
  public List<Scroll> findAll() {
    try {
      if (!Files.exists(metaDir)) {
        return List.of();
      }
      List<Scroll> result = new ArrayList<>();
      for (Path p : Files.list(metaDir).toList()) {
        if (p.getFileName().toString().endsWith(".json")) {
          parseScroll(p).ifPresent(result::add);
        }
      }
      return result;
    } catch (IOException e) {
      return List.of();
    }
  }

  @Override
  public Optional<Scroll> findById(String id) {
    Path file = metaDir.resolve(id + ".json");
    return parseScroll(file);
  }

  @Override
  public boolean existsId(String id) {
    Path file = metaDir.resolve(id + ".json");
    return Files.exists(file);
  }

  @Override
  public boolean save(Scroll scroll) {
    try {
      Files.createDirectories(metaDir);
      Path file = metaDir.resolve(scroll.id() + ".json");
      String json = toJson(scroll);
      Files.writeString(file, json, StandardCharsets.UTF_8);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private Optional<Scroll> parseScroll(Path file) {
    try {
      if (!Files.exists(file)) return Optional.empty();
      String s = Files.readString(file, StandardCharsets.UTF_8);
      return Optional.of(fromJson(s));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  // Very small JSON writer to avoid extra deps
  private String toJson(Scroll s) {
    return "{"
        + "\"id\":\""
        + escape(s.id())
        + "\",\"name\":\""
        + escape(s.name())
        + "\",\"uploaderIdKey\":\""
        + escape(s.uploaderIdKey())
        + "\",\"uploadDate\":\""
        + escape(s.uploadDate())
        + "\",\"filePath\":\""
        + escape(s.filePath())
        + "\",\"downloadCount\":"
        + s.downloadCount()
        + "}";
  }

  private Scroll fromJson(String json) {
    // Extremely small parser for the known shape
    String id = extract(json, "id");
    String name = extract(json, "name");
    String uploaderIdKey = extract(json, "uploaderIdKey");
    String uploadDate = extract(json, "uploadDate");
    String filePath = extract(json, "filePath");
    String dc = extract(json, "downloadCount");
    long downloadCount = 0L;
    try {
      downloadCount = Long.parseLong(dc);
    } catch (Exception ignore) {
    }
    return new Scroll(id, name, uploaderIdKey, uploadDate, filePath, downloadCount);
  }

  private String extract(String json, String key) {
    // naive extraction: looks for "key": value (quoted or number)
    String q = "\"" + key + "\"";
    int i = json.indexOf(q);
    if (i < 0) return "";
    int colon = json.indexOf(":", i + q.length());
    if (colon < 0) return "";
    int start = colon + 1;
    // skip spaces
    while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
    if (start < json.length() && json.charAt(start) == '"') {
      int end = start + 1;
      StringBuilder sb = new StringBuilder();
      for (; end < json.length(); end++) {
        char c = json.charAt(end);
        if (c == '\\') {
          if (end + 1 < json.length()) {
            char n = json.charAt(end + 1);
            if (n == '"' || n == '\\' || n == '/') {
              sb.append(n);
              end++;
            } else if (n == 'n') {
              sb.append('\n');
              end++;
            } else if (n == 't') {
              sb.append('\t');
              end++;
            } else if (n == 'r') {
              sb.append('\r');
              end++;
            } else {
              sb.append(n);
              end++;
            }
          }
        } else if (c == '"') {
          break;
        } else {
          sb.append(c);
        }
      }
      return sb.toString();
    } else {
      // number
      int end = start;
      while (end < json.length()
          && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
      return json.substring(start, end).trim();
    }
  }

  private String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
