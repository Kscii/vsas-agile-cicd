package org.soft2412.vsas.service;

import java.util.Optional;
import org.soft2412.vsas.model.User;

public final class SessionService {
  public Optional<User> currentUser() {
    return Optional.empty();
  }

  public boolean login(String username, String password) {
    return false;
  }

  public void logout() {}
}
