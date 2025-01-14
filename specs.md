# TCP-Based File Transfer Protocol Implementation

## Overview

This document provides detailed specifications and guidelines for implementing a reliable TCP-based file transfer protocol in Java. The protocol supports bidirectional file transfers, error handling, and performance testing through dummy data transfers. This implementation ensures that both clients and servers can communicate efficiently and reliably.

## Key Features

1. **Bidirectional File Transfers:**
   - Clients can send files to the server.
   - Clients can receive files from the server.

2. **Error Handling:**
   - Invalid commands are handled gracefully with appropriate error messages.
   - File-related errors (e.g., file not found) are managed effectively.

3. **Throughput Testing:**
   - The `TEST` command allows clients to measure upload and download throughput by transferring dummy data.

4. **Cross-Platform Compatibility:**
   - Uses hardcoded end-of-line characters (`\r\n`) for consistent communication across different platforms.

## Protocol Details

### Architecture

- **Single Program Mode:**
  - A single Java program can operate in either server or client mode.
  
- **Default Server Port:**
  - The server listens on port `9876` by default.

- **Client Modes:**
  - Clients can send files to the server using the `SEND` command.
  - Clients can receive files from the server using the `RECEIVE` command.

### Initial Handshake

1. **Client Commands:**
   - **Send File:**
     ```
     SEND filename filesize\r\n
     ```
     Example:
     ```
     SEND example.txt 1024\r\n
     ```
   - **Receive File:**
     ```
     RECEIVE filename\r\n
     ```
     Example:
     ```
     RECEIVE example.txt\r\n
     ```

2. **Server Responses:**
   - **Ready to Proceed:**
     ```
     FILE filename [filesize]\r\n
     ```
     Example for `RECEIVE` command:
     ```
     FILE example.txt 1024\r\n
     ```
   - **Error Response:**
     ```
     ERROR details message\r\n
     ```
     Example:
     ```
     ERROR Invalid file size\r\n
     ```

### Data Transfer Protocol

- **End of File Signal:**
  - The end of the file is signaled by closing the TCP connection after sending all data bytes.

## Usage Modes

### Server Mode

To start the server and listen for incoming connections:

```sh
java TCPFileTransfer SERVER [folder]
```

- **Parameters:**
  - `[folder]`: Optional. Specifies the folder where files will be stored or served from. If not provided, a default directory can be used.

### Client Modes

#### Send File to Server

To send a file to the server:

```sh
java TCPFileTransfer SEND filename [server]
```

- **Parameters:**
  - `filename`: The name of the file to send.
  - `[server]`: Optional. Specifies the server address (default is `localhost`).

#### Receive File from Server

To receive a file from the server:

```sh
java TCPFileTransfer RECEIVE filename [server]
```

- **Parameters:**
  - `filename`: The name of the file to receive.
  - `[server]`: Optional. Specifies the server address (default is `localhost`).

### Throughput Testing

To measure upload and download throughput:

```sh
java TCPFileTransfer TEST [server]
```

- **Parameters:**
  - `[server]`: Optional. Specifies the server address (default is `localhost`).

## Implementation Details

### Server Implementation

1. **Listening for Connections:**

   The server listens on a specified port (`9876` by default) and accepts incoming client connections.

2. **Handling Client Commands:**

   - **SEND Command:**
     - Reads the file name and size.
     - Responds with `FILE filename filesize\r\n`.
     - Receives the file data from the client.
   
   - **RECEIVE Command:**
     - Reads the file name.
     - Responds with `FILE filename filesize\r\n`.
     - Sends the file data to the client.

3. **Error Handling:**

   - Checks for invalid commands and responds with appropriate error messages.

### Client Implementation

1. **Connecting to Server:**

   The client connects to the server using the specified address and port.

2. **Sending Commands:**

   - **SEND Command:**
     - Sends `SEND filename filesize\r\n`.
     - Reads the response from the server.
     - Sends the file data to the server.
   
   - **RECEIVE Command:**
     - Sends `RECEIVE filename\r\n`.
     - Reads the response from the server.
     - Receives the file data from the server.

3. **Throughput Testing:**

   - **TEST-SEND Command:**
     - Sends `TEST-SEND\r\n`.
     - Generates 50 MB of dummy data and sends it to the server.
     - Measures the time taken for the transfer and calculates throughput in Mbps.
   
   - **TEST-RECEIVE Command:**
     - Sends `TEST-RECEIVE\r\n`.
     - Receives 50 MB of dummy data from the server.
     - Measures the time taken for the transfer and calculates throughput in Mbps.

4. **Error Handling:**

   - Reads error messages from the server and handles them appropriately.

