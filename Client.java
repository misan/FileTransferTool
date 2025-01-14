import java.io.*;
import java.net.*;

public class Client {
    private final String serverAddress;
    private final int port;

    public Client(String serverAddress, int port) {
        this.serverAddress = serverAddress;
        this.port = port;
    }

    public void sendFile(String filename) throws IOException {
        try (Socket socket = new Socket(serverAddress, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             FileInputStream fis = new FileInputStream(filename)) {

            File file = new File(filename);
            long fileSize = file.length();
            String command = "SEND " + filename + " " + fileSize;
            out.println(command);

            String response = in.readLine();
            if (response.startsWith("ERROR")) {
                System.err.println(response);
                return;
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                socket.getOutputStream().write(buffer, 0, bytesRead);
            }
        }
    }

    public void receiveFile(String filename) throws IOException {
        try (Socket socket = new Socket(serverAddress, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             FileOutputStream fos = new FileOutputStream(filename)) {

            String command = "RECEIVE " + filename;
            out.println(command);

            String response = in.readLine();
            if (response.startsWith("ERROR")) {
                System.err.println(response);
                return;
            }

            String[] parts = response.split(" ");
            if (!parts[0].toUpperCase().equals("FILE") || parts.length < 3) {
                System.err.println("Invalid FILE response");
                return;
            }
            
            long fileSize = Long.parseLong(parts[2]);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = socket.getInputStream().read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }
}
