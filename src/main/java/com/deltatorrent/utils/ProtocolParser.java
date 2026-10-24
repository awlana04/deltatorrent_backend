package com.deltatorrent.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.deltatorrent.models.RegisterFile;

public class ProtocolParser {
  // To avoid errors during the update of the data
  // It allows to many threads update the list faster and safely
  private static final ConcurrentHashMap<String, List<RegisterFile>> files = new ConcurrentHashMap<>();
  // 512 KB
  private static final long SLICES_SIZE = 524288;

  public String treat(String rawMessage) {
    if (rawMessage == null || rawMessage.trim().isEmpty()) {
      return "ERROR|Empty message";
    }

    String[] parts = rawMessage.split("\\|");
    String action = parts[0];

    // 1. COMANDO REGISTER (Anunciar posse parcial ou total)
            // Formato: REGISTER|nome_filme|tamanho_total|ip|porta|STATUS(SEED/PEER)|[pedacos_opcionais]
    if (action.equalsIgnoreCase("REGISTER")) {
      // REGISTER|owner|ip|port|file_name
      String fileName = parts[1];
      long totalSize = Long.parseLong(parts[2]);
      String ip = parts[3];
      int port = Integer.parseInt(parts[4]);
      String status = parts[5];
      // String fullAddress = ip + ":" + port;

      List<RegisterFile> nodes = files.computeIfAbsent(fileName, k -> new ArrayList<>());

      // Searchs if a node already exists, if not creates a new one
      RegisterFile register = nodes.stream().filter(n -> n.getAddress().equals(ip + ":" + port)).findFirst().orElse(null);

      if (register == null) {
        register = new RegisterFile(fileName, totalSize, ip, port);
        nodes.add(register);
      }

      if (status.equalsIgnoreCase("SEED")) {
        register.setIsSeed(true);
      } else {
        // Se for PEER, o comando envia quais pedaços ele obteve: ex: REGISTER|...|PEER|0;1;2
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

    // 2. COMANDO SEARCH (Saber métricas de Seeds/Peers e locais de download)
            // Formato: SEARCH|nome_filme|indice_pedaco_desejado
    if (action.equalsIgnoreCase("SEARCH")) {
      // SEARCH|search_term
      String searchTerm = parts[1];
      int sliceWanted = (parts.length > 2) ? Integer.parseInt(parts[2]) : -1;

      List<RegisterFile> nodesFound = files.get(searchTerm);

      if (nodesFound == null || nodesFound.isEmpty()) {
        return "RESULTS|NONE|SEEDS:0|PEERS:0";
      }

      long seeds = nodesFound.stream().filter(RegisterFile::checkIsSeed).count();
      long peers = nodesFound.stream().filter(n -> !n.checkIsSeed()).count();
      long fileSize = nodesFound.get(0).getTotalSize();
      long totalNecessarySlices = (long) Math.ceil((double) fileSize / SLICES_SIZE);

      List<String> sliceOwnersFound = new ArrayList<>();

      for (RegisterFile node : nodesFound) {
        // Se a busca for genérica (-1) manda todo mundo, se for por pedaço, filtra quem tem a fatia
        if (sliceWanted == -1 || node.hasSlice(sliceWanted)) {
          sliceOwnersFound.add(node.getAddress());
        }
      }

      return "RESULTS|" + String.join(";", sliceOwnersFound) +
                         "|SEEDS:" + seeds + "|PEERS:" + peers +
                         "|CHUNKS:" + totalNecessarySlices + "|SIZE:" + fileSize;

//       for (String fileName : files.keySet()) {
//         if (fileName.toLowerCase().contains(searchTerm.toLowerCase())) {
//           ownersFound.addAll(files.get(fileName));
//         }
//       }
//
//       if (ownersFound.isEmpty()) {
//         return "RESULTS|NONE";
//       }
//
//       return "RESULTS|" + String.join(";", ownersFound);
    }

    return "ERROR|Not a command";
  }

  public void clear () {
    files.clear();
  }
}
