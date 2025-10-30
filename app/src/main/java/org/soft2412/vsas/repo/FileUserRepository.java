package org.soft2412.vsas.repo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.security.PasswordHasher;

public final class FileUserRepository implements UserRepository {

  private static final String[] HEADER =
      new String[] {
        "username", "email", "phone", "idKey", "role", "passwordHash", "salt", "createdAt"
      };
  private static final Pattern TAB_OR_NEWLINE = Pattern.compile("[\\t\\r\\n]");

  private static final Path DEFAULT_PATH = resolveDefaultUsersPath();

  private final Path usersPath;

  public FileUserRepository() {
    this(DEFAULT_PATH);
  }

  public FileUserRepository(Path usersPath) {
    this.usersPath = Objects.requireNonNull(usersPath, "usersPath");
  }

  @Override
  public Optional<User> findByUsername(String username) {
    if (isBlank(username)) return Optional.empty();
    try {
      return scanFirstMatch("username", username);
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<User> findByIdKey(String idKey) {
    if (isBlank(idKey)) return Optional.empty();
    try {
      return scanFirstMatch("idKey", idKey);
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  @Override
  public boolean existsIdKey(String idKey) {
    if (isBlank(idKey)) return false;
    try {
      if (!Files.exists(usersPath)) return false;
      try (BufferedReader br = Files.newBufferedReader(usersPath, StandardCharsets.UTF_8)) {
        String header = br.readLine();
        if (header == null) return false;
        int idxIdKey = indexOfCol(header.split("\t", -1), "idKey");
        if (idxIdKey < 0) return false;
        String line;
        while ((line = br.readLine()) != null) {
          String[] parts = line.split("\t", -1);
          if (parts.length <= idxIdKey) continue;
          if (idKey.equals(parts[idxIdKey])) return true;
        }
        return false;
      }
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public boolean save(User user) {
    if (user == null) return false;
    try {
      ensureHeader(usersPath);

      String row =
          String.join(
                  "\t",
                  sanitize(user.username()),
                  sanitize(user.email()),
                  sanitize(user.phone()),
                  sanitize(user.idKey()),
                  sanitize(defaultRole(user.role())),
                  nvl(user.passwordHash()),
                  nvl(user.salt()),
                  (user.createdAt() == null ? Instant.now() : user.createdAt()).toString())
              + "\n";

      Files.writeString(
          usersPath,
          row,
          StandardCharsets.UTF_8,
          StandardOpenOption.APPEND,
          StandardOpenOption.CREATE);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public boolean updateProfile(
      String username, String newEmail, String newPhone, char[] newPassword) {
    if (isBlank(username)) return false;
    Path temp = null;
    boolean updated = false;
    PasswordHasher hasher = new PasswordHasher();
    try {
      if (!Files.exists(usersPath)) {
        return false;
      }
      try (BufferedReader br = Files.newBufferedReader(usersPath, StandardCharsets.UTF_8)) {
        String header = br.readLine();
        if (header == null) {
          return false;
        }
        String[] cols = header.split("\t", -1);
        int iUser = indexOfCol(cols, "username");
        int iEmail = indexOfCol(cols, "email");
        int iPhone = indexOfCol(cols, "phone");
        int iHash = indexOfCol(cols, "passwordHash");
        int iSalt = indexOfCol(cols, "salt");
        if (iUser < 0 || iEmail < 0 || iPhone < 0 || iHash < 0 || iSalt < 0) {
          return false;
        }

        Path parent = usersPath.getParent();
        temp = Files.createTempFile(parent == null ? Path.of(".") : parent, "users-", ".tmp");
        try (BufferedWriter bw = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
          bw.write(header);
          bw.write("\n");

          String line;
          while ((line = br.readLine()) != null) {
            String[] rawParts = line.split("\t", -1);
            String[] parts =
                rawParts.length < cols.length ? Arrays.copyOf(rawParts, cols.length) : rawParts;

            if (username.equals(parts[iUser])) {
              if (newEmail != null) {
                parts[iEmail] = sanitize(newEmail);
              }
              if (newPhone != null) {
                parts[iPhone] = sanitize(newPhone);
              }
              if (newPassword != null) {
                byte[] salt = hasher.generateSalt(16);
                String hashHex = hasher.hashToHex(newPassword, salt);
                parts[iHash] = hashHex;
                parts[iSalt] = PasswordHasher.bytesToHex(salt);
              }
              updated = true;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cols.length; i++) {
              if (i > 0) sb.append('\t');
              String value = i < parts.length ? parts[i] : "";
              sb.append(value == null ? "" : value);
            }
            bw.write(sb.toString());
            bw.write("\n");
          }
        }
      }

      if (!updated) {
        if (temp != null) {
          Files.deleteIfExists(temp);
        }
        return false;
      }

      Files.move(temp, usersPath, StandardCopyOption.REPLACE_EXISTING);
      return true;
    } catch (IOException e) {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
        }
      }
      return false;
    }
  }

  @Override
  public boolean updateRole(String username, String newRole) {
    if (isBlank(username) || isBlank(newRole)) {
      return false;
    }
    String sanitizedRole = sanitize(newRole);
    if (isBlank(sanitizedRole)) {
      return false;
    }
    String normalisedRole = sanitizedRole.toUpperCase(Locale.ROOT);

    Path temp = null;
    boolean updated = false;
    try {
      if (!Files.exists(usersPath)) {
        return false;
      }

      try (BufferedReader br = Files.newBufferedReader(usersPath, StandardCharsets.UTF_8)) {
        String header = br.readLine();
        if (header == null) {
          return false;
        }
        String[] cols = header.split("\t", -1);
        int iUser = indexOfCol(cols, "username");
        int iRole = indexOfCol(cols, "role");
        if (iUser < 0 || iRole < 0) {
          return false;
        }

        Path parent = usersPath.getParent();
        temp = Files.createTempFile(parent == null ? Path.of(".") : parent, "users-", ".tmp");
        try (BufferedWriter bw = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
          bw.write(header);
          bw.write("\n");

          String line;
          while ((line = br.readLine()) != null) {
            String[] rawParts = line.split("\t", -1);
            String[] parts =
                rawParts.length < cols.length ? Arrays.copyOf(rawParts, cols.length) : rawParts;

            if (username.equals(parts[iUser])) {
              String existingRole = parts[iRole] == null ? "" : parts[iRole];
              if (!existingRole.equalsIgnoreCase(normalisedRole)) {
                parts[iRole] = normalisedRole;
                updated = true;
              }
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cols.length; i++) {
              if (i > 0) sb.append('\t');
              String value = i < parts.length ? parts[i] : "";
              sb.append(value == null ? "" : value);
            }
            bw.write(sb.toString());
            bw.write("\n");
          }
        }
      }

      if (!updated) {
        if (temp != null) {
          Files.deleteIfExists(temp);
        }
        return false;
      }

      Files.move(temp, usersPath, StandardCopyOption.REPLACE_EXISTING);
      return true;
    } catch (IOException e) {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
        }
      }
      return false;
    }
  }

  @Override
  public boolean deleteByUsername(String username) {
    if (isBlank(username)) {
      return false;
    }
    String target = sanitize(username);
    if (isBlank(target)) {
      return false;
    }

    Path temp = null;
    boolean deleted = false;
    try {
      if (!Files.exists(usersPath)) {
        return false;
      }
      try (BufferedReader br = Files.newBufferedReader(usersPath, StandardCharsets.UTF_8)) {
        String header = br.readLine();
        if (header == null) {
          return false;
        }
        String[] cols = header.split("\t", -1);
        int idxUsername = indexOfCol(cols, "username");
        if (idxUsername < 0) {
          return false;
        }

        Path parent = usersPath.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Path tempDir = parent != null ? parent : Path.of(".");
        temp = Files.createTempFile(tempDir, "users", ".tmp");

        try (BufferedWriter bw = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
          bw.write(header);
          bw.write(System.lineSeparator());

          String line;
          while ((line = br.readLine()) != null) {
            if (line.isEmpty()) {
              continue;
            }
            String[] parts = line.split("\t", -1);
            String rowUsername = parts.length > idxUsername ? parts[idxUsername] : "";
            if (rowUsername.equalsIgnoreCase(target)) {
              deleted = true;
              continue;
            }
            bw.write(line);
            bw.write(System.lineSeparator());
          }
        }
      }

      if (!deleted) {
        if (temp != null) {
          Files.deleteIfExists(temp);
        }
        return false;
      }

      Files.move(temp, usersPath, StandardCopyOption.REPLACE_EXISTING);
      return true;
    } catch (IOException e) {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
        }
      }
      return false;
    }
  }

  private Optional<User> scanFirstMatch(String colName, String value) throws IOException {
    if (!Files.exists(usersPath)) return Optional.empty();

    try (BufferedReader br = Files.newBufferedReader(usersPath, StandardCharsets.UTF_8)) {
      String header = br.readLine();
      if (header == null) return Optional.empty();

      String[] cols = header.split("\t", -1);
      int iUser = indexOfCol(cols, "username");
      int iEmail = indexOfCol(cols, "email");
      int iPhone = indexOfCol(cols, "phone");
      int iIdKey = indexOfCol(cols, "idKey");
      int iRole = indexOfCol(cols, "role");
      int iHash = indexOfCol(cols, "passwordHash");
      int iSalt = indexOfCol(cols, "salt");
      int iCreated = indexOfCol(cols, "createdAt");
      int iTarget = indexOfCol(cols, colName);

      if (iUser < 0
          || iEmail < 0
          || iPhone < 0
          || iIdKey < 0
          || iRole < 0
          || iHash < 0
          || iSalt < 0
          || iCreated < 0
          || iTarget < 0) {
        return Optional.empty();
      }

      String line;
      while ((line = br.readLine()) != null) {
        String[] p = line.split("\t", -1);
        if (p.length < cols.length) continue;
        if (value.equals(p[iTarget])) {
          Instant ts;
          try {
            ts = Instant.parse(p[iCreated]);
          } catch (Exception e) {
            ts = Instant.now();
          }
          User u =
              new User(p[iUser], p[iEmail], p[iPhone], p[iIdKey], p[iRole], p[iHash], p[iSalt], ts);
          return Optional.of(u);
        }
      }
      return Optional.empty();
    }
  }

  private static void ensureHeader(Path usersPath) throws IOException {
    Path dir = usersPath.getParent();
    if (dir != null && !Files.exists(dir)) Files.createDirectories(dir);

    if (!Files.exists(usersPath)) {
      String header = String.join("\t", HEADER) + "\n";
      Files.writeString(usersPath, header, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
  }

  private static String defaultRole(String role) {
    return (role == null || role.isBlank()) ? "USER" : role;
  }

  private static String sanitize(String s) {
    return s == null ? "" : TAB_OR_NEWLINE.matcher(s).replaceAll(" ").trim();
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }

  private static int indexOfCol(String[] headerCols, String target) {
    for (int i = 0; i < headerCols.length; i++) {
      if (target.equals(headerCols[i])) return i;
    }
    return -1;
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static Path resolveDefaultUsersPath() {
    String explicit = trimToNull(System.getProperty("vsas.users.path"));
    if (explicit != null) {
      return Path.of(explicit);
    }
    String dataDir = trimToNull(System.getProperty("vsas.data.dir"));
    if (dataDir != null) {
      return Path.of(dataDir).resolve("users.tsv");
    }
    Path cwd = Paths.get("").toAbsolutePath().normalize();
    if (cwd.getFileName() != null && "app".equals(cwd.getFileName().toString())) {
      Path parent = cwd.getParent();
      if (parent != null) {
        return parent.resolve("data").resolve("users.tsv");
      }
    }
    return Path.of("data").resolve("users.tsv");
  }

  private static String trimToNull(String v) {
    if (v == null) return null;
    String t = v.trim();
    return t.isEmpty() ? null : t;
  }
}
