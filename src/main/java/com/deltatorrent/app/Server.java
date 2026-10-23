package com.deltatorrent.app;

import com.deltatorrent.handlers.ClientHandler;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
  private static final int PORT = 8500;
  // The number of threads that can be accepted on the connection
  private static final ExecutorService threadPool = Executors.newFixedThreadPool(50);

  public static void main(String[] args) {
    System.out.println("=== SERVER EXECUTING ===");
    System.out.println("Listening to the port: " + PORT);

    try (ServerSocket serverSocket = new ServerSocket(PORT)) {
      // This is to watch the network and accept new connections
      while (true) {
        System.out.println("\n[STATUS] Waiting for new connections...");

        // Waits until a new connection is established
        // When is established, it returns a Socket with some connection data
        Socket clientSocket = serverSocket.accept();

        System.out.println("[CONNECTION] A new try to connection received!");

        // Is used to execute tasks inside the Threads Pool
        // Every new connections owns an exclusive ClientHandler with indepedent threads
        threadPool.execute(new ClientHandler(clientSocket));
      }
    } catch (Exception error) {
      System.err.println("[ERROR]: " + error.getMessage());
    } finally {
      // Turns off the Threads Pool if the server closes safely
      threadPool.shutdown();
    }
  }
}
