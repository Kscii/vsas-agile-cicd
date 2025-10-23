package org.soft2412.vsas.repo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;

public final class FileScrollRepository implements ScrollRepository {
  private final Path dataFile = Path.of("data", "scrolls.tsv");

  public FileScrollRepository() {}

  @Override
  public List<Scroll> findAll() {
    try {
      if (!Files.exists(dataFile)) {
        return List.of();
      }
      List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
      List<Scroll> result = new ArrayList<>();
      for (String line : lines) {
        if (line == null || line.isBlank()) continue;
        parseTsv(line).ifPresent(result::add);
      }
      return result;
    } catch (IOException e) {
      return List.of();
    }
  }

  @Override
  public Optional<Scroll> findById(String id) {
    try {
      if (!Files.exists(dataFile)) return Optional.empty();
      for (String line : Files.readAllLines(dataFile, StandardCharsets.UTF_8)) {
        if (line == null || line.isBlank()) continue;
        String[] parts = line.split("\t", -1);
        if (parts.length >= 6 && id.equals(parts[0])) {
          return parseTsv(line);
        }
      }
      return Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  @Override
  public boolean existsId(String id) {
    try {
      if (!Files.exists(dataFile)) return false;
      for (String line : Files.readAllLines(dataFile, StandardCharsets.UTF_8)) {
        if (line == null || line.isBlank()) continue;
        String[] parts = line.split("\t", -1);
        if (parts.length >= 1 && id.equals(parts[0])) return true;
      }
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public boolean save(Scroll scroll) {
    try {
      Files.createDirectories(dataFile.getParent());
      String line = toTsv(scroll) + System.lineSeparator();
      Files.writeString(
          dataFile,
          line,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private Optional<Scroll> parseTsv(String line) {
    try {
      String[] p = line.split("\t", -1);
      if (p.length < 6) return Optional.empty();
      String id = p[0];
      String name = p[1];
      String uploaderIdKey = p[2];
      String uploadDate = p[3];
      String filePath = p[4];
      long downloadCount = 0L;
      try {
        downloadCount = Long.parseLong(p[5]);
      } catch (NumberFormatException ignore) {
      }
      return Optional.of(new Scroll(id, name, uploaderIdKey, uploadDate, filePath, downloadCount));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private String toTsv(Scroll s) {
    return String.join(
        "\t",
        nullToEmpty(s.id()),
        nullToEmpty(s.name()),
        nullToEmpty(s.uploaderIdKey()),
        nullToEmpty(s.uploadDate()),
        nullToEmpty(s.filePath()),
        Long.toString(s.downloadCount()));
  }

  private String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  @Override
  public boolean deleteById(String id) {
    java.util.List<Scroll> all = findAll();
    boolean hit = false;
    java.util.List<String> lines = new java.util.ArrayList<>();
    String payloadPathToDelete = null;

    for (Scroll s : all) {
      if (s.id().equals(id)) {
        hit = true;
        payloadPathToDelete = s.filePath();
        continue;
      }
      lines.add(toTsv(s));
    }
    if (!hit) return false;

    try {
      java.nio.file.Path dir = dataFile.getParent();
      if (dir != null) java.nio.file.Files.createDirectories(dir);
      java.nio.file.Path tmp = dataFile.resolveSibling(dataFile.getFileName().toString() + ".tmp");
      java.nio.file.Files.write(
          tmp,
          lines,
          java.nio.charset.StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
      java.nio.file.Files.move(
          tmp,
          dataFile,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    } catch (java.io.IOException e) {
      return false;
    }

    if (payloadPathToDelete != null && !payloadPathToDelete.isBlank()) {
      try {
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(payloadPathToDelete));
      } catch (Exception ignore) {
      }
    }
    return true;
  }

  @Override
  public boolean update(Scroll updated) {
    java.util.List<Scroll> all = findAll();
    boolean hit = false;
    java.util.List<String> lines = new java.util.ArrayList<>();

    for (Scroll s : all) {
      if (s.id().equals(updated.id())) {
        lines.add(toTsv(updated));
        hit = true;
      } else {
        lines.add(toTsv(s));
      }
    }
    if (!hit) return false;

    try {
      java.nio.file.Path dir = dataFile.getParent();
      if (dir != null) java.nio.file.Files.createDirectories(dir);
      java.nio.file.Path tmp =
          dataFile.resolveSibling(dataFile.getFileName().toString() + ".tmp");
      java.nio.file.Files.write(tmp, lines, java.nio.charset.StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
      java.nio.file.Files.move(tmp, dataFile,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      return true;
    } catch (java.io.IOException e) {
      return false;
    }
  }

}
