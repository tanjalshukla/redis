import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;


public class Main {
  // server port
  private final static int PORT = 6379;

  public static void main(String[] args){
    try (ServerSocket serverSocket = new ServerSocket()) {
      // setting reuse address before bind to bypass timeouts from prev connections
      serverSocket.setReuseAddress(true);
      serverSocket.bind(new InetSocketAddress("localhost", PORT));

      // continuously accept new connections
      while (true) {
        // Wait for connection from client.
        Socket clientSocket = serverSocket.accept();
        // dispatch handler in vthread
        Thread vThread = Thread.startVirtualThread(() ->
        {
          handleClient(clientSocket);
        });
      }
    } catch (IOException e) {
      System.err.println("Could not listen on port: " + PORT);
    }
  }

  /** Handles multiple commands from one client thru virtual thread */
  private static void handleClient(Socket clientSocket) {
    try (clientSocket) {
        BufferedInputStream in = new BufferedInputStream(clientSocket.getInputStream());
        BufferedOutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());
        while (true) {
            Protocol.handleCommand(in, out);
            // ensure pushed to client
            out.flush();
        }
    } catch (EOFException _) {
      // ignore, client finished
    } catch (IOException e) {
      System.err.println("Client Exception " + e.getMessage());
    }
  }
}
