## UDP File Transfer Protocol Specification

### 1. Introduction

This document specifies a simple User Datagram Protocol (UDP) based file transfer mechanism used by the `UDPFileTransfer` Java application. The application supports three roles: sender, receiver, and server. The protocol enables transferring files between clients through an intermediate server.

### 2. Definitions

- **Sender**: The client initiating a file send request.
- **Receiver**: The client requesting to receive a file.
- **Server**: An intermediary that facilitates the connection between the sender and receiver.

### 3. Protocol Overview

The protocol operates on UDP, using port `9876` as the default communication endpoint. It employs simple text-based messaging for control signals and data transfer. Files are transferred in blocks of up to 512 bytes each (excluding overhead), ensuring efficient use of network resources.

### 4. Message Format

#### Control Messages
- **SEND**: Initiate a file send request.
    - Format: `"SEND <filename>"`
  
- **FILE**: Acknowledgment from server indicating readiness to receive or start sending files.
  
- **BUSY**: Notification that the server is currently busy and cannot process requests.

- **EOF**: End of File indicator, signifying the completion of file transfer.

#### Data Messages
Each data message consists of a sequence number followed by the actual data block. The format is:
  - Sequence Number (4 bytes)
  - Length of Data Block (Integer as String, max 4 bytes)
  - Actual Data (up to 512 bytes minus overhead)

### 5. Protocol Flow

#### Sender-Initiated Transfer
1. **File Request**: 
   - Sender sends a `SEND <filename>` packet to the server.
2. **Server Response**:
   - If ready, the server responds with a `FILE` message containing the filename.
3. **Data Transfer**:
   - The sender transmits file data blocks, each prefixed by its sequence number and length.
   - The server forwards these packets to the receiver.

#### Receiver-Initiated Transfer
1. **File Request**:
   - Receiver requests file reception with `RECEIVE <filename>`.
2. **Server Confirmation**:
   - If not busy, the server responds with a `FILE` message, indicating readiness.
3. **Data Reception and Acknowledgment**:
   - Receiver acknowledges each data block received using sequence numbers.
4. **Completion**:
   - Transfer completes when an `EOF` message is detected.

### 6. Error Handling

- **Timeouts**: In case of timeouts during acknowledgment exchanges, the sender may retry up to five times before aborting the transfer.
- **Sequence Mismatch**: If a received block's sequence number does not match expectations, it is disregarded and awaited for retransmission.
