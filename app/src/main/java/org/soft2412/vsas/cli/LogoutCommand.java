package org.soft2412.vsas.cli;

import java.util.Objects;
import org.soft2412.vsas.service.SessionService;

public final class LogoutCommand implements Command {
  private final SessionService sessions;

  public LogoutCommand() {
    this(new SessionService());
  }

  LogoutCommand(SessionService sessions) {
    this.sessions = Objects.requireNonNull(sessions, "sessions");
  }

  @Override
  public int run(String[] args) {
    sessions.logout();
    System.out.println("Logout success");
    return 0;
  }

  @Override
  public String name() {
    return "logout";
  }

  @Override
  public String description() {
    return "Log out";
  }
}
