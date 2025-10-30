package org.soft2412.vsas.repo;

import java.util.Optional;
import org.soft2412.vsas.model.User;

public interface UserRepository {
  Optional<User> findByUsername(String username);

  Optional<User> findByIdKey(String idKey);

  boolean existsIdKey(String idKey);

  boolean save(User user);

  /**
   * Update the persisted profile for the given username.
   *
   * @param username the user to update (required)
   * @param newEmail optional new email (null to keep current)
   * @param newPhone optional new phone (null to keep current)
   * @param newPassword optional new password (null to keep current); if provided it will be hashed
   * @return {@code true} if the row was updated, {@code false} otherwise
   */
  boolean updateProfile(String username, String newEmail, String newPhone, char[] newPassword);

  /**
   * Update the role for the given username.
   *
   * @param username account identifier (required)
   * @param newRole new role value (required)
   * @return {@code true} if the persisted role was changed, {@code false} otherwise
   */
  boolean updateRole(String username, String newRole);

  /**
   * Deletes the first persisted user row matching the supplied username.
   *
   * @param username the username to delete
   * @return {@code true} if a row was removed, {@code false} otherwise
   */
  boolean deleteByUsername(String username);
}
