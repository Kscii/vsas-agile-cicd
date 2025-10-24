package org.soft2412.vsas.repo;

import java.util.List;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;

public interface ScrollRepository {
  List<Scroll> findAll();

  Optional<Scroll> findById(String id);

  boolean existsId(String id);

  boolean save(Scroll scroll);

  boolean deleteById(String id);

  boolean update(Scroll updated);
  /** Increment the download counter for the given scroll id. */
  boolean incrementDownloadCount(String id);
}
