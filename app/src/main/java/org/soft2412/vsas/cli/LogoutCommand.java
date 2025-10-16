package org.soft2412.vsas.cli;

public final class LogoutCommand implements Command {
  @Override
  public int run(String[] args) {
    System.err.println("logout: not implemented");
    return 2;
  }

  @Override
  public String name() {
    return "logout";
  }

  @Override
  public String description() {
    return "Log out";
  }
}
