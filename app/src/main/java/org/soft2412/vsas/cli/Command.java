package org.soft2412.vsas.cli;

public interface Command {
  int run(String[] args);

  String name();

  String description();
}
