package org.soft2412.vsas.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class CommandRegistry {

  private final List<CommandRegistration> registrations;
  private final Map<String, CommandRegistration> byCanonicalName;

  private CommandRegistry(List<CommandRegistration> registrations) {
    this.registrations = List.copyOf(registrations);
    Map<String, CommandRegistration> byName = new LinkedHashMap<>();
    for (CommandRegistration registration : this.registrations) {
      String key = canonicalKey(registration.path());
      if (byName.putIfAbsent(key, registration) != null) {
        throw new IllegalArgumentException(
            "Duplicate command path: " + registration.canonicalName());
      }
    }
    this.byCanonicalName = Map.copyOf(byName);
  }

  static CommandRegistry withBuiltins() {
    Builder builder = new Builder();

    builder.register(
        CommandRegistration.builder("register")
            .factory(r -> new RegisterCommand())
            .description("Register a new user")
            .help(
                new CommandHelp(
                    "register --username <u> [--password <p>] --email <e> --phone <ph> --id-key <k> [--role <role>]",
                    List.of(
                        new CommandHelp.Flag("--username <u>", "Username to register"),
                        new CommandHelp.Flag("--email <e>", "Email address"),
                        new CommandHelp.Flag("--phone <ph>", "Phone number"),
                        new CommandHelp.Flag("--id-key <k>", "Unique identifier key")),
                    List.of(
                        new CommandHelp.Flag(
                            "--password <p>", "Optional plaintext password; prompted if omitted"),
                        new CommandHelp.Flag("--role <role>", "Optional role override")),
                    "register --username alice --password Secr3t! --email alice@example.com --phone 0400000000 --id-key U-100",
                    Map.of(
                        0, "success",
                        1, "validation error (duplicate id-key or password mismatch)",
                        2, "usage error (missing flags or prompt aborted)",
                        3, "I/O error (failed to persist user)")))
            .build());

    builder.register(
        CommandRegistration.builder("login")
            .factory(r -> new LoginCommand())
            .description("Log in with username and password")
            .help(
                new CommandHelp(
                    "login --username <u> [--password <p>]",
                    List.of(new CommandHelp.Flag("--username <u>", "Account username")),
                    List.of(
                        new CommandHelp.Flag(
                            "--password <p>",
                            "Optional plaintext password; prompted securely if omitted")),
                    "login --username alice --password Secr3t!",
                    Map.of(
                        0, "success",
                        1, "validation error (invalid credentials or empty password)",
                        2, "usage error (missing required flags)",
                        3, "I/O error (password prompt or session storage)")))
            .build());

    builder.register(
        CommandRegistration.builder("logout")
            .factory(r -> new LogoutCommand())
            .description("Log out of the current session")
            .help(new CommandHelp("logout", List.of(), List.of(), "logout", Map.of(0, "success")))
            .build());

    builder.register(
        CommandRegistration.builder("whoami")
            .factory(r -> new WhoAmICommand())
            .description("Show the current authenticated user or guest")
            .help(new CommandHelp("whoami", List.of(), List.of(), "whoami", Map.of(0, "success")))
            .build());

    builder.register(
        CommandRegistration.builder("list")
            .factory(r -> new ListCommand())
            .description("List scroll metadata with optional filters")
            .help(
                new CommandHelp(
                    "list [--uploader-id <id>] [--scroll-id <sid>] [--name <kw>] [--from <yyyy-MM-dd>] [--to <yyyy-MM-dd>]",
                    List.of(),
                    List.of(
                        new CommandHelp.Flag("--uploader-id <id>", "Filter by uploader id-key"),
                        new CommandHelp.Flag("--scroll-id <sid>", "Filter by scroll identifier"),
                        new CommandHelp.Flag("--name <kw>", "Substring match on scroll name"),
                        new CommandHelp.Flag("--from <yyyy-MM-dd>", "Filter uploads on/after date"),
                        new CommandHelp.Flag("--to <yyyy-MM-dd>", "Filter uploads on/before date")),
                    "list --uploader-id U-100 --from 2025-01-01",
                    Map.of(
                        0, "success",
                        2, "usage error (invalid flag values)",
                        3, "I/O error (failed to read scroll data)")))
            .build());

    builder.register(
        CommandRegistration.builder("upload")
            .factory(r -> new UploadCommand())
            .description("Upload a scroll binary and metadata")
            .help(
                new CommandHelp(
                    "upload --id <sid> --name <name> --file <path>",
                    List.of(
                        new CommandHelp.Flag("--id <sid>", "Unique scroll identifier"),
                        new CommandHelp.Flag("--name <name>", "Human readable name"),
                        new CommandHelp.Flag("--file <path>", "Path to the binary to upload")),
                    List.of(),
                    "upload --id S-001 --name \"Quarterly Report\" --file ./report.pdf",
                    Map.of(
                        0,
                        "success",
                        1,
                        "validation or permission error (login required, duplicate id, missing file)",
                        2,
                        "usage error (missing required flags)",
                        3,
                        "I/O error (copy failed or metadata persistence)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("download")
            .factory(r -> new DownloadCommand())
            .description("Download a scroll to the local filesystem")
            .help(
                new CommandHelp(
                    "download --id <sid> [--out <dir>]",
                    List.of(
                        new CommandHelp.Flag("--id <sid>", "Identifier of the scroll to download")),
                    List.of(
                        new CommandHelp.Flag(
                            "--out <dir>",
                            "Optional destination directory (defaults to current directory)")),
                    "download --id S-001 --out ./downloads",
                    Map.of(
                        0, "success",
                        1, "validation or permission error (login required or scroll missing)",
                        2, "usage error (missing flags or declined confirmation)",
                        3, "I/O error (read/write failure)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("bookmark")
            .factory(
                r ->
                    new Command() {
                      @Override
                      public int run(String[] args) {
                        if (args != null && args.length > 0) {
                          System.err.println(
                              "Unknown bookmark subcommand: " + String.join(" ", args));
                        }
                        System.out.println("Usage:");
                        System.out.println("  bookmark add --id <sid>");
                        System.out.println("  bookmark list");
                        System.out.println("  bookmark remove --id <sid> [--yes]");
                        return 2;
                      }

                      @Override
                      public String name() {
                        return "bookmark";
                      }

                      @Override
                      public String description() {
                        return "Manage personal scroll bookmarks";
                      }
                    })
            .description("Manage personal scroll bookmarks")
            .help(
                new CommandHelp(
                    "bookmark <subcommand>",
                    List.of(new CommandHelp.Flag("<subcommand>", "One of: add, list, remove")),
                    List.of(),
                    "bookmark add --id S-001",
                    Map.of(
                        0, "success (subcommand dependent)",
                        2, "usage error (missing or unknown subcommand)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("bookmark", "add")
            .factory(r -> new BookmarkAddCommand())
            .description("Add a bookmark for a scroll")
            .help(
                new CommandHelp(
                    "bookmark add --id <sid>",
                    List.of(new CommandHelp.Flag("--id <sid>", "Scroll identifier to bookmark")),
                    List.of(),
                    "bookmark add --id S-001",
                    Map.of(
                        0, "success (including already bookmarked)",
                        1, "validation or permission error (login required or scroll missing)",
                        2, "usage error (missing flags or unknown option)",
                        3, "I/O error (failed to persist bookmark)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("bookmark", "list")
            .factory(r -> new BookmarkListCommand())
            .description("List bookmarks for the current user")
            .help(
                new CommandHelp(
                    "bookmark list",
                    List.of(),
                    List.of(),
                    "bookmark list",
                    Map.of(0, "success", 1, "permission error (login required)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("bookmark", "remove")
            .factory(r -> new BookmarkRemoveCommand())
            .description("Remove a bookmark")
            .help(
                new CommandHelp(
                    "bookmark remove --id <sid> [--yes]",
                    List.of(new CommandHelp.Flag("--id <sid>", "Scroll identifier to remove")),
                    List.of(new CommandHelp.Flag("--yes", "Skip the confirmation prompt")),
                    "bookmark remove --id S-001 --yes",
                    Map.of(
                        0, "success (including aborted confirmation)",
                        1, "validation or permission error (login required or not bookmarked)",
                        2, "usage error (missing flags or unknown option)",
                        3, "I/O error (failed to persist bookmark removal)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("scroll")
            .factory(r -> new ScrollCommand())
            .description("Manage scroll lifecycle commands")
            .help(
                new CommandHelp(
                    "scroll <subcommand> [options]",
                    List.of(new CommandHelp.Flag("<subcommand>", "One of: delete, update")),
                    List.of(
                        new CommandHelp.Flag(
                            "delete",
                            "Delete a scroll you uploaded: scroll delete --id <sid> [--yes]"),
                        new CommandHelp.Flag(
                            "update",
                            "Update metadata or payload: scroll update --id <sid> [--name \"<n>\"] [--file <path>] [--yes]")),
                    "scroll delete --id S-001",
                    Map.of(
                        0, "success (subcommand dependent)",
                        2, "usage error (missing or unknown subcommand)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("scroll", "delete")
            .factory(
                r ->
                    new Command() {
                      @Override
                      public int run(String[] args) {
                        return new ScrollDeleteSubcommand().run(args);
                      }

                      @Override
                      public String name() {
                        return "scroll delete";
                      }

                      @Override
                      public String description() {
                        return "Delete a scroll you uploaded";
                      }
                    })
            .description("Delete a scroll you uploaded")
            .help(
                new CommandHelp(
                    "scroll delete --id <sid> [--yes]",
                    List.of(
                        new CommandHelp.Flag("--id <sid>", "Identifier of the scroll to delete")),
                    List.of(new CommandHelp.Flag("--yes", "Skip the confirmation prompt")),
                    "scroll delete --id S-001 --yes",
                    Map.of(
                        0, "success",
                        1, "validation or permission error (login required or forbidden)",
                        2, "usage error (missing flags or unknown option)",
                        3, "I/O error (persistence failure)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("scroll", "update")
            .factory(
                r ->
                    new Command() {
                      @Override
                      public int run(String[] args) {
                        return new ScrollUpdateSubcommand().run(args);
                      }

                      @Override
                      public String name() {
                        return "scroll update";
                      }

                      @Override
                      public String description() {
                        return "Update scroll metadata or file";
                      }
                    })
            .description("Update scroll metadata or file")
            .help(
                new CommandHelp(
                    "scroll update --id <sid> [--name \"<n>\"] [--file <path>] [--yes]",
                    List.of(
                        new CommandHelp.Flag("--id <sid>", "Identifier of the scroll to update")),
                    List.of(
                        new CommandHelp.Flag("--name \"<n>\"", "New scroll name"),
                        new CommandHelp.Flag("--file <path>", "Replace scroll binary"),
                        new CommandHelp.Flag("--yes", "Skip confirmation prompts")),
                    "scroll update --id S-001 --name \"Updated Name\"",
                    Map.of(
                        0, "success",
                        1, "validation or permission error (login required or forbidden)",
                        2, "usage error (missing args or unknown option)",
                        3, "I/O error (file copy or persistence failure)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("admin", "users", "delete")
            .factory(r -> new AdminUsersDeleteCommand())
            .description("Delete a user account (admin only)")
            .help(
                new CommandHelp(
                    "admin users delete --username <u> [--yes]",
                    List.of(
                        new CommandHelp.Flag(
                            "--username <u>", "Username of the account to delete")),
                    List.of(
                        new CommandHelp.Flag(
                            "--yes", "Skip confirmation only when --username is provided")),
                    "admin users delete --username alice --yes",
                    Map.of(
                        0,
                        "success",
                        1,
                        "permission or validation error (requires admin, target missing, owns scrolls, or self-delete)",
                        2,
                        "usage error (missing flags or unknown option)",
                        3,
                        "I/O error (prompt or persistence failure)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("admin", "users", "role")
            .factory(r -> new AdminUsersRoleCommand())
            .description("Update a user's role (admin only)")
            .help(
                new CommandHelp(
                    "admin users role --username <u> --role admin|user",
                    List.of(
                        new CommandHelp.Flag(
                            "--username <u>", "Username of the account to update")),
                    List.of(
                        new CommandHelp.Flag(
                            "--role <role>",
                            "New role (admin or user); prompted if the flag is omitted")),
                    "admin users role --username alice --role admin",
                    Map.of(
                        0, "success",
                        1, "permission or validation error (requires admin or unknown user)",
                        2, "usage error (missing or invalid flags)",
                        3, "I/O error (prompt or persistence failure)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("admin", "users", "list")
            .factory(r -> new AdminUsersListCommand())
            .description("List users (admin only)")
            .help(
                new CommandHelp(
                    "admin users list [--username <u>] [--id-key <k>] [--role admin|user]",
                    List.of(),
                    List.of(
                        new CommandHelp.Flag("--username <u>", "Filter by username (exact match)"),
                        new CommandHelp.Flag("--id-key <k>", "Filter by idKey (exact match)"),
                        new CommandHelp.Flag("--role <role>", "Filter by role (admin|user)")),
                    "admin users list --role admin",
                    Map.of(
                        0, "success",
                        1, "permission or validation error (requires admin or invalid role)",
                        2, "usage error (missing values or unknown option)",
                        3, "I/O error (failed to read users.tsv)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("preview")
            .factory(r -> new PreviewCommand())
            .description("Preview metadata and a snippet of a scroll")
            .help(
                new CommandHelp(
                    "preview --id <sid>",
                    List.of(
                        new CommandHelp.Flag("--id <sid>", "Identifier of the scroll to preview")),
                    List.of(),
                    "preview --id S-001",
                    Map.of(
                        0, "success",
                        1, "validation error (unknown scroll)",
                        2, "usage error (missing or invalid flags)",
                        3, "I/O error (reading scroll data)")))
            .build());

    builder.register(
        CommandRegistration.builder("profile")
            .factory(r -> new ProfileUpdateCommand())
            .description("Manage profile details")
            .help(
                new CommandHelp(
                    "profile update [--email <e>] [--phone <ph>] [--password]",
                    List.of(
                        new CommandHelp.Flag("update", "Required subcommand to modify profile")),
                    List.of(
                        new CommandHelp.Flag("--email <e>", "New email address"),
                        new CommandHelp.Flag("--phone <ph>", "New phone number"),
                        new CommandHelp.Flag("--password", "Prompt for a new password")),
                    "profile update --email new@example.com --phone 0400000000",
                    Map.of(
                        0,
                        "success",
                        1,
                        "validation or permission error (login required or mismatched passwords)",
                        2,
                        "usage error (missing subcommand or flags)",
                        3,
                        "I/O error (password prompt or repository failure)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("profile", "update")
            .factory(
                r ->
                    new Command() {
                      private final ProfileUpdateCommand delegate = new ProfileUpdateCommand();

                      @Override
                      public int run(String[] args) {
                        String[] withSubcommand;
                        if (args == null || args.length == 0) {
                          withSubcommand = new String[] {"update"};
                        } else {
                          withSubcommand = new String[args.length + 1];
                          withSubcommand[0] = "update";
                          System.arraycopy(args, 0, withSubcommand, 1, args.length);
                        }
                        return delegate.run(withSubcommand);
                      }

                      @Override
                      public String name() {
                        return "profile update";
                      }

                      @Override
                      public String description() {
                        return "Update profile contact details or password";
                      }
                    })
            .description("Update profile contact details or password")
            .help(
                new CommandHelp(
                    "profile update [--email <e>] [--phone <ph>] [--password]",
                    List.of(),
                    List.of(
                        new CommandHelp.Flag("--email <e>", "New email address"),
                        new CommandHelp.Flag("--phone <ph>", "New phone number"),
                        new CommandHelp.Flag("--password", "Prompt for a new password")),
                    "profile update --password",
                    Map.of(
                        0,
                        "success",
                        1,
                        "validation or permission error (login required or mismatched passwords)",
                        2,
                        "usage error (missing fields)",
                        3,
                        "I/O error (password prompt or repository failure)")))
            .access(CommandRegistration.Access.AUTHENTICATED)
            .build());

    builder.register(
        CommandRegistration.builder("help")
            .factory(HelpCommand::new)
            .description("Show help for commands")
            .help(
                new CommandHelp(
                    "help [<command> [<subcommand>...]]",
                    List.of(),
                    List.of(
                        new CommandHelp.Flag(
                            "<command>",
                            "Optional command path such as 'upload' or 'scroll delete'")),
                    "help upload",
                    Map.of(
                        0, "success",
                        1, "validation error (requested command not found)",
                        2, "usage error (invalid arguments)")))
            .build());

    return builder.build();
  }

  Optional<CommandResolution> resolve(String[] args) {
    if (args == null || args.length == 0) {
      return Optional.empty();
    }
    CommandRegistration best = null;
    for (CommandRegistration registration : registrations) {
      if (registration.matches(args)) {
        if (best == null || registration.pathLength() > best.pathLength()) {
          best = registration;
        }
      }
    }
    if (best == null) {
      return Optional.empty();
    }
    String[] remaining = Arrays.copyOfRange(args, best.pathLength(), args.length);
    return Optional.of(new CommandResolution(best, remaining));
  }

  Optional<CommandRegistration> findByCanonicalName(String canonical) {
    if (canonical == null) {
      return Optional.empty();
    }
    String trimmed = canonical.trim();
    if (trimmed.isEmpty()) {
      return Optional.empty();
    }
    return findByTokens(Arrays.asList(trimmed.split("\\s+")));
  }

  Optional<CommandRegistration> findByTokens(List<String> tokens) {
    if (tokens == null || tokens.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byCanonicalName.get(canonicalKey(tokens)));
  }

  List<CommandRegistration> commands() {
    return registrations;
  }

  private static String canonicalKey(List<String> tokens) {
    List<String> normalised = new ArrayList<>(tokens.size());
    for (String token : tokens) {
      String trimmed = Objects.requireNonNull(token, "token").trim();
      if (trimmed.isEmpty()) {
        throw new IllegalArgumentException("Command token cannot be blank");
      }
      normalised.add(trimmed.toLowerCase(Locale.ROOT));
    }
    return String.join(" ", normalised);
  }

  static final class Builder {
    private final List<CommandRegistration> entries = new ArrayList<>();

    Builder register(CommandRegistration registration) {
      entries.add(Objects.requireNonNull(registration, "registration"));
      return this;
    }

    CommandRegistry build() {
      return new CommandRegistry(entries);
    }
  }
}
