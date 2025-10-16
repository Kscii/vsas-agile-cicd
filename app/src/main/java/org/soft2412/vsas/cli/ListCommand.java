package org.soft2412.vsas.cli;

public final class ListCommand implements Command {
  @Override
  public int run(String[] args) {
    System.err.println("list: not implemented");
    return 2;
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
