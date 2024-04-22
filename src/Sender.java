import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private static final String[] FLAGS = { "-", "S", "A", "F", "D" };
    private static final int MAX_RETRANSMISSIONS = 3;

    // Variables to track statistics
    private int totalDataTransferred = 0;
    private int totalDataReceived = 0;
    private int totalPacketsSent = 0;
    private int totalPacketsReceived = 0;
    private int totalRetransmissions = 0;
    private int totalOutOfSequencePackets = 0;
    private int totalPacketsWithIncorrectChecksum = 0;
    private int totalDuplicateAcks = 0;

    private int sequenceNumber = 0;
    private int ackNumber = 0;

    private int port;
    private String remoteIP;
    private int remotePort;
    private String fileName;
    private int mtu;
    private int sws;

    public Sender(int p, String remIP, int remPort, String fname, int m, int s){
        this.port = p;
        this.remoteIP = remIP;
        this.remotePort = remPort;
        this.fileName = fname;
        this.mtu = m;
        this.sws = s;
    }

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
            
            // Attempt handshake
            sender.start_connection(sender, socket, remoteAddress, remotePort, buffer);

            // TODO -- how to send packets indefinitely??

            // Wait for any inbound packet type
            DatagramPacket inboundPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(inboundPacket); // blocking!

            // Handle different types of inbound packets
             if (sender.isFIN(inboundPacket.getData())) {
                // TODO: handle fin -- send back an ack and output statistics
                sender.handleFIN(sender, socket, remoteAddress, port);
            } else if (sender.isACK(inboundPacket.getData())) {
                // TODO: handle ack -- potentially close connection
                sender.handleACK(sender, socket, remoteAddress, remotePort);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to handle TCP handshake only if no packets have been sent
    private void start_connection(Sender sender, DatagramSocket socket, InetAddress remoteAddress, int remotePort, byte[] buffer) throws IOException {
        // only start the connection if there have been no packets sent
        if(totalPacketsSent != 0) {
            return;
        }
        try {
            // Send SYN packet
            sender.sendSYN(sender, socket, remoteAddress, remotePort);

            // Wait for SYN-ACK from receiver
            DatagramPacket synackPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(synackPacket); // blocking!

            // Process SYN-ACK packet
            if (sender.isSYNACK(synackPacket.getData())) {
                // Handle the ack packet
                sender.handleACK(sender, socket, remoteAddress, remotePort);
                // Send ACK to complete handshake
                sender.sendACK(sender, socket, remoteAddress, remotePort);
            } else {
                socket.close();
                throw new IOException("Handshake Failed -- did not receive SYN-ACK from receiver.");
            }

            // Only increment total packet count if handshake succeeds
            totalPacketsSent += 2;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * SENDERS
     */

    // Method to send UDP packet
    private void sendUDPPacket(DatagramSocket socket, InetAddress receiverIP, int receiverPort, byte[] data, String flagList) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, receiverIP, receiverPort);
        socket.send(packet);

        // Output information about the sent packet
        outputSegmentInfo(flagList, sequenceNumber, data.length, -1);
    }

    private void sendSYN(Sender sender, DatagramSocket socket, InetAddress remoteAddress, int remotePort) {

    }

    private void sendACK(Sender sender, DatagramSocket socket, InetAddress remoteAddress, int remotePort) {

    }

    private void sendFIN(Sender sender, DatagramSocket socket, InetAddress remoteAddress, int remotePort) {

    }

    private void sendDATA(Sender sender, DatagramSocket socket, InetAddress remoteAddress, int remotePort) {

    }

    /*
     * HANDLERS
     */

    // TODO -- we also have to output for received packets, find out where to do that

    // Method to handle FIN segment and close connection
    private void handleFIN(Sender sender, DatagramSocket socket, InetAddress receiverIP, int receiverPort) {
        // pseudo code for handleFin here:

        /*
         * 1. update seq + ack number
         * 2. craft ack packet (set A flag)
         * 3. serialize to bytes array and send ack packet via UDP
         * 4. close connection
         */
    }

    // Method to handle ACK reception
    private void handleACK(Sender sender, DatagramSocket socket, InetAddress receiverIP, int receiverPort) {
        // pseudo code for handleAck here:

        /*
         * 1. update seq + ack number
         * 2. check to see if the ack number is = total size + 1 (for the handshake)
         *  2a. if so, craft a fin packet (set F flag)
         *  2b. serialize to bytes array and send fin packet via UDP
         */
    }

    // Method to handle DATA reception
    private void handleDATA(Sender sender, DatagramSocket socket, InetAddress receiverIP, int receiverPort) {
        // pseudo code for handleAck here:

        /*
        * 1. update seq + ack number
        * 2. craft data packet (set A+D flags)
        * 3. serialize to bytes array and send ack/data packet via UDP
        */
    }

    /*
     * MISC.
     */
    
    // Method to close the connection and print statistics
    private void printStatistics() {
        // Implement closing logic and print statistics here
    }
    
    // Method to output segment information
    private void outputSegmentInfo(String flagList, int seqNumber, int numBytes, int ackNumber) {
        Date date = new Date();
        // Need both snd and rcv ability
        System.out.printf("%d snd %s %d %s %d %d %d\n", date.getTime(), flagList, seqNumber, numBytes, ackNumber);
    }

    // For Handshake
    private boolean isSYNACK(byte[] data) {
        // Check if the packet is a SYNACK packet
        // Implement logic to check if the packet is a SYNACK packet
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