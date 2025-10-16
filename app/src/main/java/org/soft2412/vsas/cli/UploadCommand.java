package org.soft2412.vsas.cli;

public final class UploadCommand implements Command {
  @Override
  public int run(String[] args) {
    System.err.println("upload: not implemented");
    return 2;
  }

  @Override
  public String name() {
    return "upload";
  }

  @Override
  public String description() {
    return "Upload a scroll";
  }
}
