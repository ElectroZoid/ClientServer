package com.swe.networking;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java com.swe.networking.Main <server_ip> <server_port> <local_port>");
            System.out.println("Example (for the server): java com.swe.networking.Main 127.0.0.1 12000 12000");
            System.out.println("Example (for a client): java com.swe.networking.Main 127.0.0.1 12000 12001");
            return;
        }

        try {
            // Parse command-line arguments
            final InetAddress serverIp = InetAddress.getByName(args[0]);
            final int serverPort = Integer.parseInt(args[1]);
            final int localPort = Integer.parseInt(args[2]);

            // Create an instance of your ClientServer
            System.out.println("Starting node on port " + localPort + ", connecting to server at " + serverIp + ":" + serverPort);
            final ClientServer node = new ClientServer(serverIp, serverPort, localPort);

            // Keep the server running and listening for connections
            // The receiveFrom() method only accepts one connection, so we put it in a loop.
            while (true) {
                System.out.println("Waiting for a connection on port " + localPort + "...");
                node.receiveFrom();
            }

        } catch (UnknownHostException e) {
            System.err.println("Error: Invalid server IP address. " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Error: Ports must be valid numbers. " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}