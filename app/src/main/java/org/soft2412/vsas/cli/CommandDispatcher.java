package org.soft2412.vsas.cli;

import java.util.Objects;

public final class CommandDispatcher {
  private final CommandRegistry registry;

  public CommandDispatcher() {
    this(CommandRegistry.withBuiltins());
  }

  CommandDispatcher(CommandRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  public int dispatch(String[] args) {
    if (args == null || args.length == 0) {
      return registry
          .findByCanonicalName("help")
          .map(reg -> reg.create(registry).run(new String[0]))
          .orElse(0);
    }

    return registry
        .resolve(args)
        .map(
            resolution ->
                resolution.registration().create(registry).run(resolution.remainingArgs()))
        .orElseGet(
            () -> {
              System.err.println("Unknown command: " + String.join(" ", args));
              return 2;
            });
  }
}
