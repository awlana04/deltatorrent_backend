package com.deltatorrent.handlers;

import com.deltatorrent.utils.ProtocolParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

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

    ProtocolParser parser = new ProtocolParser();

    // This is the Stream manegement that treats data
    // We need to declare input and output inside the try (try-with-resources), this ensures the
    // safely close
    // If we don't do this, then we'd need to manually close on the .finally to avoid resource leaks
    try (
    // Treats what the client types on the network
    BufferedReader input =
            new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        // Pulls back data into user computer by networks parameters without pile up cache
        PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true); ) {
      String rawMessage;

      // This keeps the thread alive listening to the client
      // If the client doesn't says anything, the thread will sleep without wasting processing
      while ((rawMessage = input.readLine()) != null) {
        System.out.println("[RECEIVED from " + clientIP + "]: " + rawMessage);

        if (rawMessage.equalsIgnoreCase("QUIT")) {
          output.println("BYE|Connection exited with success.");
          break;
        }

        String answer = parser.treat(rawMessage);
        System.out.println(answer);
      }
    } catch (Exception error) {
      System.err.println("[ERRRO] Client communication " + clientIP + ": " + error.getMessage());
    } finally {
      try {
        clientSocket.close();
        System.out.println("[CONNECTION EXITED] Client " + clientIP + " disconnected.");
      } catch (Exception error) {
        System.err.println("[ERROR] Closing the socket: " + error.getMessage());
      }
    }
  }
}
