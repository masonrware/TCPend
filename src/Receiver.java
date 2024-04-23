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

    private byte[] createHeader(int length, int afs){
        // seqnum and acknum are globals, get length and afs from cmd line
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try {
            // Create a DataOutputStream to write data to the ByteArrayOutputStream
            DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

            // Write different data types to the byte array
            dataOutputStream.writeInt(this.sequenceNumber); 
            dataOutputStream.writeInt(this.ackNumber); 
            dataOutputStream.writeLong(System.nanoTime()); 
            dataOutputStream.writeInt((length << 3) | afs);
            dataOutputStream.writeInt(0);

            // Close the DataOutputStream
            dataOutputStream.close();

            // Get the byte array
            byte[] byteArray = byteArrayOutputStream.toByteArray();

            // Print the byte array
            return byteArray;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        
    }

    private int getChecksum(byte[] data){
        int sum = 0;
        int carry = 0;

        // Pad the data with 0x00 if the length is odd
        byte[] paddedData = data.length % 2 == 0 ? data : Arrays.copyOf(data, data.length + 1);

        // Calculate the sum of 16-bit segments
        for (int i = 0; i < paddedData.length; i += 2) {
            int segment = ((paddedData[i] & 0xFF) << 8) | (paddedData[i + 1] & 0xFF);
            sum += segment;
            if ((sum & 0xFFFF0000) != 0) {
                sum &= 0xFFFF;
                carry++;
            }
        }

        // Add carry to the least significant bit
        while (carry > 0) {
            sum++;
            if ((sum & 0xFFFF0000) != 0) {
                sum &= 0xFFFF;
                carry++;
            } else {
                break;
            }
        }

        // Flip all 16 bits to get the checksum
        return ~sum & 0xFFFF;
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

    private int extractDataLength(byte[] header) { 
        // Must disregard last 3 bits (SFA flags)
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
        return ((flags & 0b0010) == 0b0010);
    }

    private boolean isACK(byte[] data) {
        int flags = (int) (data[19]);
        System.out.println("isACK: " + flags);
        return ((flags & 0b1000) == 0b1000);
    }

    private boolean isFIN(byte[] data) {
        int flags = (int) (data[19]);
        System.out.println("isFIN: " + flags);
        return ((flags & 0b0100) == 0b0100);
    }

    private boolean isDATA(byte[] data) {
        System.out.println("isDATA: " + extractDataLength(data));
        return (extractDataLength(data) > 0);
    }

}
