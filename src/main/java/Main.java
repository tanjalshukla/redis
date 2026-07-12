import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
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
      DataInputStream in = new DataInputStream(clientSocket.getInputStream());
      byte[] buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
        clientSocket.getOutputStream().write("+PONG\r\n".getBytes());
      }
    } catch (EOFException _) {
      // ignore, client finished
    } catch (IOException e) {
      System.out.println("Client Exception " + e.getMessage());
    }
  }
}
