import java.io.*;
import java.nio.*;
import java.net.*;

public class UDPFileTransfer {

    private static final int PORT = 9876;
    private static final String DEFAULT_SERVER_IP = "127.0.0.1"; // Default to localhost

    public static void main(String[] args) throws UnknownHostException {
        if (args.length < 1 || !args[0].equals("SEND") && !args[0].equals("RECEIVE") && !args[0].equals("SERVER")) {
            System.out.println("Usage: java UDPFileTransfer SEND|RECEIVE [filename] [server_ip] | SERVER");
            return;
        }

        String action = args[0];

        if (action.equals("SEND")) {
            sendFile(args.length >= 3 ? InetAddress.getByName(args[2]) : InetAddress.getByName(DEFAULT_SERVER_IP), args[1]);
        } else if (action.equals("RECEIVE")) {
            receiveFile(args.length >= 2 ? args[1] : null, args.length == 3 ? InetAddress.getByName(args[2]) : InetAddress.getByName(DEFAULT_SERVER_IP));
        } else if (action.equals("SERVER")) {
            runServer();
        }
    }

    private static void sendFile(InetAddress serverAddress, String filename) throws IOException {
        DatagramSocket socket = new DatagramSocket();

        byte[] sendData = ("SEND " + filename).getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, PORT);
        socket.send(sendPacket);

        // Wait for response
        byte[] receiveBuffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(packet);

        String receivedMessage = new String(packet.getData(), 0, packet.getLength());
        System.out.println("Received: " + receivedMessage);

        if (receivedMessage.equals("FILE")) {
            handleFileTransfer(socket, serverAddress, PORT, filename);
        } else {
            System.err.println("Server is busy");
        }
    }

    private static void receiveFile(String filename, InetAddress serverAddress) throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);

        byte[] receiveBuffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);

        while (true) {
            socket.receive(packet); // Wait for request
            String receivedMessage = new String(packet.getData(), 0, packet.getLength());

            if (!receivedMessage.equals("BUSY")) { // Server is not busy
                // Send confirmation to start file transfer
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                String response = "FILE " + receivedMessage.substring(6);
                byte[] sendData = response.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                socket.send(sendPacket);

                // Handle file transfer
                handleFileTransfer(socket, clientAddress, clientPort, filename);
            }
        }
    }

    private static void runServer() throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);

        byte[] receiveBuffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);

        while (true) {
            socket.receive(packet); // Wait for request
            String receivedMessage = new String(packet.getData(), 0, packet.getLength());

            if (!receivedMessage.equals("BUSY")) { // Server is not busy
                // Send confirmation to start file transfer
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();

                String response = "FILE " + receivedMessage.substring(6);
                byte[] sendData = response.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                socket.send(sendPacket);

                // Handle file transfer
                handleFileTransfer(socket, clientAddress, clientPort);
            }
        }
    }

    private static void handleFileTransfer(DatagramSocket socket, InetAddress address, int port, String filename) throws IOException {
        File file = new File(filename);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        FileOutputStream fos = null;
        byte[] buffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            fos = new FileOutputStream(file);
            int blockNumber = 0;

            while (true) {
                socket.receive(packet); // Wait for data
                String receivedMessage = new String(packet.getData(), 0, packet.getLength());

                if (!receivedMessage.startsWith(String.valueOf(blockNumber))) { // Check sequence number
                    continue;
                }

                buffer = new byte[512];
                int readBytes = Integer.parseInt(receivedMessage.substring(4)); // Extract block data length

                int retries = 5;
                while (retries-- > 0) {
                    fos.write(buffer, 4, readBytes);
                    String ackMessage = String.valueOf(blockNumber);
                    byte[] sendData = ackMessage.getBytes();

                    DatagramPacket sendAck = new DatagramPacket(sendData, sendData.length, address, port);
                    socket.send(sendAck);

                    try {
                        socket.receive(packet); // Wait for next block or confirmation
                        receivedMessage = new String(packet.getData(), 0, packet.getLength());
                        if (receivedMessage.equals("EOF")) { // End of file
                            break;
                        }
                        blockNumber++;
                    } catch (IOException e) {
                        System.out.println("Timeout: Resending data");
                    }
                }

                if (retries < 0) { // Failed to receive and send ACK after 5 retries
                    System.err.println("Transfer failed for file " + filename);
                    break;
                }

                blockNumber++;
            }
        } finally {
            if (fos != null) fos.close();
        }
    }

    private static void handleFileTransfer(DatagramSocket socket, InetAddress address, int port) throws IOException {
        byte[] buffer = new byte[512];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            socket.receive(packet); // Wait for data
            String receivedMessage = new String(packet.getData(), 0, packet.getLength());

            if (!receivedMessage.startsWith(String.valueOf(blockNumber))) { // Check sequence number
                continue;
            }

            int readBytes = Integer.parseInt(receivedMessage.substring(4)); // Extract block data length

            int retries = 5;
            while (retries-- > 0) {
                byte[] data = new byte[readBytes];
                System.arraycopy(packet.getData(), 4, data, 0, readBytes);
                DatagramPacket sendAck = new DatagramPacket(String.valueOf(blockNumber).getBytes(), String.valueOf(blockNumber).length(), address, port);
                socket.send(sendAck);

                try {
                    socket.receive(packet); // Wait for next block or confirmation
                    receivedMessage = new String(packet.getData(), 0, packet.getLength());
                    if (receivedMessage.equals("EOF")) { // End of file
                        break;
                    }
                    blockNumber++;
                } catch (IOException e) {
                    System.out.println("Timeout: Resending data");
                }
            }

            if (retries < 0) { // Failed to receive and send ACK after 5 retries
                System.err.println("Transfer failed for file " + filename);
                break;
            }

            blockNumber++;
        }
    }
}
