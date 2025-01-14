import java.io.*;
import java.net.*;

public class TCPFileTransfer {

    private static final int DEFAULT_PORT = 9876;
    private static final int TEST_DATA_SIZE = 50 * 1024 * 1024; // 50 MB

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java TCPFileTransfer [SERVER folder | SEND filename [server] | RECEIVE filename [server] | TEST [server]]");
            return;
        }

        try {
            switch (args[0].toUpperCase()) {
                case "SERVER":
                    startServer(args.length > 1 ? args[1] : ".");
                    break;
                case "SEND":
                    if (args.length < 2) {
                        System.out.println("Usage: SEND filename [server]");
                        return;
                    }
                    sendFile(args[1], args.length > 2 ? args[2] : "localhost");
                    break;
                case "RECEIVE":
                    if (args.length < 2) {
                        System.out.println("Usage: RECEIVE filename [server]");
                        return;
                    }
                    receiveFile(args[1], args.length > 2 ? args[2] : "localhost");
                    break;
                case "TEST":
                    testThroughput(args.length > 1 ? args[1] : "localhost");
                    break;
                default:
                    System.out.println("Invalid mode. Use SERVER, SEND, RECEIVE, or TEST.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startServer(String folder) throws IOException {
        ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT);
        System.out.println("Server started on port " + DEFAULT_PORT + ".");
        File baseFolder = new File(folder);
        if (!baseFolder.exists() && !baseFolder.mkdirs()) {
            throw new IOException("Failed to create base folder: " + folder);
        }

        while (true) {
            try (Socket clientSocket = serverSocket.accept()) {
                handleClient(clientSocket, baseFolder);
            }
        }
    }

    private static void handleClient(Socket clientSocket, File baseFolder) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        String command = in.readLine();

        if (command.startsWith("SEND")) {
            handleSendCommand(command, in, clientSocket.getInputStream(), baseFolder, out);
        } else if (command.startsWith("RECEIVE")) {
            handleReceiveCommand(command, clientSocket.getOutputStream(), baseFolder, out);
        } else if (command.equals("TEST-SEND")) {
            handleTestSend(in, clientSocket.getInputStream(), out);
        } else if (command.equals("TEST-RECEIVE")) {
            handleTestReceive(clientSocket.getOutputStream(), out);
        } else {
            out.write("ERROR Invalid command\r\n");
            out.flush();
        }
    }

    private static void handleSendCommand(String command, BufferedReader in, InputStream clientIn, File baseFolder, BufferedWriter out) throws IOException {
        String[] parts = command.split(" ", 3);
        if (parts.length < 3) {
            out.write("ERROR Invalid SEND command\r\n");
            out.flush();
            return;
        }

        String filename = parts[1];
        long fileSize = Long.parseLong(parts[2]);
        File file = new File(baseFolder, filename);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            out.write("FILE " + filename + " " + fileSize + "\r\n");
            out.flush();

            byte[] buffer = new byte[8192];
            long received = 0;
            while (received < fileSize) {
                int read = clientIn.read(buffer);
                if (read == -1) break;
                fos.write(buffer, 0, read);
                received += read;
            }
        }

        System.out.println("File received: " + file.getAbsolutePath());
    }

    private static void handleReceiveCommand(String command, OutputStream clientOut, File baseFolder, BufferedWriter out) throws IOException {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2) {
            out.write("ERROR Invalid RECEIVE command\r\n");
            out.flush();
            return;
        }

        String filename = parts[1];
        File file = new File(baseFolder, filename);
        if (!file.exists()) {
            out.write("ERROR File not found\r\n");
            out.flush();
            return;
        }

        out.write("FILE " + filename + " " + file.length() + "\r\n");
        out.flush();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                clientOut.write(buffer, 0, read);
            }
        }

        System.out.println("File sent: " + file.getAbsolutePath());
    }

    private static void handleTestSend(BufferedReader in, InputStream clientIn, BufferedWriter out) throws IOException {
        out.write("READY\r\n");
        out.flush();

        byte[] buffer = new byte[8192];
        long received = 0;
        long startTime = System.nanoTime();
        while (received < TEST_DATA_SIZE) {
            int read = clientIn.read(buffer);
            if (read == -1) break;
            received += read;
        }
        long duration = System.nanoTime() - startTime;
        double throughput = (received * 8.0 / 1_000_000) / (duration / 1_000_000_000.0);
        System.out.printf("Upload throughput: %.2f Mbps\n", throughput);
    }

    private static void handleTestReceive(OutputStream clientOut, BufferedWriter out) throws IOException {
        out.write("READY\r\n");
        out.flush();

        byte[] buffer = new byte[8192];
        long sent = 0;
        long startTime = System.nanoTime();
        while (sent < TEST_DATA_SIZE) {
            int toSend = (int) Math.min(buffer.length, TEST_DATA_SIZE - sent);
            clientOut.write(buffer, 0, toSend);
            clientOut.flush();
            sent += toSend;
        }
        long duration = System.nanoTime() - startTime;
        double throughput = (sent * 8.0 / 1_000_000) / (duration / 1_000_000_000.0);
        System.out.printf("Download throughput: %.2f Mbps\n", throughput);
    }

    private static void sendFile(String filename, String server) throws IOException {
        try (Socket socket = new Socket(server, DEFAULT_PORT)) {
            File file = new File(filename);
            if (!file.exists()) {
                System.out.println("File not found: " + filename);
                return;
            }

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.write("SEND " + file.getName() + " " + file.length() + "\r\n");
            out.flush();

            String response = in.readLine();
            if (!response.startsWith("FILE")) {
                System.out.println("Error from server: " + response);
                return;
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    socket.getOutputStream().write(buffer, 0, read);
                }
            }

            System.out.println("File sent: " + filename);
        }
    }

    private static void receiveFile(String filename, String server) throws IOException {
        try (Socket socket = new Socket(server, DEFAULT_PORT)) {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.write("RECEIVE " + filename + "\r\n");
            out.flush();

            String response = in.readLine();
            if (!response.startsWith("FILE")) {
                System.out.println("Error from server: " + response);
                return;
            }

            String[] parts = response.split(" ", 3);
            long fileSize = Long.parseLong(parts[2]);
            File file = new File(filename);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                long received = 0;
                while (received < fileSize) {
                    int read = socket.getInputStream().read(buffer);
                    if (read == -1) break;
                    fos.write(buffer, 0, read);
                    received += read;
                }
            }

            System.out.println("File received: " + filename);
        }
    }

    private static void testThroughput(String server) throws IOException {
        try (Socket socket = new Socket(server, DEFAULT_PORT)) {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Test upload
            out.write("TEST-SEND\r\n");
            out.flush();
            String response = in.readLine();
            if (!response.equals("READY")) {
                System.out.println("Error from server: " + response);
                return;
            }

            byte[] buffer = new byte[8192];
            long sent = 0;
            long startTime = System.nanoTime();
            while (sent < TEST_DATA_SIZE) {
                int toSend = (int) Math.min(buffer.length, TEST_DATA_SIZE - sent);
                socket.getOutputStream().write(buffer, 0, toSend);
                sent += toSend;
            }
            long duration = System.nanoTime() - startTime;
            double uploadThroughput = (sent * 8.0 / 1_000_000) / (duration / 1_000_000_000.0);
            System.out.printf("Upload throughput: %.2f Mbps\n", uploadThroughput);

            // Test download
            socket.close(); // Close and re-establish connection for download test
            Socket socket2 = new Socket(server, DEFAULT_PORT);
            out = new BufferedWriter(new OutputStreamWriter(socket2.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(socket2.getInputStream()));

            out.write("TEST-RECEIVE\r\n");
            out.flush();
            response = in.readLine();
            if (!response.equals("READY")) {
                System.out.println("Error from server: " + response);
                return;
            }

            long received = 0;
            startTime = System.nanoTime();
            while (received < TEST_DATA_SIZE) {
                int read = socket2.getInputStream().read(buffer);
                if (read == -1) break;
                received += read;
            }
            duration = System.nanoTime() - startTime;
            double downloadThroughput = (received * 8.0 / 1_000_000) / (duration / 1_000_000_000.0);
            System.out.printf("Download throughput: %.2f Mbps\n", downloadThroughput);
        }
    }
}
