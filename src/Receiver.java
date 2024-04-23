import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver {
    private static final String[] FLAGS = { "-", "S", "A", "F", "D" };
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
    private int mtu;
    private int sws;
    private String fileName;
    private DatagramSocket socket;
    private byte[] buffer;

    public Receiver(int p, int m, int s, String fname){
        this.port = p;
        this.mtu = m;
        this.sws = s;
        this.fileName = fname;
        this.buffer = new byte[mtu];

        try {
            this.socket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /*
     * STARTUP CODE
     */

    public void startHost() {
        this.startThreads();
    }

    private void startThreads() {
        Thread receiverThread = new Thread(() -> {
            try {
                this.receivingThreadFunc();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        receiverThread.start();
    }

    // private void sendMessages(int port, String remoteIP, int remotePort, String fileName, int mtu, int sws) throws IOException {
    //     // TODO -- how to send packets indefinitely??
    // }

    private void receivingThreadFunc() throws IOException {
        // Receive forever (until we get a FIN)
        while(true) {
            try {
                // Receive a TCP Packet (for handshake)
                DatagramPacket inboundPacket = new DatagramPacket(this.buffer, this.buffer.length);
                socket.receive(inboundPacket); // blocking!

                // These should not be class-based because they might (won't) come from different sources
                InetAddress senderIP = inboundPacket.getAddress();
                int senderPort = inboundPacket.getPort();

                if (this.isSYN(inboundPacket.getData())) {
                    // Respond to Handshake
                    this.handleSYN(this.socket, senderIP, senderPort);
                } else if (this.isACK(inboundPacket.getData())) {
                    // Handle the ack packet
                    this.handleACK(this.socket, senderIP, senderPort);
                } else if (this.isFIN(inboundPacket.getData())) {
                    // Respond to a FIN with a FINACK
                    this.handleFIN(this.socket, senderIP, senderPort);
                } else if (this.isDATA(inboundPacket.getData())) {
                    // Handle the DATA packet
                    this.handleDATA(this.socket, senderIP, senderPort);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }  
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

    private void sendACK(DatagramSocket socket, InetAddress senderIP, int senderPort) {

    }

    private void sendSYNACK(DatagramSocket socket, InetAddress senderIP, int senderPort) {

    }

    private void sendFINACK(DatagramSocket socket, InetAddress senderIP, int senderPort) {

    }
    

    /*
     * HANDLERS
     */


    // TODO -- we also have to output for received packets, find out where to do that


    // Method to handle a SYN packet only if we haven't received any packets (handshake)
    private void handleSYN(DatagramSocket socket, InetAddress senderIP, int senderPort) {
        if(totalPacketsReceived != 0) {
            return;
        }  

        // Increment total packet count
        totalPacketsReceived += 1;
        this.sendSYNACK(socket, senderIP, senderPort);
    }

    // Method to handle ACK reception
    private void handleACK(DatagramSocket socket, InetAddress senderIP, int senderPort) {
        // pseudo code for handlingACK here:

        /*
         * 1. update seq + ack number
         * 2. silently do nothing
         */
    }

    // Method to handle FIN reception
    private void handleFIN(DatagramSocket socket, InetAddress senderIP, int senderPort) {
        // pseudo code for handleFIN here:

        /*
         * 1. update seq + ack number
         * 2. craft ack/fin packet (set A+F flags)
         * 3. serialize to bytes array and send ack/fin packet via UDP
         * 4. close connection?
         */
    }

    // Method to handle received data segment
    private void handleDATA(DatagramSocket socket, InetAddress senderIP, int senderPort) throws IOException {
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

    private boolean isDATA(byte[] data) {
        // Check if the packet is a DATA packet
        // Implement logic to check if the packet is a DATA packet
        return true; // Placeholder, actual implementation depends on protocol
    }
}
