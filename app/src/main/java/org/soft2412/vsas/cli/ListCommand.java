package org.soft2412.vsas.cli;

import java.util.List;
import org.soft2412.vsas.model.Scroll;
import org.soft2412.vsas.repo.FileScrollRepository;
import org.soft2412.vsas.repo.ScrollRepository;

public final class ListCommand implements Command {
  private final ScrollRepository scrolls = new FileScrollRepository();

  @Override
  public int run(String[] args) {
    List<Scroll> all = scrolls.findAll();
    if (all.isEmpty()) {
      System.out.println("no scrolls");
      return 0;
    }

    System.out.println("id | name | uploader | uploadDate");
    for (Scroll s : all) {
      System.out.println(
          s.id() + " | " + s.name() + " | " + s.uploaderIdKey() + " | " + s.uploadDate());
    }
    return 0;
  }

  @Override
  public String name() {
    return "list";
  }

  @Override
  public String description() {
    return "List scrolls";
  }
}
