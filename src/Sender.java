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

    public Sender(int p, String remIP, int remPort, String fname, int m, int s) {
        this.port = p;
        this.remoteIP = remIP;
        this.remotePort = remPort;
        this.fileName = fname;
        this.mtu = m;
        this.sws = s;
        // Leave space for the header
        this.buffer = new byte[mtu - HEADER_SIZE];

        try {
            this.socket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        try {
            this.remoteAddress = InetAddress.getByName(remoteIP);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /*
     * STARTUP CODE
     */

    public void startHost() {
        // Attempt handshake
        try {
            this.handshake();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.startThreads();
    }

    private void startThreads() {
        Thread senderThread = new Thread(() -> {
            try {
                this.sendingThreadFunc();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread receiverThread = new Thread(() -> {
            try {
                this.receivingThreadFunc();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        senderThread.start();
        receiverThread.start();
    }

    private void sendingThreadFunc() throws IOException {
        // Open the file for reading
        FileInputStream fileInputStream = new FileInputStream(fileName);
        int bytesRead;

        // buffer is of size mtu
        while ((bytesRead = fileInputStream.read(this.buffer)) != -1) {
            // Book-keeping
            this.sequenceNumber += bytesRead;
            this.totalDataTransferred += bytesRead;
            
            byte[] data = new byte[bytesRead];
            System.arraycopy(buffer, 0, data, 0, bytesRead);    

            // Send data segment
            this.sendDATA(data);
        }

        fileInputStream.close();
    }

    private void receivingThreadFunc() throws IOException {
        // Receive forever (until we are done sending)
        while (true) {
            // Wait for any inbound packet type
            DatagramPacket inboundPacket = new DatagramPacket(this.buffer, this.buffer.length);
            socket.receive(inboundPacket); // blocking!

            // TODO: handle checksum!!!

            // Handle different types of inbound packets
            if (isFIN(inboundPacket.getData())) {
                this.handleFIN();
            } else if (isACK(inboundPacket.getData())) {
                this.handleACK();
            }
        }
    }

    // Method to handle TCP handshake only if no packets have been sent
    private void handshake() throws IOException {
        this.socket = new DatagramSocket(port);
        this.remoteAddress = InetAddress.getByName(remoteIP);

        // only start the connection if there have been no packets sent
        if (totalPacketsSent != 0) {
            return;
        }
        try {
            byte[] empty_data = new byte[0];

            // Send SYN packet
            this.sendSYN(empty_data);

            byte[] in_buffer = new byte[this.mtu];

            // Wait for SYN-ACK from receiver
            DatagramPacket synackPacket = new DatagramPacket(in_buffer, in_buffer.length);
            socket.receive(synackPacket); // blocking!

            // Process SYN-ACK packet
            if (this.isSYNACK(synackPacket.getData())) {
                // Handle the ack packet
                this.handleACK();
                // Send ACK to complete handshake


                this.sendACK(empty_data);
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
    private void sendUDPPacket(byte[] data, String flagList, InetAddress receiverIP, int receiverPort)
            throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, receiverIP, receiverPort);
        socket.send(packet);

        // Output information about the sent packet
        outputSegmentInfo(flagList, data.length);
    }

    private void sendSYN(byte[] data) {
        // this.socket, this.remoteAddress, this.remotePort
    }

    private void sendACK(byte[] data) {
        // this.socket, this.remoteAddress, this.remotePort
    }

    private void sendFIN(byte[] data) {
        // this.socket, this.remoteAddress, this.remotePort
    }

    private void sendDATA(byte[] data) {
        // this.socket, this.remoteAddress, this.remotePort
    }

    /*
     * HANDLERS
     */

    // TODO -- we also have to output for received packets, find out where to do
    // that

    // Method to handle FIN segment and close connection
    private void handleFIN() {
        // pseudo code for handleFin here:

        // this.socket, this.remoteAddress, this.remotePort

        /*
         * 1. update seq + ack number
         * 2. craft ack packet (set A flag)
         * 3. serialize to bytes array and send ack packet via UDP
         * 4. close connection
         */
    }

    // Method to handle ACK reception
    private void handleACK() {
        // pseudo code for handleAck here:

        // this.socket, this.remoteAddress, this.remotePort

        // copy over the ack number of the received packet to our seq number
        // copy over the seq number of the received packet + 1 to our ack number
        this.sequenceNumber += this.buffer.length;
        // this.ackNumber += 
        /*
         * 1. update seq + ack number
         * 2. check to see if the ack number is = total size + 1 (for the handshake)
         * 2a. if so, craft a fin packet (set F flag)
         * 2b. serialize to bytes array and send fin packet via UDP
         */
    }

    // Method to handle DATA reception
    private void handleDATA() {
        // pseudo code for handleAck here:

        // this.socket, this.remoteAddress, this.remotePort

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
    private void outputSegmentInfo(String flagList, int numBytes) {
        Date date = new Date();
        // Need both snd and rcv ability
        System.out.printf("%d snd %s %d %s %d %d %d\n", date.getTime(), flagList, this.sequenceNumber, numBytes,
                this.ackNumber);
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