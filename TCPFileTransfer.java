import java.io.IOException;
import java.io.*;
import java.util.*;
import java.net.*;

import java.io.IOException;

public class TCPFileTransfer {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: SERVER [folder] | SEND filename [server] | RECEIVE filename [server]");
            return;
        }

        String mode = args[0].toUpperCase();
        switch (mode) {
            case "SERVER":
                String folder = args.length > 1 ? args[1] : ".";
                new Server(9876, folder).start();
                break;
            case "SEND":
                if (args.length < 2) {
                    System.err.println("Usage: SEND filename [server]");
                    return;
                }
                String server = args.length > 2 ? args[2] : "localhost";
                new Client(server, 9876).sendFile(args[1]);
                break;
            case "RECEIVE":
                if (args.length < 2) {
                    System.err.println("Usage: RECEIVE filename [server]");
                    return;
                }
                server = args.length > 2 ? args[2] : "localhost";
                new Client(server, 9876).receiveFile(args[1]);
                break;
            default:
                System.err.println("Invalid mode. Use SERVER [folder] | SEND filename [server] | RECEIVE filename [server]");
        }
    }
}
