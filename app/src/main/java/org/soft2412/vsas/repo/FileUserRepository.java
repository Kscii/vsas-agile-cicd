package org.soft2412.vsas.repo;

import java.util.Optional;
import org.soft2412.vsas.model.User;

public final class FileUserRepository implements UserRepository {
  public FileUserRepository() {}

  @Override
  public Optional<User> findByUsername(String username) {
    return Optional.empty();
  }

  @Override
  public Optional<User> findByIdKey(String idKey) {
    return Optional.empty();
  }

  @Override
  public boolean existsIdKey(String idKey) {
    return false;
  }

  @Override
  public boolean save(User user) {
    return false;
  }
}
