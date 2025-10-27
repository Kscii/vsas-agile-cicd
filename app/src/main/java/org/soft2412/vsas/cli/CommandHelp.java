package org.soft2412.vsas.cli;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Describes how to use a command. The information is consumed by the help command and other user
 * guidance features.
 */
final class CommandHelp {
  private final String usage;
  private final List<Flag> requiredFlags;
  private final List<Flag> optionalFlags;
  private final String example;
  private final Map<Integer, String> exitCodes;

  CommandHelp(
      String usage,
      List<Flag> requiredFlags,
      List<Flag> optionalFlags,
      String example,
      Map<Integer, String> exitCodes) {
    this.usage = Objects.requireNonNull(usage, "usage");
    this.requiredFlags = requiredFlags == null ? List.of() : List.copyOf(requiredFlags);
    this.optionalFlags = optionalFlags == null ? List.of() : List.copyOf(optionalFlags);
    this.example = example == null ? "" : example;
    this.exitCodes = exitCodes == null ? Map.of() : Map.copyOf(exitCodes);
  }

  String usage() {
    return usage;
  }

  List<Flag> requiredFlags() {
    return requiredFlags;
  }

  List<Flag> optionalFlags() {
    return optionalFlags;
  }

  String example() {
    return example;
  }

  Map<Integer, String> exitCodes() {
    return exitCodes;
  }

  static final class Flag {
    private final String label;
    private final String description;

    Flag(String label, String description) {
      this.label = Objects.requireNonNull(label, "label");
      this.description = description == null ? "" : description;
    }

    String label() {
      return label;
    }

    String description() {
      return description;
    }
  }
}
