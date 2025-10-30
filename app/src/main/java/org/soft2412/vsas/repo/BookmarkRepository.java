package org.soft2412.vsas.repo;

import java.util.List;
import org.soft2412.vsas.model.Bookmark;

public interface BookmarkRepository {
  boolean add(String userIdKey, String scrollId);

  boolean remove(String userIdKey, String scrollId);

  boolean exists(String userIdKey, String scrollId);

  List<Bookmark> listByUser(String userIdKey);
}
