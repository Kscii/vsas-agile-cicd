package org.soft2412.vsas.repo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.soft2412.vsas.model.Bookmark;

public final class FileBookmarkRepository implements BookmarkRepository {
  private static final Pattern TAB_OR_NEWLINE = Pattern.compile("[\\t\\r\\n]");
  private static final Path DEFAULT_PATH = Path.of("data", "bookmarks.tsv");
  private static final String[] HEADER = new String[] {"userIdKey", "scrollId", "addedAt"};

  private final Path dataFile;

  public FileBookmarkRepository() {
    this(DEFAULT_PATH);
  }

  public FileBookmarkRepository(Path dataFile) {
    this.dataFile = Objects.requireNonNull(dataFile, "dataFile");
  }

  @Override
  public boolean add(String userIdKey, String scrollId) {
    if (isBlank(userIdKey) || isBlank(scrollId)) {
      return false;
    }
    try {
      ensureFile();
      if (exists(userIdKey, scrollId)) {
        return true;
      }
      String row =
          String.join("\t", sanitize(userIdKey), sanitize(scrollId), Instant.now().toString())
              + System.lineSeparator();
      Files.writeString(
          dataFile, row, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public boolean remove(String userIdKey, String scrollId) {
    String targetUser = sanitize(userIdKey);
    String targetScroll = sanitize(scrollId);
    if (isBlank(targetUser) || isBlank(targetScroll)) {
      return false;
    }
    if (!Files.exists(dataFile)) {
      return false;
    }
    Path temp = null;
    boolean removed = false;
    try (BufferedReader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
      String header = reader.readLine();
      List<String> rows = new ArrayList<>();
      if (header != null) {
        rows.add(header);
      }
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length < 3) {
          rows.add(line);
          continue;
        }
        String rowUser = sanitize(parts[0]).trim();
        String rowScroll = sanitize(parts[1]).trim();
        if (targetUser.equals(rowUser) && targetScroll.equals(rowScroll)) {
          removed = true;
          continue;
        }
        rows.add(line);
      }
      if (!removed) {
        return false;
      }
      temp = dataFile.resolveSibling(dataFile.getFileName().toString() + ".tmp");
      try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        for (int i = 0; i < rows.size(); i++) {
          writer.write(rows.get(i));
          writer.write(System.lineSeparator());
        }
      }
      Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING);
      return true;
    } catch (IOException e) {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignore) {
        }
      }
      return false;
    }
  }

  @Override
  public boolean exists(String userIdKey, String scrollId) {
    String targetUser = sanitize(userIdKey);
    String targetScroll = sanitize(scrollId);
    if (isBlank(targetUser) || isBlank(targetScroll)) {
      return false;
    }
    if (!Files.exists(dataFile)) {
      return false;
    }
    try (BufferedReader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
      String header = reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length < 3) {
          continue;
        }
        String rowUser = sanitize(parts[0]).trim();
        String rowScroll = sanitize(parts[1]).trim();
        if (targetUser.equals(rowUser) && targetScroll.equals(rowScroll)) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public List<Bookmark> listByUser(String userIdKey) {
    if (isBlank(userIdKey) || !Files.exists(dataFile)) {
      return List.of();
    }
    List<Bookmark> result = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
      String header = reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        String[] parts = line.split("\t", -1);
        if (parts.length < 3) {
          continue;
        }
        if (!userIdKey.equals(parts[0])) {
          continue;
        }
        Instant added;
        try {
          added = Instant.parse(parts[2]);
        } catch (Exception e) {
          added = Instant.EPOCH;
        }
        result.add(new Bookmark(parts[0], parts[1], added));
      }
    } catch (IOException e) {
      return List.of();
    }
    Collections.sort(result, (a, b) -> a.addedAt().compareTo(b.addedAt()));
    return List.copyOf(result);
  }

  private void ensureFile() throws IOException {
    Path parent = dataFile.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    if (!Files.exists(dataFile)) {
      try (BufferedWriter writer = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
        writer.write(String.join("\t", HEADER));
        writer.write(System.lineSeparator());
      }
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return TAB_OR_NEWLINE.matcher(value).replaceAll(" ").trim();
  }
}
