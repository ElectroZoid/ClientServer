# Java P2P Networking with Central Discovery

This project is a simple Java-based peer-to-peer (P2P) networking application. It features a central server that facilitates the discovery of clients, allowing them to communicate directly with each other.

## Features

*   **Client/Server Architecture**: A central server manages a list of connected clients.
*   **Peer Discovery**: Clients register with the server to discover other peers on the network.
*   **Direct Peer-to-Peer Communication**: Once discovered, clients can communicate directly with each other.
*   **Custom Packet Structure**: A defined packet format for network communication.

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

*   Java 17 or later
*   Apache Maven

### Building

To build the project and create a runnable JAR file, run the following command from the root directory:

```sh
mvn clean package
```

This will create a file named `module-networking-1.0-SNAPSHOT.jar` in the `target` directory.

### Running

You can run the application as either a server or a client.

#### Running the Server

To start the server, run the following command, replacing `<ip_address>` with your machine's local IP address and `<port>` with the desired port number:

```sh
java -jar target/module-networking-1.0-SNAPSHOT.jar <ip_address> <port> <port>
```

For example:

```sh
java -jar target/module-networking-1.0-SNAPSHOT.jar 192.168.1.5 12000 12000
```

#### Running a Client

To start a client, use the same command but provide the server's IP and port, along with a unique local port for the client:

```sh
java -jar target/module-networking-1.0-SNAPSHOT.jar <server_ip> <server_port> <client_port>
```

For example:

```sh
java -jar target/module-networking-1.0-SNAPSHOT.jar 192.168.1.5 12000 12001
```

## Packet Structure

The application uses a custom packet structure for all network communication. The packet is divided into a header and a payload.

### Header (20 bytes)

| Bits  | Field           | Description                               |
| :---- | :-------------- | :---------------------------------------- |
| 0-1   | Type            | The type of packet (e.g., HELLO, DATA).   |
| 2-4   | Priority        | The priority of the packet.               |
| 5-8   | Module          | The source or destination module.         |
| 9-11  | Connection Type | The type of connection (e.g., NEW, ACK).  |
| 12    | Broadcast       | A flag indicating if the packet is a broadcast. |
| 13-15 | (empty)         | Reserved for future use.                  |
| 16-47 | IPv4 Address    | The 32-bit IPv4 address.                  |
| 48-63 | Port Number     | The 16-bit port number.                   |
| 64-95 | Message ID      | A unique identifier for the message.      |
| 96-127| Chunk Number    | The sequence number of the data chunk.    |
| 128-159| Chunk Length   | The length of the data chunk.             |

### Payload

The payload contains the actual data being transmitted and is of variable length.
