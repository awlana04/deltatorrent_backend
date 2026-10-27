package com.deltatorrent.utils;

import com.deltatorrent.models.RegisterFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ProtocolParser {
  private static final ConcurrentHashMap<String, List<RegisterFile>> files =
      new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Integer> uploadHistory = new ConcurrentHashMap<>();
  private static final long SLICES_SIZE = 524288L;

  public String treat(String rawMessage) {
    if (rawMessage == null || rawMessage.trim().isEmpty()) {
      return "ERROR|Empty message";
    }

    String[] parts = rawMessage.split(java.util.regex.Pattern.quote("|"), -1);

    String action = parts[0].trim();

    if (action.equalsIgnoreCase("REGISTER")) {
      if (parts.length < 6) {
        return "ERROR|Invalid REGISTER format";
      }

      String fileName = parts[1].trim();

      if (fileName.isEmpty()) {
        return "ERROR|Empty file name";
      }

      long totalSize;
      int port;

      try {
        totalSize = Long.parseLong(parts[2].trim());
        port = Integer.parseInt(parts[4].trim());
      } catch (NumberFormatException e) {
        return "ERROR|Invalid size or port";
      }

      String ip = parts[3].trim();
      String status = parts[5].trim();

      if (totalSize < 0 || ip.isEmpty() || port < 1 || port > 65535) {
        return "ERROR|Invalid registration information";
      }

      if (!status.equalsIgnoreCase("SEED") && !status.equalsIgnoreCase("PEER")) {
        return "ERROR|Invalid peer status";
      }

      // Atualiza os peers de forma sincronizada por arquivo.
      List<RegisterFile> nodes = files.computeIfAbsent(fileName, key -> new ArrayList<>());

      synchronized (nodes) {
        // Procura o mesmo peer pelo endereço IP:porta.
        RegisterFile register =
            nodes.stream()
                .filter(node -> node.getAddress().equals(ip + ":" + port))
                .findFirst()
                .orElse(null);

        if (register == null) {
          register = new RegisterFile(fileName, totalSize, ip, port);

          nodes.add(register);
        }

        if (status.equalsIgnoreCase("SEED")) {
          register.setIsSeed(true);

          // O seed possui todas as fatias.
          uploadHistory.merge(ip, 5, Integer::sum);

        } else {
          uploadHistory.merge(ip, 1, Integer::sum);

          // Registra as fatias que o peer já possui.
          if (parts.length > 6 && !parts[6].isBlank()) {
            String[] indexes = parts[6].split(";");

            for (String index : indexes) {
              if (index.isBlank()) {
                continue;
              }

              try {
                int sliceIndex = Integer.parseInt(index.trim());

                if (sliceIndex >= 0) {
                  register.addSlice(sliceIndex);
                }
              } catch (NumberFormatException e) {
                return "ERROR|Invalid slice index";
              }
            }
          }
        }
      }

      System.out.println(
          "[TRACKER] Registered " + fileName + " at " + ip + ":" + port + " as " + status);

      return "REGISTER_OK";
    }

    if (action.equalsIgnoreCase("SEARCH")) {

      if (parts.length < 2 || parts[1].trim().isEmpty()) {
        return "ERROR|Invalid SEARCH format";
      }

      String searchTerm = parts[1].trim();

      int sliceWanted = -1;

      try {
        if (parts.length > 2 && !parts[2].isBlank()) {
          sliceWanted = Integer.parseInt(parts[2].trim());
        }
      } catch (NumberFormatException e) {
        return "ERROR|Invalid slice index";
      }

      if (sliceWanted < -1) {
        return "ERROR|Invalid slice index";
      }

      String requesterIP = parts.length > 3 && !parts[3].isBlank() ? parts[3].trim() : "UNKNOWN";

      int score = uploadHistory.getOrDefault(requesterIP, 0);

      String answerPrefix = "RESULTS|";

      // Mantém a lógica de choking existente.
      if (!requesterIP.equals("UNKNOWN") && score == 0) {
        answerPrefix = "HOLD|3000|RESULTS|";
      }

      List<RegisterFile> nodesFound = files.get(searchTerm);

      if (nodesFound == null || nodesFound.isEmpty()) {
        return "RESULTS|NONE|SEEDS:0|PEERS:0";
      }

      synchronized (nodesFound) {
        if (nodesFound.isEmpty()) {
          return "RESULTS|NONE|SEEDS:0|PEERS:0";
        }

        long fileSize = nodesFound.get(0).getTotalSize();

        int totalSlices = (int) (fileSize / SLICES_SIZE + (fileSize % SLICES_SIZE == 0 ? 0 : 1));

        long seeds = nodesFound.stream().filter(RegisterFile::checkIsSeed).count();

        long peers = nodesFound.stream().filter(node -> !node.checkIsSeed()).count();

        if (sliceWanted == -1) {

          int[] abundance = new int[totalSlices];

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

          // O FileLeecher atual baixa o arquivo inteiro
          // de um único peer. Por isso, selecionamos um seed.
          RegisterFile owner =
              nodesFound.stream().filter(RegisterFile::checkIsSeed).findFirst().orElse(null);

          StringBuilder response = new StringBuilder();

          response
              .append("RAREST_MAP|")
              .append(String.join(";", rarityMap))
              .append("|SEEDS:")
              .append(seeds)
              .append("|PEERS:")
              .append(peers)
              .append("|CHUNKS:")
              .append(totalSlices)
              .append("|SIZE:")
              .append(fileSize);

          if (owner != null) {
            String address = owner.getAddress();
            int separator = address.lastIndexOf(':');

            if (separator <= 0 || separator == address.length() - 1) {
              return "ERROR|Invalid peer address";
            }

            String ownerIP = address.substring(0, separator);

            int ownerPort;

            try {
              ownerPort = Integer.parseInt(address.substring(separator + 1));
            } catch (NumberFormatException e) {
              return "ERROR|Invalid peer port";
            }

            if (ownerPort < 1 || ownerPort > 65535) {
              return "ERROR|Invalid peer port";
            }

            response.append("|OWNER_IP:").append(ownerIP).append("|OWNER_PORT:").append(ownerPort);
          }

          return response.toString();
        }

        if (sliceWanted >= totalSlices) {
          return "RESULTS|NONE"
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
          if (node.checkIsSeed() || node.hasSlice(sliceWanted)) {
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
            + totalSlices
            + "|SIZE:"
            + fileSize;
      }
    }

    return "ERROR|Not a command";
  }

  public void clear() {
    files.clear();
    uploadHistory.clear();
  }
}
