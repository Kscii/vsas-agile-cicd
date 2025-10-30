package org.soft2412.vsas.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.model.User;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;
import org.soft2412.vsas.service.SessionService;

public final class ScrollUpdateSubcommand {

  public int run(String[] args) {
    String[] safeArgs = args == null ? new String[0] : args;

    SessionService session = new SessionService();
    Optional<User> userOpt = session.currentUser();
    if (userOpt.isEmpty()) {
      System.err.println("Forbidden: please login first.");
      return 1;
    }
    String requesterIdKey = userOpt.get().idKey();

    String id = null;
    String newName = null;
    String newFilePath = null;
    boolean yes = false;

    for (int i = 0; i < safeArgs.length; i++) {
      String a = safeArgs[i];
      switch (a) {
        case "--id":
          if (i + 1 >= safeArgs.length) {
            System.err.println("Missing value for --id");
            return 2;
          }
          id = safeArgs[++i].trim();
          break;
        case "--name":
          if (i + 1 >= safeArgs.length) {
            System.err.println("Missing value for --name");
            return 2;
          }
          newName = safeArgs[++i];
          break;
        case "--file":
          if (i + 1 >= safeArgs.length) {
            System.err.println("Missing value for --file");
            return 2;
          }
          newFilePath = safeArgs[++i];
          break;
        case "--yes":
          yes = true;
          break;
        default:
          System.err.println("Unknown option: " + a);
          return 2;
      }
    }

    if (id == null || id.isEmpty()) {
      System.err.println(
          "Usage: scroll update --id <sid> [--name \"<n>\"] [--file <path>] [--yes]");
      return 2;
    }
    if ((newName == null || newName.isBlank()) && (newFilePath == null || newFilePath.isBlank())) {
      System.err.println("Nothing to update. Provide --name and/or --file");
      return 2;
    }

    ScrollRepository repo = new FileScrollRepository();
    Optional<Scroll> os = repo.findById(id);
    if (os.isEmpty()) {
      System.err.println("Not found: " + id);
      return 1;
    }
    Scroll old = os.get();
    if (!old.uploaderIdKey().equals(requesterIdKey)) {
      System.err.println("Forbidden: only the uploader can update this scroll.");
      return 1;
    }

    String finalPayloadPath = old.filePath();
    boolean fileChanged = false;

    if (newFilePath != null && !newFilePath.isBlank()) {
      Path src = Path.of(newFilePath);
      if (!Files.isRegularFile(src)) {
        System.err.println("File not found: " + newFilePath);
        return 1;
      }
      if (!yes) {
        System.out.print("Replace existing file? [y/n] ");
        BufferedReader br =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        try {
          line = br.readLine();
        } catch (IOException e) {
          System.err.println("I/O error: " + e.getMessage());
          return 3;
        }
        String ans = line == null ? "" : line.trim().toLowerCase();
        if (!(ans.equals("y") || ans.equals("yes"))) {
          System.out.println("Aborted.");
          return 0;
        }
      }
      Path dst = Path.of(finalPayloadPath);
      Path tmp = dst.resolveSibling(dst.getFileName().toString() + ".tmp");
      try {
        Files.createDirectories(dst.getParent());
        Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        fileChanged = true;
      } catch (IOException e) {
        System.err.println("Failed to replace payload: " + e.getMessage());
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException ignore) {
        }
        return 3;
      }
    }

    long newUploadCount = old.uploadCount();
    if (fileChanged) {
      newUploadCount = newUploadCount + 1;
    }

    String name = (newName != null && !newName.isBlank()) ? newName : old.name();
    Scroll updated =
        new Scroll(
            old.id(),
            name,
            old.uploaderIdKey(),
            old.uploadDate(),
            finalPayloadPath,
            newUploadCount,
            old.downloadCount());

    boolean ok = repo.update(updated);
    if (!ok) {
      System.err.println("Update failed (persistence).");
      return 3;
    }

    System.out.println("Updated: " + id);
    return 0;
  }
}
