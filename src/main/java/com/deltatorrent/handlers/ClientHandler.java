package com.deltatorrent.handlers;

import com.deltatorrent.utils.ProtocolParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

  private final Socket clientSocket;
  private final ProtocolParser parser;

  public ClientHandler(Socket socket, ProtocolParser parser) {
    this.clientSocket = socket;
    this.parser = parser;
  }

  @Override
  public void run() {
    String clientIP = clientSocket.getInetAddress().getHostAddress();

    System.out.println(
        "[THREAD " + Thread.currentThread().getId() + "] Treating client: " + clientIP);

    try (BufferedReader input =
            new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter output =
            new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8)) {
      String rawMessage;

      while ((rawMessage = input.readLine()) != null) {
        System.out.println("[RECEIVED from " + clientIP + "]: " + rawMessage);

        if (rawMessage.equalsIgnoreCase("QUIT")) {
          output.println("BYE|Connection exited with success.");
          output.flush();
          break;
        }

        try {
          String answer = parser.treat(rawMessage);

          System.out.println("[RESPONSE to " + clientIP + "]: " + answer);

          output.println(answer);
          output.flush();

          if (output.checkError()) {
            System.err.println("[ERROR] Failed to send response to " + clientIP);
            break;
          }

        } catch (Exception e) {
          System.err.println(
              "[ERROR] Failed to process request from " + clientIP + ": " + e.getMessage());

          output.println("ERROR|Failed to process request");
          output.flush();
        }
      }

    } catch (Exception error) {
      System.err.println(
          "[ERROR] Client communication with " + clientIP + ": " + error.getMessage());

    } finally {
      try {
        clientSocket.close();

        System.out.println("[CONNECTION EXITED] Client " + clientIP + " disconnected.");

      } catch (Exception error) {
        System.err.println("[ERROR] Closing socket: " + error.getMessage());
      }
    }
  }
}
