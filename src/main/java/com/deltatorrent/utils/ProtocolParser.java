package com.deltatorrent.utils;

import com.deltatorrent.models.RegisterFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolParser {
  // To avoid errors during the update of the data
  // It allows to many threads update the list faster and safely
  private static final ConcurrentHashMap<String, List<RegisterFile>> files =
      new ConcurrentHashMap<>();
  // Chocking
  private static final ConcurrentHashMap<String, Integer> uploadHistory = new ConcurrentHashMap<>();
  // 512 KB
  private static final long SLICES_SIZE = 524288;

  public String treat(String rawMessage) {
    if (rawMessage == null || rawMessage.trim().isEmpty()) {
      return "ERROR|Empty message";
    }

    String[] parts = rawMessage.split("\\\\|");
    String action = parts[0];

    if (action.equalsIgnoreCase("REGISTER")) {
      // REGISTER|file_name|total_size|ip|port|STATUS(SEED/PEER)|[optinal_slices]
      String fileName = parts[1];
      long totalSize = Long.parseLong(parts[2]);
      String ip = parts[3];
      int port = Integer.parseInt(parts[4]);
      String status = parts[5];

      List<RegisterFile> nodes = files.computeIfAbsent(fileName, k -> new ArrayList<>());

      // Searchs if a node already exists, if not creates a new one
      RegisterFile register =
          nodes.stream()
              .filter(n -> n.getAddress().equals(ip + ":" + port))
              .findFirst()
              .orElse(null);

      if (register == null) {
        register = new RegisterFile(fileName, totalSize, ip, port);
        nodes.add(register);
      }

      if (status.equalsIgnoreCase("SEED")) {
        register.setIsSeed(true);
        // If the IP already exists, plus 5, if not it starts with 5 then
        uploadHistory.merge(ip, 5, Integer::sum);
      } else {
        uploadHistory.merge(ip, 1, Integer::sum);
        // If is a PEER, the command sends which slices it already has
        if (parts.length > 6) {
          String[] indexes = parts[6].split(";");

          for (String idx : indexes) {
            if (!idx.isEmpty()) {
              register.addSlice(Integer.parseInt(idx));
            }
          }
        }
      }

      return "REGISTER_OK";
    }

    // SEARCH|file_name|wanted_slice_index
    if (action.equalsIgnoreCase("SEARCH")) {
      String searchTerm = parts[1];
      int sliceWanted = (parts.length > 2) ? Integer.parseInt(parts[2]) : -1;
      // Recover the IP's node which is recovering a search
      String ipRequest = (parts.length > 3) ? parts[3] : "UNKNOWN";
      int score = uploadHistory.getOrDefault(ipRequest, 0);
      String answerPrefix = "RESULTS|";

      // If the node already download on the network but there's a zero score of uploads, it
      // receives a choke
      if (!ipRequest.equals("UNKNOWN") && score == 0) {
        // Instruction HOLD of 3000ms
        answerPrefix = "HOLD|3000|RESULTS|";
      }

      List<RegisterFile> nodesFound = files.get(searchTerm);

      if (nodesFound == null || nodesFound.isEmpty()) {
        return "RESULTS|NONE|SEEDS:0|PEERS:0";
      }

      long fileSize = nodesFound.get(0).getTotalSize();
      long totalNecessarySlices = (long) Math.ceil((double) fileSize / SLICES_SIZE);
      long seeds = nodesFound.stream().filter(RegisterFile::checkIsSeed).count();
      long peers = nodesFound.stream().filter(n -> !n.checkIsSeed()).count();

      if (sliceWanted == -1) {
        int[] abundance = new int[totalSlices];

        // Scan the swarm, counting how many nodes each of the pieces contains.
        for (RegisterFile node : nodesFound) {
          if (node.checkIsSeed()) {
            for (int i = 0; i < totalSlices; i++) {
              abundance[i]++;
            }
          } else {
            for (int i = 0; i < totalSlices; i++) {
              if (node.hasSlice(i)) {
                abundance[i]++;
              }
            }
          }
        }

        List<String> rarityMap = new ArrayList<>();

        for (int i = 0; i < totalSlices; i++) {
          rarityMap.add(i + ":" + abundance[i]);
        }

        return "RAREST_MAP|"
            + String.join(";", rarityMap)
            + "|SEEDS:"
            + seeds
            + "|PEERS:"
            + peers
            + "|CHUNKS:"
            + totalSlices
            + "|SIZE:"
            + fileSize;
      }

      List<String> sliceOwnersFound = new ArrayList<>();

      for (RegisterFile node : nodesFound) {
        // If is a generic search it returns everything, if is a slice, filters who owns the slice
        if (sliceWanted == -1 || node.hasSlice(sliceWanted)) {
          sliceOwnersFound.add(node.getAddress());
        }
      }

      return answerPrefix
          + String.join(";", sliceOwnersFound)
          + "|SEEDS:"
          + seeds
          + "|PEERS:"
          + peers
          + "|CHUNKS:"
          + totalNecessarySlices
          + "|SIZE:"
          + fileSize;
    }

    return "ERROR|Not a command";
  }

  public void clear() {
    files.clear();
    uploadHistory.clear();
  }
}
