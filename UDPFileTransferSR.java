import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Programa unificado para transferencia de archivos mediante UDP
 * Este programa puede funcionar tanto como cliente o servidor dependiendo de los argumentos
 * proporcionados en la línea de comandos.
 *
 * Características principales:
 * - Transferencia confiable usando números de secuencia
 * - Sistema de reconocimiento (ACK) para garantizar la entrega
 * - Manejo de timeouts y reintentos
 * - Soporte para archivos de cualquier tamaño
 */
public class UDPFileTransferSR {
    // Constantes globales del programa
    private static final int SERVER_PORT = 9876;      // Puerto por defecto del servidor
    private static final int BUFFER_SIZE = 516;       // 4 bytes para número de secuencia + 512 bytes para datos
    private static boolean isBusy = false;            // Bandera para controlar si el servidor está ocupado
    private static final int WINDOW_SIZE = 4;
    /**
     * Punto de entrada principal del programa
     * Analiza los argumentos de la línea de comandos y determina el modo de operación
     *
     * Modos de operación:
     * 1. Servidor: java UDPFileTransfer SERVER
     * 2. Cliente (enviar): java UDPFileTransfer SEND archivo [servidor]
     * 3. Cliente (recibir): java UDPFileTransfer RECEIVE archivo [servidor]
     */
    public static void main(String[] args) {
        // Verificar que se proporcionaron argumentos
        if (args.length < 1) {
            printUsage();
            return;
        }

        // Convertir el comando a mayúsculas para hacer la comparación insensible a mayúsculas/minúsculas
        String command = args[0].toUpperCase();
        
        try {
            // Determinar el modo de operación basado en el primer argumento
            switch (command) {
                case "SERVER":
                    // Modo servidor - no debe tener argumentos adicionales
                    if (args.length != 1) {
                        printUsage();
                        return;
                    }
                    runServer();
                    break;
                    
                case "SEND":
                case "RECEIVE":
                    // Modos cliente - requieren nombre de archivo y opcionalmente dirección del servidor
                    if (args.length < 2 || args.length > 3) {
                        printUsage();
                        return;
                    }
                    String fileName = args[1];
                    // Si no se especifica servidor, usar localhost
                    String serverName = (args.length == 3) ? args[2] : "localhost";
                    runClient(command, fileName, serverName);
                    break;
                    
                default:
                    printUsage();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Muestra las instrucciones de uso del programa
     * Explica los diferentes modos y sus parámetros
     */
    private static void printUsage() {
        System.out.println("Uso del programa:");
        System.out.println("  Modo servidor: java UDPFileTransfer SERVER");
        System.out.println("  Modo cliente: java UDPFileTransfer <SEND|RECEIVE> <archivo> [servidor]");
    }

    /**
     * Implementación del lado cliente
     * Maneja tanto el envío como la recepción de archivos
     * 
     * @param action - SEND o RECEIVE
     * @param fileName - nombre del archivo a transferir
     * @param serverName - dirección del servidor
     */
    private static void runClient(String action, String fileName, String serverName) {
        // try-with-resources asegura que el socket se cierre automáticamente
        try (DatagramSocket clientSocket = new DatagramSocket()) {
            // Resolver la dirección del servidor
            InetAddress serverAddress = InetAddress.getByName(serverName);
            
            // Preparar y enviar la solicitud inicial
            String request = action + " " + fileName;
            sendMessage(request, clientSocket, serverAddress, SERVER_PORT);

            // Esperar la respuesta del servidor
            byte[] responseBuffer = new byte[BUFFER_SIZE];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            clientSocket.receive(responsePacket);

            // Procesar la respuesta del servidor
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            
            // Verificar si el servidor está ocupado
            if (response.equals("BUSY")) {
                System.out.println("El servidor está ocupado. Por favor intente más tarde.");
                return;
            }

            // Verificar que la respuesta sea válida
            if (!response.startsWith("FILE ")) {
                System.out.println("Error del servidor: " + response);
                return;
            }

            // Ejecutar la operación solicitada
            if (action.equals("SEND")) {
                sendFile(fileName, clientSocket, serverAddress, SERVER_PORT);
            } else {
                receiveFile(fileName, clientSocket, serverAddress, SERVER_PORT);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Implementación del lado servidor
     * Escucha continuamente por nuevas solicitudes y las procesa
     */
    private static void runServer() {
        // try-with-resources para el socket del servidor
        try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {
            System.out.println("Servidor iniciado en el puerto " + SERVER_PORT + "...");

            while (true) {
                // Preparar buffer para recibir solicitudes
                byte[] receiveBuffer = new byte[BUFFER_SIZE];
                DatagramPacket requestPacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(requestPacket);

                // Extraer información de la solicitud
                String request = new String(requestPacket.getData(), 0, requestPacket.getLength());
                InetAddress clientAddress = requestPacket.getAddress();
                int clientPort = requestPacket.getPort();

                // Verificar si el servidor está ocupado
                if (isBusy) {
                    sendMessage("BUSY", serverSocket, clientAddress, clientPort);
                    continue;
                }

                // Parsear la solicitud
                String[] parts = request.split(" ", 2);
                if (parts.length != 2) {
                    sendMessage("ERROR Invalid request", serverSocket, clientAddress, clientPort);
                    continue;
                }

                String action = parts[0];
                String fileName = parts[1];

                // Manejar solicitud de aborto
                if (action.equals("ABORT")) {
                    isBusy = false;
                    continue;
                }

                // Procesar la solicitud según la acción
                if (action.equals("SEND")) {
                    isBusy = true;
                    sendMessage("FILE " + fileName, serverSocket, clientAddress, clientPort);
                    receiveFile(fileName, serverSocket, clientAddress, clientPort);
                    isBusy = false;
                } else if (action.equals("RECEIVE")) {
                    isBusy = true;
                    sendMessage("FILE " + fileName, serverSocket, clientAddress, clientPort);
                    sendFile(fileName, serverSocket, clientAddress, clientPort);
                    isBusy = false;
                } else {
                    sendMessage("ERROR Invalid action", serverSocket, clientAddress, clientPort);
                }

                serverSocket.setSoTimeout(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Método utilitario para enviar mensajes UDP
     * 
     * @param message - mensaje a enviar
     * @param socket - socket UDP a usar
     * @param address - dirección destino
     * @param port - puerto destino
     */
    private static void sendMessage(String message, DatagramSocket socket, InetAddress address, int port) throws IOException {
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
    }
/*
    /**
     * Envía un archivo por UDP usando un protocolo confiable
     * 
     * Características del protocolo:
     * - División del archivo en bloques de 512 bytes
     * - Número de secuencia para cada bloque
     * - Sistema de acknowledgment (ACK)
     * - Reintentos en caso de pérdida de paquetes
     * 
     * @param fileName - nombre del archivo a enviar
     * @param socket - socket UDP a usar
     * @param address - dirección destino
     * @param port - puerto destino
    private static void sendFile(String fileName, DatagramSocket socket, InetAddress address, int port) {
        try (FileInputStream fis = new FileInputStream(fileName)) {
            byte[] buffer = new byte[512];
            int blockNumber = 0;
            int retries;

            while (true) {
                // Leer un bloque del archivo
                int bytesRead = fis.read(buffer);
                if (bytesRead == -1) {
                    bytesRead = 0; // Enviar paquete vacío para EOF si el tamaño del archivo es múltiplo de 512
                }

                // Preparar el paquete con número de secuencia y datos
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                dos.writeInt(blockNumber);        // Escribir número de secuencia (4 bytes)
                dos.write(buffer, 0, bytesRead);  // Escribir datos

                byte[] sendData = baos.toByteArray();
                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, address, port);

                // Sistema de reintentos y acknowledgment
                retries = 0;
                boolean acknowledged = false;

                while (retries < 5 && !acknowledged) {
                    socket.send(packet);

                    // Esperar ACK
                    byte[] ackBuffer = new byte[4];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);

                    try {
                        socket.setSoTimeout(1000);  // Timeout de 1 segundo para ACK
                        socket.receive(ackPacket);
                        
                        // Verificar que el ACK corresponde al bloque enviado
                        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(ackPacket.getData()));
                        int ackBlockNumber = dis.readInt();

                        if (ackBlockNumber == blockNumber) {
                            acknowledged = true;
                        }
                    } catch (SocketTimeoutException e) {
                        retries++;
                    }
                }

                // Si no se recibió ACK después de 5 intentos, abortar
                if (!acknowledged) {
                    System.out.println("Transferencia abortada: No se recibió ACK para el bloque " + blockNumber);
                    sendMessage("ABORT", socket, address, port);
                    return;
                }

                // Si el último bloque fue más pequeño que 512 bytes, hemos terminado
                if (bytesRead < 512) {
                    break;
                }

                blockNumber++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Recibe un archivo por UDP usando un protocolo confiable
     * 
     * Características:
     * - Reconstrucción ordenada del archivo usando números de secuencia
     * - Envío de ACKs para cada bloque recibido
     * - Manejo de bloques duplicados
     * 
     * @param fileName - nombre del archivo a crear/escribir
     * @param socket - socket UDP a usar
     * @param address - dirección del remitente (para enviar ACKs)
     * @param port - puerto del remitente
    private static void receiveFile(String fileName, DatagramSocket socket, InetAddress address, int port) {
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int expectedBlockNumber = 0;

            while (true) {
                // Recibir un bloque de datos
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Extraer número de secuencia y datos
                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                DataInputStream dis = new DataInputStream(bais);
                int blockNumber = dis.readInt();           // Leer número de secuencia
                byte[] data = new byte[packet.getLength() - 4];
                dis.readFully(data);                       // Leer datos

                // Verificar si es el bloque que esperábamos
                if (blockNumber == expectedBlockNumber) {
                    fos.write(data);  // Escribir datos al archivo

                    // Enviar ACK
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    dos.writeInt(expectedBlockNumber);

                    byte[] ackData = baos.toByteArray();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, port);
                    socket.send(ackPacket);

                    expectedBlockNumber++;
                }

                // Si el bloque es más pequeño que 512 bytes, es el último
                if (data.length < 512) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
*/

// Changes applied to the sendFile and receiveFile methods

private static void sendFile(String fileName, DatagramSocket socket, InetAddress address, int port) {
    try (FileInputStream fis = new FileInputStream(fileName)) {
        byte[] buffer = new byte[512];
        int base = 0, nextSeqNum = 0;
        Map<Integer, byte[]> packetMap = new HashMap<>();
        Map<Integer, Long> packetTimers = new HashMap<>();
        boolean eof = false;

        while (true) {
            // Send packets within the window
            while (nextSeqNum < base + WINDOW_SIZE && !eof) {
                int bytesRead = fis.read(buffer);
                if (bytesRead < 512) { // EOF: send a zero-length data packet
                    eof = true;
                    if (bytesRead == -1) bytesRead = 0;
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                dos.writeInt(nextSeqNum);
                dos.write(buffer, 0, bytesRead);
                byte[] packetData = baos.toByteArray();
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, address, port);

                socket.send(packet);
                packetMap.put(nextSeqNum, packetData);
                packetTimers.put(nextSeqNum, System.currentTimeMillis());
                nextSeqNum++;
            }

            // Receive ACKs
            byte[] ackBuffer = new byte[4];
            DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
            try {
                socket.setSoTimeout(100);
                socket.receive(ackPacket);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(ackBuffer));
                int ackNum = dis.readInt();

                if (ackNum >= base) {
                    base = ackNum + 1; // Slide the window
                }
            } catch (SocketTimeoutException e) {
                // Timeout: retransmit unacknowledged packets
		System.out.print(".");
                long currentTime = System.currentTimeMillis();
                for (Map.Entry<Integer, Long> entry : packetTimers.entrySet()) {
                    if (entry.getKey() >= base && entry.getKey() < base + WINDOW_SIZE) {
                        if (currentTime - entry.getValue() > 500) {
                            byte[] packetData = packetMap.get(entry.getKey());
                            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, address, port);
                            socket.send(packet);
                            packetTimers.put(entry.getKey(), currentTime);
                        }
                    }
                }
            }

            // Exit condition: all packets (including EOF) have been acknowledged
            if (eof && base == nextSeqNum) {
                System.out.println("Transfer complete.");
                break;
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private static void receiveFile(String fileName, DatagramSocket socket, InetAddress address, int port) {
    try (FileOutputStream fos = new FileOutputStream(fileName)) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int base = 0;
        Map<Integer, byte[]> packetBuffer = new HashMap<>();
        boolean eof = false;

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            DataInputStream dis = new DataInputStream(bais);
            int seqNum = dis.readInt();
            byte[] data = new byte[packet.getLength() - 4];
            dis.readFully(data);

            // Send ACK for the received packet
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(seqNum);
            byte[] ackData = baos.toByteArray();
            DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, port);
            socket.send(ackPacket);

            // Buffer the packet and check for delivery
            if (seqNum >= base && seqNum < base + WINDOW_SIZE) {
                packetBuffer.put(seqNum, data);
                while (packetBuffer.containsKey(base)) {
                    byte[] inOrderData = packetBuffer.remove(base);
                    fos.write(inOrderData);
                    base++;

                    // Check for EOF
                    if (inOrderData.length < 512) {
                        eof = true;
                    }
                }
            }

            // Exit condition: EOF detected and acknowledged
            if (eof && packetBuffer.isEmpty()) {
                System.out.println("File received successfully.");
                break;
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}




}
