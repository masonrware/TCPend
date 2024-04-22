import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private static final String[] FLAGS = { "-", "S", "A", "F", "D" };
    private static final int MAX_RETRANSMISSIONS = 3;
    private static final int HEADER_SIZE = 24 * Byte.SIZE;

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
    private DatagramSocket socket;
    private InetAddress remoteAddress;
    private byte[] buffer;

    public Sender(int p, String remIP, int remPort, String fname, int m, int s){
        this.port = p;
        this.remoteIP = remIP;
        this.remotePort = remPort;
        this.fileName = fname;
        this.mtu = m;
        this.sws = s;
        this.buffer = new byte[mtu];

        // Attempt handshake
        try {
            this.startConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.startThreads();
    }

    /*
     * STARTUP CODE
     */

    private void startThreads() {
        Thread senderThread = new Thread(() -> {
            try {
                sendMessages();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread receiverThread = new Thread(() -> {
            try {
                receiveMessages();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        senderThread.start();
        receiverThread.start();
    }

    // TODO finish updating this -- add bytes read to total
    private void sendMessages() throws IOException {
        // Open the file for reading
        FileInputStream fileInputStream = new FileInputStream(fileName);
        int bytesRead;

        while ((bytesRead = fileInputStream.read(this.buffer)) != -1) {
            byte[] data = new byte[bytesRead];
            System.arraycopy(this.buffer, 0, data, 0, bytesRead);

            // Send data segment
            this.sendDATA(this.socket, this.remoteAddress, this.remotePort);
            sequenceNumber += bytesRead;
        }

        fileInputStream.close();
    }

    // TODO finish updating this
    private void receiveMessages() throws IOException {
        // Receive forever (until we are done sending)
        while(true) {
            DatagramSocket socket = new DatagramSocket(port);
            InetAddress remoteAddress = InetAddress.getByName(remoteIP);

            // Wait for any inbound packet type
            DatagramPacket inboundPacket = new DatagramPacket(this.buffer, this.buffer.length);
            socket.receive(inboundPacket); // blocking!

            // Handle different types of inbound packets
            if (isFIN(inboundPacket.getData())) {
                // TODO: handle fin -- send back an ack and output statistics
                handleFIN(socket, remoteAddress, port);
            } else if (isACK(inboundPacket.getData())) {
                // TODO: handle ack -- potentially close connection
                handleACK(socket, remoteAddress, remotePort);
            }
        }
    }


    // Method to handle TCP handshake only if no packets have been sent
    private void startConnection() throws IOException {
        this.socket = new DatagramSocket(port);
        this.remoteAddress = InetAddress.getByName(remoteIP);

        // only start the connection if there have been no packets sent
        if(totalPacketsSent != 0) {
            return;
        }
        try {
            // Send SYN packet
            this.sendSYN(socket, remoteAddress, remotePort);

            byte[] in_buffer = new byte[this.mtu];

            // Wait for SYN-ACK from receiver
            DatagramPacket synackPacket = new DatagramPacket(in_buffer, in_buffer.length);
            socket.receive(synackPacket); // blocking!

            // Process SYN-ACK packet
            if (this.isSYNACK(synackPacket.getData())) {
                // Handle the ack packet
                this.handleACK(this.socket, this.remoteAddress, this.remotePort);
                // Send ACK to complete handshake
                this.sendACK(this.socket, this.remoteAddress, this.remotePort);
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
    private void sendUDPPacket(byte[] data, String flagList, InetAddress receiverIP, int receiverPort) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, receiverIP, receiverPort);
        socket.send(packet);

        // Output information about the sent packet
        outputSegmentInfo(flagList, sequenceNumber, data.length, -1);
    }

    private void sendSYN(DatagramSocket socket, InetAddress remoteAddress, int remotePort) {

    }

    private void sendACK(DatagramSocket socket, InetAddress remoteAddress, int remotePort) {

    }

    private void sendFIN(DatagramSocket socket, InetAddress remoteAddress, int remotePort) {

    }

    private void sendDATA(DatagramSocket socket, InetAddress remoteAddress, int remotePort) {

    }

    /*
     * HANDLERS
     */

    // TODO -- we also have to output for received packets, find out where to do that

    // Method to handle FIN segment and close connection
    private void handleFIN(DatagramSocket socket, InetAddress receiverIP, int receiverPort) {
        // pseudo code for handleFin here:

        /*
         * 1. update seq + ack number
         * 2. craft ack packet (set A flag)
         * 3. serialize to bytes array and send ack packet via UDP
         * 4. close connection
         */
    }

    // Method to handle ACK reception
    private void handleACK(DatagramSocket socket, InetAddress receiverIP, int receiverPort) {
        // pseudo code for handleAck here:

        /*
         * 1. update seq + ack number
         * 2. check to see if the ack number is = total size + 1 (for the handshake)
         *  2a. if so, craft a fin packet (set F flag)
         *  2b. serialize to bytes array and send fin packet via UDP
         */
    }

    // Method to handle DATA reception
    private void handleDATA(DatagramSocket socket, InetAddress receiverIP, int receiverPort) {
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