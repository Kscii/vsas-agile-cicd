package org.soft2412.vsas.cli;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HelpCommand implements Command {
  private final CommandRegistry registry;
  private final PrintStream out;
  private final PrintStream err;

  public HelpCommand(CommandRegistry registry) {
    this(registry, System.out, System.err);
  }

  HelpCommand(CommandRegistry registry, PrintStream out, PrintStream err) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
  }

  @Override
  public int run(String[] args) {
    if (args == null || args.length == 0) {
      printSummary();
      return 0;
    }
    List<String> tokens = Arrays.asList(args);
    return registry
        .findByTokens(tokens)
        .map(
            registration -> {
              printDetail(registration);
              return 0;
            })
        .orElseGet(
            () -> {
              err.println("Unknown command: " + String.join(" ", tokens));
              return 1;
            });
  }

  private void printSummary() {
    out.println("Available commands:");
    for (CommandRegistration registration : registry.commands()) {
      if (registration.pathLength() == 1) {
        out.printf("  %s - %s%n", registration.canonicalName(), registration.description());
      }
    }
  }

  private void printDetail(CommandRegistration registration) {
    CommandHelp help = registration.help();
    out.printf("Command: %s%n", registration.canonicalName());
    out.printf("Description: %s%n", registration.description());
    out.printf("Usage: %s%n", help.usage());

    if (!help.requiredFlags().isEmpty()) {
      out.println("Required flags:");
      for (CommandHelp.Flag flag : help.requiredFlags()) {
        out.printf("  %s%s%n", flag.label(), format(flag.description()));
      }
    }

    if (!help.optionalFlags().isEmpty()) {
      out.println("Optional flags:");
      for (CommandHelp.Flag flag : help.optionalFlags()) {
        out.printf("  %s%s%n", flag.label(), format(flag.description()));
      }
    }

    if (!help.exitCodes().isEmpty()) {
      out.println("Exit codes:");
      help.exitCodes()
          .entrySet()
          .stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(entry -> out.printf("  %d -> %s%n", entry.getKey(), entry.getValue()));
    }

    if (!help.example().isBlank()) {
      out.println("Example:");
      out.printf("  %s%n", help.example());
    }
  }

  private String format(String description) {
    if (description == null || description.isBlank()) {
      return "";
    }
    return " : " + description;
  }

  @Override
  public String name() {
    return "help";
  }

  @Override
  public String description() {
    return "Show help for commands";
  }
}
