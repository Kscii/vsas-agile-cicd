package org.soft2412.vsas.cli;

import java.util.Arrays;

public final class CommandDispatcher {
  public int dispatch(String[] args) {
    if (args == null || args.length == 0) {
      System.out.println("Usage: vsas <command> [options]");
      System.out.println("Commands: register, login, logout, whoami, list, upload");
      // Return success for help/usage to allow `gradlew run` without args
      return 0;
    }
    String cmd = args[0];
    String[] opts = Arrays.copyOfRange(args, 1, args.length);
    switch (cmd) {
      case "register":
        return new RegisterCommand().run(opts);
      case "login":
        return new LoginCommand().run(opts);
      case "logout":
        return new LogoutCommand().run(opts);
      case "whoami":
        return new WhoAmICommand().run(opts);
      case "list":
        return new ListCommand().run(opts);
      case "upload":
        return new UploadCommand().run(opts);
      default:
        System.err.println("Unknown command: " + cmd);
        return 2;
    }
  }
}
