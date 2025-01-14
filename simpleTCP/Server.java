import java.io.*;
import java.net.*;

public class Server {
    private final int port;
    private final String folder;

    public Server(int port, String folder) {
        this.port = port;
        this.folder = folder;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket, folder)).start();
            }
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final String folder;

        public ClientHandler(Socket socket, String folder) {
            this.clientSocket = socket;
            this.folder = folder;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                 DataOutputStream dataOut = new DataOutputStream(clientSocket.getOutputStream());
                 DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream())) {

                String command = in.readLine();
                if (command == null || command.isEmpty()) {
                    sendResponse(out, "ERROR", "Invalid command");
                    return;
                }

                String[] parts = command.split(" ");
                String action = parts[0];
                switch (action.toUpperCase()) {
                    case "SEND":
                        handleSend(parts, dataIn);
                        break;
                    case "RECEIVE":
                        handleReceive(parts, out, dataOut);
                        break;
                    default:
                        sendResponse(out, "ERROR", "Invalid command");
                }
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Failed to close socket: " + e.getMessage());
                }
            }
        }

        private void sendResponse(PrintWriter out, String responseType, String message) {
            out.println(responseType.toUpperCase() + " " + message);
        }

        private void handleSend(String[] parts, DataInputStream dataIn) throws IOException {
            if (parts.length < 3) {
                sendResponse(new PrintWriter(clientSocket.getOutputStream(), true), "ERROR", "Invalid SEND command");
                return;
            }
			
            String filename = parts[1];
			sendResponse(new PrintWriter(clientSocket.getOutputStream(), true), "FILE", filename );
            long fileSize = Long.parseLong(parts[2]);
            File file = new File(folder, filename);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = dataIn.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            
        }

        private void handleReceive(String[] parts, PrintWriter out, DataOutputStream dataOut) throws IOException {
            if (parts.length < 2) {
                out.println("ERROR Invalid RECEIVE command");
                return;
            }
            String filename = parts[1];
            File file = new File(folder, filename);

            if (!file.exists()) {
                sendResponse(out, "ERROR", "File not found: " + filename);
                return;
            }

            out.println("FILE " + filename + " " + file.length());
            byte[] buffer = new byte[8192];
            try (FileInputStream fis = new FileInputStream(file)) {
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}
