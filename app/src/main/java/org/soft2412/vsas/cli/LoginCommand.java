package org.soft2412.vsas.cli;

public final class LoginCommand implements Command {
  @Override
  public int run(String[] args) {
    System.err.println("login: not implemented");
    return 2;
  }

  @Override
  public String name() {
    return "login";
  }

  @Override
  public String description() {
    return "Log in";
  }
}
