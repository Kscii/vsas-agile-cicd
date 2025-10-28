package org.soft2412.vsas.cli;

import java.util.Arrays;
import java.util.Objects;

final class CommandResolution {
  private final CommandRegistration registration;
  private final String[] remainingArgs;

  CommandResolution(CommandRegistration registration, String[] remainingArgs) {
    this.registration = Objects.requireNonNull(registration, "registration");
    this.remainingArgs =
        remainingArgs == null ? new String[0] : Arrays.copyOf(remainingArgs, remainingArgs.length);
  }

  CommandRegistration registration() {
    return registration;
  }

  String[] remainingArgs() {
    return Arrays.copyOf(remainingArgs, remainingArgs.length);
  }
}
