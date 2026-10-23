package com.deltatorrent.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolParser {
  // To avoid errors during the update of the data
  // It allows to many threads update the list faster and safely
  private static final ConcurrentHashMap<String, List<String>> files = new ConcurrentHashMap<>();

  public String treat(String rawMessage) {
    if (rawMessage == null || rawMessage.trim().isEmpty()) {
      return "ERROR|Empty message";
    }

    String[] parts = rawMessage.split("\\|");
    String action = parts[0];

    if (action.equalsIgnoreCase("REGISTER")) {
      // REGISTER|owner|ip|port|file_name
      String ip = parts[2];
      String port = parts[3];
      String fileName = parts[4];
      String fullAddress = ip + ":" + port;

      files.computeIfAbsent(fileName, k -> new ArrayList<>()).add(fullAddress);
      return "REGISTER_OK";
    }

    if (action.equalsIgnoreCase("SEARCH")) {
      // SEARCH|search_term
      String searchTerm = parts[1];
      List<String> ownersFound = new ArrayList<>();

      for (String fileName : files.keySet()) {
        if (fileName.toLowerCase().contains(searchTerm.toLowerCase())) {
          ownersFound.addAll(files.get(fileName));
        }
      }

      if (ownersFound.isEmpty()) {
        return "RESULTS|NONE";
      }

      return "RESULTS|" + String.join(";", ownersFound);
    }

    return "ERROR|Not a command";
  }
}
