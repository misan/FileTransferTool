import socket
import os
import sys
from threading import Thread
import time

DEFAULT_PORT = 9876
TEST_DATA_SIZE = 50 * 1024 * 1024  # 50 MB

def main():
    if len(sys.argv) < 2:
        print("Usage: python tcpfiletransfer.py [SERVER folder | SEND filename [server] | RECEIVE filename [server] | TEST [server]]")
        sys.exit(1)

    try:
        command = sys.argv[1].upper()
        if command == "SERVER":
            start_server(sys.argv[2] if len(sys.argv) > 2 else ".")
        elif command == "SEND":
            if len(sys.argv) < 3:
                print("Usage: SEND filename [server]")
                sys.exit(1)
            send_file(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "localhost")
        elif command == "RECEIVE":
            if len(sys.argv) < 3:
                print("Usage: RECEIVE filename [server]")
                sys.exit(1)
            receive_file(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "localhost")
        elif command == "TEST":
            test_throughput(sys.argv[2] if len(sys.argv) > 2 else "localhost")
        else:
            print("Invalid mode. Use SERVER, SEND, RECEIVE, or TEST.")
    except Exception as e:
        import traceback
        traceback.print_exc()

def start_server(folder):
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind(('0.0.0.0', DEFAULT_PORT))
    server_socket.listen()
    print(f"Server started on port {DEFAULT_PORT}.")

    base_folder = os.path.abspath(folder)
    if not os.path.exists(base_folder):
        os.makedirs(base_folder)

    while True:
        client_socket, addr = server_socket.accept()
        Thread(target=handle_client, args=(client_socket, base_folder)).start()

def handle_client(client_socket, base_folder):
    with client_socket:
        in_stream = client_socket.makefile('r', buffering=1, newline='\r\n')
        out_stream = client_socket.makefile('w', buffering=1, newline='\r\n')

        command = in_stream.readline().strip()
        if command.startswith("SEND"):
            handle_send_command(command, in_stream, client_socket, base_folder, out_stream)
        elif command.startswith("RECEIVE"):
            handle_receive_command(command, client_socket, base_folder, out_stream)
        elif command == "TEST-SEND":
            handle_test_send(in_stream, client_socket, out_stream)
        elif command == "TEST-RECEIVE":
            handle_test_receive(client_socket, out_stream)
        else:
            out_stream.write("ERROR Invalid command\r\n")
            out_stream.flush()

def handle_send_command(command, in_stream, client_socket, base_folder, out_stream):
    parts = command.split()
    if len(parts) < 3:
        out_stream.write("ERROR Invalid SEND command\r\n")
        out_stream.flush()
        return

    filename = parts[1]
    file_size = int(parts[2])
    file_path = os.path.join(base_folder, filename)

    with open(file_path, 'wb') as fos:
        out_stream.write(f"FILE {filename} {file_size}\r\n")
        out_stream.flush()

        received = 0
        buffer_size = 8192
        while received < file_size:
            data = client_socket.recv(buffer_size)
            if not data:
                break
            fos.write(data)
            received += len(data)

    print(f"File received: {file_path}")

def handle_receive_command(command, client_socket, base_folder, out_stream):
    parts = command.split()
    if len(parts) < 2:
        out_stream.write("ERROR Invalid RECEIVE command\r\n")
        out_stream.flush()
        return

    filename = parts[1]
    file_path = os.path.join(base_folder, filename)

    if not os.path.exists(file_path):
        out_stream.write("ERROR File not found\r\n")
        out_stream.flush()
        return

    file_size = os.path.getsize(file_path)
    out_stream.write(f"FILE {filename} {file_size}\r\n")
    out_stream.flush()

    with open(file_path, 'rb') as fis:
        buffer_size = 8192
        while True:
            data = fis.read(buffer_size)
            if not data:
                break
            client_socket.sendall(data)

    print(f"File sent: {file_path}")

def handle_test_send(in_stream, client_socket, out_stream):
    out_stream.write("READY\r\n")
    out_stream.flush()

    received = 0
    buffer_size = 8192
    start_time = time.time()
    while received < TEST_DATA_SIZE:
        data = client_socket.recv(buffer_size)
        if not data:
            break
        received += len(data)

    duration = time.time() - start_time
    throughput = (received * 8.0 / 1_000_000) / duration
    print(f"Upload throughput: {throughput:.2f} Mbps")

def handle_test_receive(client_socket, out_stream):
    out_stream.write("READY\r\n")
    out_stream.flush()

    sent = 0
    buffer_size = 8192
    start_time = time.time()
    while sent < TEST_DATA_SIZE:
        to_send = min(buffer_size, TEST_DATA_SIZE - sent)
        client_socket.sendall(b'\x00' * to_send)
        sent += to_send

    duration = time.time() - start_time
    throughput = (sent * 8.0 / 1_000_000) / duration
    print(f"Download throughput: {throughput:.2f} Mbps")

def send_file(filename, server):
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((server, DEFAULT_PORT))

    with client_socket:
        out_stream = client_socket.makefile('w', buffering=1, newline='\r\n')
        in_stream = client_socket.makefile('r', buffering=1, newline='\r\n')

        if not os.path.exists(filename):
            print(f"File not found: {filename}")
            return

        file_size = os.path.getsize(filename)
        out_stream.write(f"SEND {os.path.basename(filename)} {file_size}\r\n")
        out_stream.flush()

        response = in_stream.readline().strip()
        if not response.startswith("FILE"):
            print(f"Error from server: {response}")
            return

        with open(filename, 'rb') as fis:
            buffer_size = 8192
            while True:
                data = fis.read(buffer_size)
                if not data:
                    break
                client_socket.sendall(data)

    print(f"File sent: {filename}")

def receive_file(filename, server):
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((server, DEFAULT_PORT))

    with client_socket:
        out_stream = client_socket.makefile('w', buffering=1, newline='\r\n')
        in_stream = client_socket.makefile('r', buffering=1, newline='\r\n')

        out_stream.write(f"RECEIVE {filename}\r\n")
        out_stream.flush()

        response = in_stream.readline().strip()
        if not response.startswith("FILE"):
            print(f"Error from server: {response}")
            return

        parts = response.split()
        file_size = int(parts[2])

        with open(filename, 'wb') as fos:
            received = 0
            buffer_size = 8192
            while received < file_size:
                data = client_socket.recv(buffer_size)
                if not data:
                    break
                fos.write(data)
                received += len(data)

    print(f"File received: {filename}")

def test_throughput(server):
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((server, DEFAULT_PORT))

    with client_socket:
        out_stream = client_socket.makefile('w', buffering=1, newline='\r\n')
        in_stream = client_socket.makefile('r', buffering=1, newline='\r\n')

        # Test upload
        out_stream.write("TEST-SEND\r\n")
        out_stream.flush()

        response = in_stream.readline().strip()
        if not response == "READY":
            print(f"Error from server: {response}")
            return

        sent = 0
        buffer_size = 8192
        start_time = time.time()
        while sent < TEST_DATA_SIZE:
            to_send = min(buffer_size, TEST_DATA_SIZE - sent)
            client_socket.sendall(b'\x00' * to_send)
            sent += to_send

        duration = time.time() - start_time
        upload_throughput = (sent * 8.0 / 1_000_000) / duration
        print(f"Upload throughput: {upload_throughput:.2f} Mbps")

    # Test download
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect((server, DEFAULT_PORT))

    with client_socket:
        out_stream = client_socket.makefile('w', buffering=1, newline='\r\n')
        in_stream = client_socket.makefile('r', buffering=1, newline='\r\n')

        out_stream.write("TEST-RECEIVE\r\n")
        out_stream.flush()

        response = in_stream.readline().strip()
        if not response == "READY":
            print(f"Error from server: {response}")
            return

        received = 0
        buffer_size = 8192
        start_time = time.time()
        while received < TEST_DATA_SIZE:
            data = client_socket.recv(buffer_size)
            if not data:
                break
            received += len(data)

        duration = time.time() - start_time
        download_throughput = (received * 8.0 / 1_000_000) / duration
        print(f"Download throughput: {download_throughput:.2f} Mbps")

if __name__ == "__main__":
    import sys

    if len(sys.argv) < 2:
        print("Usage: python TCPFileTransfer.py [SERVER folder | SEND filename [server] | RECEIVE filename [server] | TEST [server]]")
        sys.exit(1)

    try:
        mode = sys.argv[1].upper()
        if mode == "SERVER":
            start_server(sys.argv[2] if len(sys.argv) > 2 else ".")
        elif mode == "SEND":
            send_file(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "localhost")
        elif mode == "RECEIVE":
            receive_file(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "localhost")
        elif mode == "TEST":
            test_throughput(sys.argv[2] if len(sys.argv) > 2 else "localhost")
        else:
            print("Invalid mode. Use SERVER, SEND, RECEIVE, or TEST.")
    except Exception as e:
        import traceback
        traceback.print_exc()

