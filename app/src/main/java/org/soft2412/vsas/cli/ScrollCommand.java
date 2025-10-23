package org.soft2412.vsas.cli;

import java.util.Arrays;

public final class ScrollCommand {

  public int run(String[] args) {
    if (args.length == 0) {
      printUsage();
      return 2;
    }
    String sub = args[0];
    String[] rest = Arrays.copyOfRange(args, 1, args.length);

    switch (sub) {
      case "delete":
        return new ScrollDeleteSubcommand().run(rest);
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
}
