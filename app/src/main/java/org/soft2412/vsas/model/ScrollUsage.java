package org.soft2412.vsas.model;

public final class ScrollUsage {

  private final String uploaderIdKey;
  private final long uploads;
  private final long downloads;

  public ScrollUsage(String uploaderIdKey, long uploads, long downloads) {
    this.uploaderIdKey = uploaderIdKey;
    this.uploads = uploads;
    this.downloads = downloads;
  }

  public String getUploaderIdKey() {
    return uploaderIdKey;
  }

  public long getUploads() {
    return uploads;
  }

  public long getDownloads() {
    return downloads;
  }
}
