package org.soft2412.vsas.cli;

import java.util.Arrays;

public final class ScrollCommand implements Command {

  @Override
  public int run(String[] args) {
    String[] safeArgs = args == null ? new String[0] : args;
    if (safeArgs.length == 0) {
      printUsage();
      return 2;
    }
    String sub = safeArgs[0];
    String[] rest = Arrays.copyOfRange(safeArgs, 1, safeArgs.length);

    switch (sub) {
      case "delete":
        return new ScrollDeleteSubcommand().run(rest);
      case "update":
        return new ScrollUpdateSubcommand().run(rest);
      default:
        System.err.println("Unknown subcommand: " + sub);
        printUsage();
        return 2;
    }
  }

  private void printUsage() {
    System.out.println("Usage:");
    System.out.println("  scroll delete --id <sid> [--yes]");
    System.out.println("  scroll update --id <sid> [--name \"<n>\"] [--file <path>] [--yes]");
  }

  @Override
  public String name() {
    return "scroll";
  }

  @Override
  public String description() {
    return "Manage scroll subcommands";
  }
}
