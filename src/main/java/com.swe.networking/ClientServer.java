package com.swe.networking;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Represents a node in a network that can function as both a client and a server.
 * It manages a list of clients within its own cluster and communicates with a
 * main server to interact with nodes in other clusters.
 */
public final class ClientServer {

    /** The default port number for this server node to listen on. */
    private static final int DEFAULT_PORT = 12000;
    /** The default size for packet buffers. */
    private static final int BUFFER_SIZE = 1024;
    /** Packet type identifier for a HELLO message. */
    private static final int PACKET_TYPE_HELLO = 3;
    /** Connection type identifier for a new connection. */
    private static final int CONN_TYPE_NEW = 0;

    /** The hostname of this client/server. */
    private final InetAddress hostName;
    /** The port on which this client/server is running. */
    private final int port;

    /** The hostname of the focal server. */
    private final InetAddress serverHostname;
    /** The port on which the focal server is running. */
    private final int serverPort;

    /**
     * A record to immutably store information about a client node.
     *
     * @param hostName The IP address of the client.
     * @param port     The port number of the client.
     */
    public record ClientNode(InetAddress hostName, int port) {
    }

    /** All the clients in owns cluster. */
    private final List<ClientNode> clients;
    /** Receiving socket for this client/server. */
    private final ServerSocket receiveSocket;

    /**
     * Constructs a ClientServer instance.
     * It determines its own host IP, starts a server socket to listen for connections,
     * and registers with the main cluster server unless it is the server itself.
     *
     * @param sHostname The IP address of the main cluster server.
     * @param sPort     The port number of the main cluster server.
     * @param srcPort   The port at which TCP server is hosted for this instance.
     * @throws IOException If an I/O error occurs when opening the server socket.
     */
    public ClientServer(final InetAddress sHostname, final int sPort, final int srcPort) throws IOException {
        try {
            // UPDATED: Call the new method to get the correct local network IP
            this.hostName = getLocalNetworkIp();
            System.out.println("Successfully determined local IP: " + this.hostName.getHostAddress());
        } catch (final Exception e) {
            System.err.println("Could not get local IP address: " + e.getMessage());
            throw new RuntimeException("Failed to get local IP", e);
        }
        this.port = srcPort;

        this.serverHostname = sHostname;
        this.serverPort = sPort;

        this.clients = new ArrayList<>();

        try {
            this.receiveSocket = new ServerSocket(this.port);
        } catch (final IOException e) {
            System.err.println("TCP server error: " + e.getMessage());
            throw e;
        }

        System.out.println(this.hostName);
        System.out.println(this.serverHostname);

        if (this.serverHostname.equals(this.hostName) && this.port == this.serverPort) {
            clients.add(new ClientNode(this.hostName, this.port));
            System.out.println("This is server");
        } else {
            System.out.println("This is client");
            sendHello(this.hostName, this.port, this.serverHostname, this.serverPort);
        }

        printClients();
    }

    /**
     * Iterates through all network interfaces to find the primary, non-loopback,
     * IPv4 address of the local machine.
     *
     * @return The local network {@link InetAddress}.
     * @throws SocketException if an I/O error occurs.
     */
    private static InetAddress getLocalNetworkIp() throws SocketException {
        final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            final NetworkInterface ni = networkInterfaces.nextElement();
            // Skip interfaces that are down, loopback, or virtual
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                continue;
            }
            final Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                final InetAddress address = inetAddresses.nextElement();
                // Look for a site-local IPv4 address
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                    return address;
                }
            }
        }
        // If no suitable address was found, throw an exception.
        throw new SocketException("Could not find a suitable local network IP address.");
    }

    /**
     * Checks if a destination address belongs to a client in the local cluster.
     *
     * @param ip   The IP address to check.
     * @param destPort The destPort to check.
     * @return true if the destination is in the cluster, false otherwise.
     */
    private boolean destInCluster(final InetAddress ip, final int destPort) {
        return clients.contains(new ClientNode(ip, destPort));
    }

    /**
     * Listens for a single incoming connection, reads the packet, and processes it.
     * This method uses a try-with-resources statement to ensure the socket is closed.
     */
    public void receiveFrom() {
        try (Socket commSocket = this.receiveSocket.accept()) {
            final DataInputStream dataIn = new DataInputStream(commSocket.getInputStream());
            final byte[] packet = dataIn.readAllBytes();

            final InetAddress sourceIp = commSocket.getInetAddress();
            final int sourcePort = commSocket.getPort();

            final PacketParser parser = PacketParser.getPacketParser();
            System.out.println("Received packet from: " + sourceIp + ":" + sourcePort);

            if (serverHostname.equals(this.hostName) && port == this.serverPort) {
                final int type = parser.getType(packet);
                final int connectionType = parser.getConnectionType(packet);

                if (type == PACKET_TYPE_HELLO && connectionType == CONN_TYPE_NEW) {
                    receiveHello(parser.getPayload(packet)); // Assuming the payload is the relevant part
                    printClients();
                }
            } else {
                final int type = parser.getType(packet);
                final int connectionType = parser.getConnectionType(packet);

                if (type == PACKET_TYPE_HELLO && connectionType == CONN_TYPE_NEW) {
                    receiveHello(packet);
                    printClients();
                }
                // to be done callMessageListener()
            }
            System.out.println("Packet processed successfully.");

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends data to a specified destination.
     * This method creates a new socket for each send operation to ensure thread safety
     * and prevent connection errors.
     *
     * @param data The byte array of data to send.
     * @param dest The destination IP address.
     * @param destPort The destination port.
     * @return 0 on success, -1 on failure.
     */
    public int sendTo(final byte[] data, final InetAddress dest, final int destPort) {
        if (serverHostname.equals(this.hostName) && port == this.serverPort) {
            // --- This is the Server's logic ---
            if (destInCluster(dest, destPort)) {
                try (Socket socket = new Socket(dest, destPort)) {
                    final DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
                    dataOut.write(data);
                } catch (final IOException e) {
                    System.err.println("Server error sending to in-cluster client: " + e.getMessage());
                    return -1;
                }
            } else {
                /** Final variable. */
                final int variable = 0;
                // Destination is in another cluster (logic to be implemented via Topology)
                /*
                try (Socket socket = new Socket(topology.getServer(dest).hostName(), port)) {
                    // getServer to be implemented by Topology.
                    final DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
                    dataOut.write(data);
                } catch (final IOException e) {
                    System.err.println("Server error sending to other cluster: " + e.getMessage());
                    return -1;
                }
                */
            }
        } else {
            // --- This is the Client's logic ---
            if (destInCluster(dest, destPort)) {
                try (Socket socket = new Socket(dest, destPort)) {
                    final DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
                    dataOut.write(data);
                } catch (final IOException e) {
                    System.err.println("Client error sending to peer: " + e.getMessage());
                    return -1;
                }
            } else {
                // Destination is outside the cluster, send it to our server to forward it
                try (Socket socket = new Socket(serverHostname, serverPort)) {
                    final DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
                    dataOut.write(data);
                } catch (final IOException e) {
                    System.err.println("Client error sending to server for forwarding: " + e.getMessage());
                    return -1;
                }
            }
        }
        return 0;
    }

    /**
     * Creates a 64-bit packet header from its constituent parts.
     *
     * @param type           The packet type (2 bits).
     * @param priority       The priority level (3 bits).
     * @param module         The source/destination module (4 bits).
     * @param connectionType The connection type (3 bits).
     * @param broadcast      The broadcast flag (1 bit).
     * @param empty          Reserved bits (3 bits).
     * @param ip             Placeholder for IP (32 bits).
     * @param prt           Placeholder for Port (16 bits).
     * @return A 64-bit header packed into a long.
     */
    private long createPacketHeader(final int type, final int priority, final int module, final int connectionType,
                                    final int broadcast, final int empty, final int ip, final int prt) {
        long packetHeader = 0;
        final int typeShift = 62;
        final int priorityShift = 59;
        final int moduleShift = 55;
        final int connTypeShift = 52;
        final int broadcastShift = 51;
        final int emptyShift = 48;
        final int ipShift = 16;

        packetHeader |= (long) type << typeShift;
        packetHeader |= (long) priority << priorityShift;
        packetHeader |= (long) module << moduleShift;
        packetHeader |= (long) connectionType << connTypeShift;
        packetHeader |= (long) broadcast << broadcastShift;
        packetHeader |= (long) empty << emptyShift;
        packetHeader |= (long) ip << ipShift;
        packetHeader |= prt;
        return packetHeader;
    }

    /**
     * Constructs and sends a HELLO packet from this node to the main server.
     *
     * @param payloadIp   The IP address to be sent in payload.
     * @param payloadPort The port to be sent in payload.
     * @param destIp The destination IP address.
     * @param destPort The destination port.
     * @throws IOException If the send operation fails.
     */
    private void sendHello(final InetAddress payloadIp, final int payloadPort, final InetAddress destIp, final int destPort) throws IOException {
        final long packetHeader = createPacketHeader(PACKET_TYPE_HELLO, 0, 0, CONN_TYPE_NEW, 0, 0, 0, 0);
        final byte[] addressBytes = payloadIp.getAddress();

        final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        buffer.putLong(packetHeader);
        System.out.println(addressBytes.length);
        buffer.putInt(addressBytes.length);
        buffer.put(addressBytes);
        buffer.putInt(payloadPort);

        // Create a new byte array with the exact size of the data written
        final byte[] packet = new byte[buffer.position()];
        buffer.flip(); // Prepare buffer for reading
        buffer.get(packet); // Read data into the new array

        System.out.println(buffer);

        this.sendTo(packet, destIp, destPort);
    }

    /**
     * Processes a received HELLO packet's payload.
     *
     * @param payload The payload byte array from the packet.
     */
    private void receiveHello(final byte[] payload) {
        InetAddress receivedIp = null;
        int receivedPort = -1;

        final ByteBuffer buffer = ByteBuffer.wrap(payload);
        try {
            // Parse the payload using ByteBuffer
            final int ipSize = buffer.getInt();
            System.out.println(ipSize);
            final byte[] addressBytes = new byte[ipSize];
            buffer.get(addressBytes);
            receivedIp = InetAddress.getByAddress(addressBytes);
            receivedPort = buffer.getInt();

            final ClientNode newClient = new ClientNode(receivedIp, receivedPort);

            if (!clients.contains(newClient)) {
                clients.add(newClient);
                System.out.println("Added new client to cluster: " + newClient);
            }

        } catch (final UnknownHostException e) {
            System.err.println("Error parsing IP address from hello packet.");
            e.printStackTrace();
        } catch (final java.nio.BufferUnderflowException e) {
            System.err.println("Error parsing hello packet, buffer underflow. Packet may be malformed.");
            e.printStackTrace();
        }

        if (this.serverHostname.equals(this.hostName) &&  this.port == this.serverPort) {
            final long packetHeader = createPacketHeader(PACKET_TYPE_HELLO, 0, 0, CONN_TYPE_NEW, 0, 0, 0, 0);
            final ByteBuffer broadcastBuffer = ByteBuffer.allocate(BUFFER_SIZE);
            broadcastBuffer.putLong(packetHeader);
            broadcastBuffer.put(payload);

            final byte[] packet = new byte[broadcastBuffer.position()];
            broadcastBuffer.flip();
            broadcastBuffer.get(packet);

            for (final ClientNode client : this.clients) {
                if (!(client.hostName.equals(this.hostName) && client.port == this.port)) {
                    // Forward IP and port of newly added client to each existing client
                    this.sendTo(packet, client.hostName, client.port);
                }

                // Send back each existing client info to the newly added client
                try {
                    this.sendHello(client.hostName, client.port, receivedIp, receivedPort);
                } catch (final IOException e) {
                    System.err.println("Client error sending to server for forwarding: " + e.getMessage());
                    e.printStackTrace();
                }

            }
        }

    }

    public void printClients() {
        // Synchronize on the clients list to prevent concurrent modification issues
        // if you plan to access this from multiple threads in the future.
        synchronized (clients) {
            System.out.println("\n--- Current Client List (" + clients.size() + " nodes) ---");
            if (clients.isEmpty()) {
                System.out.println("The client list is empty.");
            } else {
                int i = 1;
                for (final ClientNode client : clients) {
                    System.out.println(
                            (i++) + ". IP: " + client.hostName().getHostAddress() + ", Port: " + client.port()
                    );
                }
            }
            System.out.println("------------------------------------\n");
        }
    }
}