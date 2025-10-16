package org.soft2412.vsas.cli;

public final class RegisterCommand implements Command {
  @Override
  public int run(String[] args) {
    System.err.println("register: not implemented");
    return 2;
  }

  @Override
  public String name() {
    return "register";
  }

  @Override
  public String description() {
    return "Register a new user";
  }
}
