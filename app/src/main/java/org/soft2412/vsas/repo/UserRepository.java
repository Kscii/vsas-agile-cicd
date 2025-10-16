package org.soft2412.vsas.repo;

import java.util.Optional;
import org.soft2412.vsas.model.User;

public interface UserRepository {
  Optional<User> findByUsername(String username);

  Optional<User> findByIdKey(String idKey);

  boolean existsIdKey(String idKey);

  boolean save(User user);
}
