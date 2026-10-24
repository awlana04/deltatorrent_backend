package com.deltatorrent.models;

import java.util.HashSet;
import java.util.Set;

public class RegisterFile {
  private String fileName;
  private long totalSize;
  private String ownerIP;
  private int ownerPort;
  private Set<Integer> slicesOwned;
  private boolean isSeed;

  public RegisterFile(String fileName, long totalSize, String ownerIP, int ownerPort) {
    this.fileName = fileName;
    this.totalSize = totalSize;
    this.ownerIP = ownerIP;
    this.ownerPort = ownerPort;
    this.slicesOwned = new HashSet<>();
    this.isSeed = false;
  }

  public void addSlice(int index) {
    this.slicesOwned.add(index);
  }

  public void setIsSeed(boolean isSeed) {
    this.isSeed = isSeed;

    if (isSeed) {
      // If is seed, then it already has all the slices required
      this.slicesOwned.clear();
    }
  }

  public boolean hasSlice(int index) {
    return isSeed || slicesOwned.contains(index);
  }

  public String getAddress() {
    return ownerIP + ":" + ownerPort;
  }

  public String getFileName() {
    return fileName;
  }

  public long getTotalSize() {
    return totalSize;
  }

  public boolean checkIsSeed() {
    return isSeed;
  }

  public int getQuantityOfSlices() {
    return slicesOwned.size();
  }
}
