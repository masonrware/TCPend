import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private static final String[] FLAGS = { "-", "S", "A", "F", "D" };
    private static final int MAX_RETRANSMISSIONS = 3;

    // Variables to track statistics
    private int totalDataTransferred = 0;
    private int totalPacketsSent = 0;
    private int totalRetransmissions = 0;

    private int sequenceNumber = 0;
    private int ackNumber = 0;

    public static void main(String[] args) {
        if (args.length != 10 || !args[0].equals("-p") || !args[2].equals("-s") || !args[4].equals("-a")
                || !args[6].equals("f") || !args[8].equals("-m") || !args[10].equals("-c")) {
            System.out.println("Usage: java Sender -p <port> -s <remote IP> -a <remote port> f <file name> -m <mtu> -c <sws>");
            return;
        }

        int port = Integer.parseInt(args[1]);
        String remoteIP = args[3];
        int remotePort = Integer.parseInt(args[5]);
        String fileName = args[7];
        int mtu = Integer.parseInt(args[9]);
        int sws = Integer.parseInt(args[11]);

        Sender sender = new Sender();

        try {
            DatagramSocket socket = new DatagramSocket(port);
            InetAddress remoteAddress = InetAddress.getByName(remoteIP);
            byte[] buffer = new byte[mtu];

            // TCP Handshake Code
            // Send SYN packet
            sender.sendSYN(socket, remoteAddress, remotePort);
            // Wait for SYN-ACK from receiver
            DatagramPacket synackPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(synackPacket);
            // Process SYN-ACK packet
            if (sender.isSYNACK(synackPacket.getData())) {
                // Send ACK to complete handshake
                sender.sendACK(socket, remoteAddress, remotePort);
            } else {
                // TODO: handshake not set up properly?
            }
            
            // Post handshake

            // TODO: we have to send and receive at the same time -- threading???
            // Start sending data segments
            while (true) {
                // TODO: Read data from file into buffer
                // Call sendDataSegment method to send the data segment
                sender.sendDataSegment(socket, remoteAddress, remotePort, buffer);
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

    // Method to handle retransmissions
    private void handleRetransmission(DatagramSocket socket, InetAddress receiverIP, int receiverPort, byte[] data) {
        // Implement retransmission logic here
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

    private void sendACK(DatagramSocket socket, InetAddress receiverIP, int receiverPort) throws IOException {
        // Construct ACK packet data
        byte[] ackData = { /* Construct ACK packet data */ };
        // Send ACK packet
        sendPacket(socket, receiverIP, receiverPort, ackData);
    }


    private void sendSYN(DatagramSocket socket, InetAddress receiverIP, int receiverPort) throws IOException {
        // Construct SYN packet data
        byte[] synData = { /* Construct SYN packet data */ };
        // Send SYN packet
        sendPacket(socket, receiverIP, receiverPort, synData);
    }

    // Method to send data segment
    private void sendDataSegment(DatagramSocket socket, InetAddress receiverIP, int receiverPort, byte[] data) {
        // Implement sending logic here
    }

    // Method to send FIN segment and close connection
    private void sendFIN(DatagramSocket socket, InetAddress receiverIP, int receiverPort) {
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
        System.out.printf("%d snd %s %d %s %d %d %d\n", date.getTime(), flagList, seqNumber, numBytes, ackNumber);
    }

    private boolean isSYNACK(byte[] data) {
        // Check if the packet is a SYN-ACK packet
        // Implement logic to check if the packet is a SYN-ACK packet
        return true; // Placeholder, actual implementation depends on protocol
    }

    private boolean isACK(byte[] data) {
        // Check if the packet is a ACK packet
        // Implement logic to check if the packet is a ACK packet
        return true; // Placeholder, actual implementation depends on protocol
    }

    private boolean isFINACK(byte[] data) {
        // Check if the packet is a FIN packet
        // Implement logic to check if the packet is a FIN packet
        return true; // Placeholder, actual implementation depends on protocol
    }
}