package org.soft2412.vsas.cli;

import org.soft2412.vsas.service.SessionService;

public final class WhoAmICommand implements Command {
  private final SessionService sessions = new SessionService();

  @Override
  public int run(String[] args) {
    return sessions
        .currentUser()
        .map(
            u -> {
              System.out.println(u.username() + " (role=" + u.role() + ")");
              return 0;
            })
        .orElseGet(
            () -> {
              System.out.println("guest");
              return 0;
            });
  }

  @Override
  public String name() {
    return "whoami";
  }

  @Override
  public String description() {
    return "Show current user or guest";
  }
}
