import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver {
    private static final String[] FLAGS = { "-", "S", "A", "F", "D" };

    // Variables to track statistics
    private int totalDataReceived = 0;
    private int totalPacketsReceived = 0;
    private int totalOutOfSequencePackets = 0;
    private int totalPacketsWithIncorrectChecksum = 0;
    private int totalDuplicateAcks = 0;

    private int sequenceNumber = 0;
    private int ackNumber = 0;

    public static void main(String[] args) {
        if (args.length != 8 || !args[0].equals("-p") || !args[2].equals("-m") || !args[4].equals("-c") || !args[6].equals("-f")) {
            System.out.println("Usage: java Receiver -p <port> -m <mtu> -c <sws> -f <file name>");
            return;
        }

        int port = Integer.parseInt(args[1]);
        int mtu = Integer.parseInt(args[3]);
        int sws = Integer.parseInt(args[5]);
        String fileName = args[7];

        Receiver receiver = new Receiver();

        try {
            DatagramSocket socket = new DatagramSocket(port);
            byte[] buffer = new byte[mtu];

            // TCP Handshake Code
            DatagramPacket synPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(synPacket);
            // Process SYN packet
            if (receiver.isSYN(synPacket.getData())) {
                InetAddress senderIP = synPacket.getAddress();
                int senderPort = synPacket.getPort();
                // Send SYN-ACK packet
                receiver.sendSYNACK(socket, senderIP, senderPort);
            }
            // Wait for ACK from sender
            DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(ackPacket);
            // Process ACK packet
            if (receiver.isACK(ackPacket.getData())) {

                // Post handshake
                
                // TODO: we have to send and receive at the same time -- threading???
                // Handshake successful, start receiving data segments
                while (true) {
                    DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(dataPacket);

                    // TODO: handle different types of potential packets -- (no need for syn, handled above), data/ack, fin, ack ...
                    // Process received data segment
                    // receiver.handleDataSegment(dataPacket);
                }
            } else {
                // TODO: handshake not set up properly?
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * HANDLERS (OUTSIDE OF HANDSHAKE)
     */

    // Method to handle ACK reception
    private void handleACK(int ackNumber) {
        // Implement ACK handling logic here
    }

    // Method to handle received data segment
    private void handleDataSegment(DatagramSocket socket, InetAddress senderIP, int senderPort, byte[] data) throws IOException {
        // TODO: Extract sequence number and data from the packet
        // int sequenceNumber = ...; // Extract sequence number from the data portion of the packet
        // byte[] data = ...; // Extract data from the packet

        // Process received data, store in buffer, etc.
        // Send ACK for the last successfully received contiguous byte
        sendACK(socket, senderIP, senderPort, sequenceNumber + data.length);
    }

    // Method to handle FIN segment and close connection
    private void handleFIN(DatagramSocket socket, InetAddress senderIP, int senderPort) {
        // Implement handling FIN logic here
    }

    /*
     * SENDERS
     */

    // Method to send UDP packet
    private void sendPacket(DatagramSocket socket, InetAddress receiverIP, int receiverPort, byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, receiverIP, receiverPort);
        socket.send(packet);
        // Output information about the sent packet
        outputSegmentInfo("D", sequenceNumber, data.length, -1);
    }

    private void sendSYNACK(DatagramSocket socket, InetAddress senderIP, int senderPort) throws IOException {
        // TODO: Construct SYN-ACK packet data
        byte[] synackData = { /* Construct SYN-ACK packet data */ };
        // Send SYN-ACK packet
        sendPacket(socket, senderIP, senderPort, synackData);
    }

    // Method to send ACK segment
    private void sendACK(DatagramSocket socket, InetAddress senderIP, int senderPort, int ackNumber) throws IOException {
            // Construct ACK packet data
        byte[] ackData = { /* Construct ACK packet data */ };        
        // Send the ACK packet
        sendPacket(socket, senderIP, senderPort, ackData);
    }

    // Method to send FIN segment and close connection
    private void sendFINACK(DatagramSocket socket, InetAddress receiverIP, int receiverPort) {
        // Implement FIN segment sending logic here
    }

    /*
     * MISC.
     */

    // Method to close the connection and print statistics
    private void closeConnectionAndPrintStatistics() {
        // Implement closing logic and print statistics here
    }
    
    // Method to output segment information
    private void outputSegmentInfo(String flagList, int seqNumber, int numBytes, int ackNumber) {
        Date date = new Date();
        // Need both snd and rcv ability
        System.out.printf("%d rcv %s %d %d %d\n", date.getTime(), flagList, seqNumber, numBytes, ackNumber);
    }

    private boolean isSYN(byte[] data) {
        // Check if the packet is a SYN packet
        // Implement logic to check if the packet is a SYN packet
        return true; // Placeholder, actual implementation depends on protocol
    }

    private boolean isACK(byte[] data) {
        // Check if the packet is a ACK packet
        // Implement logic to check if the packet is a ACK packet
        return true; // Placeholder, actual implementation depends on protocol
    }

    private boolean isFIN(byte[] data) {
        // Check if the packet is a FIN packet
        // Implement logic to check if the packet is a FIN packet
        return true; // Placeholder, actual implementation depends on protocol
    }
}
