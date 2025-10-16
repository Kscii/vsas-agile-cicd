package org.soft2412.vsas.repo;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.soft2412.vsas.model.Scroll;

public final class FileScrollRepository implements ScrollRepository {
  public FileScrollRepository() {}

  @Override
  public List<Scroll> findAll() {
    return Collections.emptyList();
  }

  @Override
  public Optional<Scroll> findById(String id) {
    return Optional.empty();
  }

  @Override
  public boolean existsId(String id) {
    return false;
  }

  @Override
  public boolean save(Scroll scroll) {
    return false;
  }
}
