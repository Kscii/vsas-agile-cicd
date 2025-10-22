package org.soft2412.vsas.model;

public final class Scroll {
  private final String id;
  private final String name;
  private final String uploaderIdKey;
  private final String uploadDate;
  private final String filePath;
  private final long uploadCount;
  private final long downloadCount;

  public Scroll(
      String id,
      String name,
      String uploaderIdKey,
      String uploadDate,
      String filePath,
      long uploadCount,
      long downloadCount) {
    this.id = id;
    this.name = name;
    this.uploaderIdKey = uploaderIdKey;
    this.uploadDate = uploadDate;
    this.filePath = filePath;
    this.uploadCount = uploadCount;
    this.downloadCount = downloadCount;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String uploaderIdKey() {
    return uploaderIdKey;
  }

  public String uploadDate() {
    return uploadDate;
  }

  public String filePath() {
    return filePath;
  }

  public long uploadCount() {
    return uploadCount;
  }

  public long downloadCount() {
    return downloadCount;
  }
}
