import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver {
    private static final String[] FLAGS = { "-", "S", "A", "F", "D" };

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
    private int mtu;
    private int sws;
    private String fileName;

    public Receiver(int p, int m, int s, String fname){
        this.port = p;
        this.mtu = m;
        this.sws = s;
        this.fileName = fname;
    }

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
            // TODO: how to get sender's IP address?

            DatagramSocket socket = new DatagramSocket(port);
            byte[] buffer = new byte[mtu];


            // Receive a TCP Packet
            DatagramPacket inboundPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(inboundPacket); // blocking!

            InetAddress senderIP = inboundPacket.getAddress();
            int senderPort = inboundPacket.getPort();


            if (receiver.isSYN(inboundPacket.getData())) {
                // Respond to Handshake
                receiver.handleSYN(receiver, socket, senderIP, senderPort);
            } else if (receiver.isACK(inboundPacket.getData())) {
                // Handle the ack packet
                receiver.handleACK(receiver, socket, senderIP, senderPort);
            } else if (receiver.isFIN(inboundPacket.getData())) {
                // Respond to a FIN with a FINACK
                receiver.handleFIN(receiver, socket, senderIP, senderPort);
            } else if (receiver.isDATA(inboundPacket.getData())) {
                // Handle the DATA packet
                receiver.handleDATA(receiver, socket, senderIP, senderPort);
            }
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

    private void sendACK(Receiver receiver, DatagramSocket socket, InetAddress senderIP, int senderPort) {

    }

    private void sendSYNACK(Receiver receiver, DatagramSocket socket, InetAddress senderIP, int senderPort) {

    }

    private void sendFINACK(Receiver receiver, DatagramSocket socket, InetAddress senderIP, int senderPort) {

    }
    

    /*
     * HANDLERS
     */


    // TODO -- we also have to output for received packets, find out where to do that


    // Method to handle a SYN packet only if we haven't received any packets (handshake)
    private void handleSYN(Receiver receiver, DatagramSocket socket, InetAddress senderIP, int senderPort) {
        if(totalPacketsReceived != 0) {
            return;
        }  

        // Increment total packet count
        totalPacketsReceived += 1;
        receiver.sendSYNACK(receiver, socket, senderIP, senderPort);
    }

    // Method to handle ACK reception
    private void handleACK(Receiver receiver, DatagramSocket socket, InetAddress senderIP, int senderPort) {
        // pseudo code for handlingACK here:

        /*
         * 1. update seq + ack number
         * 2. silently do nothing
         */
    }

    // Method to handle FIN reception
    private void handleFIN(Receiver receiver, DatagramSocket socket, InetAddress senderIP, int senderPort) {
        // pseudo code for handleFIN here:

        /*
         * 1. update seq + ack number
         * 2. craft ack/fin packet (set A+F flags)
         * 3. serialize to bytes array and send ack/fin packet via UDP
         * 4. close connection?
         */
    }

    // Method to handle received data segment
    private void handleDATA(Receiver receiver, DatagramSocket socket, InetAddress senderIP, int senderPort) throws IOException {
        // pseudo code for handleDATA here:

        /*
         * 1. update seq + ack number
         * 2. write data to buffer or file?? -- need to alter args
         * 3. craft ack packet (set A flags)
         * 4. serialize to bytes array and send ack packet via UDP
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
        System.out.printf("%d rcv %s %d %d %d\n", date.getTime(), flagList, seqNumber, numBytes, ackNumber);
    }

    private int extractSequenceNumber(byte[] header) {
        return (header[3] & 0xFF) << 24 |
               (header[2] & 0xFF) << 16 |
               (header[1] & 0xFF) << 8 |
               (header[0] & 0xFF);
    }

    private int extractAcknowledgmentNumber(byte[] header) {
        return (header[7] & 0xFF) << 24 |
               (header[6] & 0xFF) << 16 |
               (header[5] & 0xFF) << 8 |
               (header[4] & 0xFF);
    }

    private long extractTimestamp(byte[] header) {
        return (long)(header[15] & 0xFF) << 56 |
               (long)(header[14] & 0xFF) << 48 |
               (long)(header[13] & 0xFF) << 40 |
               (long)(header[12] & 0xFF) << 32 |
               (long)(header[11] & 0xFF) << 24 |
               (long)(header[10] & 0xFF) << 16 |
               (long)(header[9] & 0xFF) << 8 |
               (long)(header[8] & 0xFF);
    }

    private int extractDataLength(byte[] header) { // 0001 1111
        return (header[19] & 0x1F) << 24 |
               (header[18] & 0xFF) << 16 |
               (header[17] & 0xFF) << 8 |
               (header[16] & 0xFF);
    }

    private int extractChecksum(byte[] header) {
        return (header[23] & 0xFF) << 8 |
               (header[22] & 0xFF);
    }

    // byte 19 [ - | S | F | A ]
    // For Handshake
    private boolean isSYN(byte[] data) {
        int flags = (int) (data[19]);
        System.out.println("isSYN: " + flags);
        return ((flags & 0b0100) == 0b0100);
    }

    private boolean isACK(byte[] data) {
        int flags = (int) (data[19]);
        System.out.println("isACK: " + flags);
        return ((flags & 0b0001) == 0b0001);
    }

    private boolean isFIN(byte[] data) {
        int flags = (int) (data[19]);
        System.out.println("isFIN: " + flags);
        return ((flags & 0b0010) == 0b0010);
    }

    private boolean isDATA(byte[] data) {
        System.out.println("isDATA");
        return (extractDataLength(data) > 0);
    }

}
