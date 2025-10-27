package org.soft2412.vsas.cli;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class CommandRegistration {
  private final List<String> path;
  private final Function<CommandRegistry, Command> factory;
  private final String description;
  private final CommandHelp help;

  private CommandRegistration(
      List<String> path,
      Function<CommandRegistry, Command> factory,
      String description,
      CommandHelp help) {
    this.path = List.copyOf(path);
    this.factory = Objects.requireNonNull(factory, "factory");
    this.description = Objects.requireNonNull(description, "description");
    this.help = Objects.requireNonNull(help, "help");
  }

  static Builder builder(String... tokens) {
    return new Builder(tokens);
  }

  List<String> path() {
    return path;
  }

  int pathLength() {
    return path.size();
  }

  String canonicalName() {
    return String.join(" ", path);
  }

  String description() {
    return description;
  }

  CommandHelp help() {
    return help;
  }

  Command create(CommandRegistry registry) {
    return factory.apply(registry);
  }

  boolean matches(String[] args) {
    if (args == null || args.length < path.size()) {
      return false;
    }
    for (int i = 0; i < path.size(); i++) {
      if (!path.get(i).equalsIgnoreCase(args[i])) {
        return false;
      }
    }
    return true;
  }

  static final class Builder {
    private final List<String> path;
    private Function<CommandRegistry, Command> factory;
    private String description;
    private CommandHelp help;

    private Builder(String... tokens) {
      Objects.requireNonNull(tokens, "tokens");
      if (tokens.length == 0) {
        throw new IllegalArgumentException("Command path must contain at least one token");
      }
      this.path =
          List.of(tokens)
              .stream()
              .map(
                  token -> {
                    Objects.requireNonNull(token, "path token");
                    String trimmed = token.trim();
                    if (trimmed.isEmpty()) {
                      throw new IllegalArgumentException("Command path token cannot be blank");
                    }
                    return trimmed;
                  })
              .toList();
    }

    Builder factory(Function<CommandRegistry, Command> factory) {
      this.factory = Objects.requireNonNull(factory, "factory");
      return this;
    }

    Builder description(String description) {
      this.description = Objects.requireNonNull(description, "description");
      return this;
    }

    Builder help(CommandHelp help) {
      this.help = Objects.requireNonNull(help, "help");
      return this;
    }

    CommandRegistration build() {
      if (factory == null) {
        throw new IllegalStateException("Factory not set");
      }
      if (description == null) {
        throw new IllegalStateException("Description not set");
      }
      if (help == null) {
        throw new IllegalStateException("Help metadata not set");
      }
      return new CommandRegistration(path, factory, description, help);
    }
  }
}
