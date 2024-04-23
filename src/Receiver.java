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

    public Receiver(int p, int m, int s, String fname) {
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

    private void receivingThreadFunc() throws IOException {
        // Receive forever (until we get a FIN)
        while (true) {
            try {
                // Receive a TCP Packet (for handshake)
                DatagramPacket inboundPacket = new DatagramPacket(this.buffer, this.buffer.length);
                this.socket.receive(inboundPacket); // blocking!

                // These should not be class-based because they might (won't) come from
                // different sources
                InetAddress senderIP = inboundPacket.getAddress();
                int senderPort = inboundPacket.getPort();

                if (this.isSYN(inboundPacket.getData())) {
                    this.sequenceNumber += 1;

                    // Respond to Handshake
                    this.handleSYN(inboundPacket.getData(), senderIP, senderPort);
                } else if (this.isACK(inboundPacket.getData())) {
                    // Handle the ack packet
                    this.handleACK(inboundPacket.getData(), senderIP, senderPort);
                } else if (this.isFIN(inboundPacket.getData())) {
                    // Respond to a FIN with a FINACK
                    this.handleFIN(inboundPacket.getData(), senderIP, senderPort);
                } else if (this.isDATA(inboundPacket.getData())) {
                    // Handle the DATA packet
                    this.handleDATA(inboundPacket.getData(), senderIP, senderPort);
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
    private void sendUDPPacket(DatagramSocket socket, InetAddress receiverIP, int receiverPort, byte[] data,
            String flagList) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, receiverIP, receiverPort);
        socket.send(packet);

        // Output information about the sent packet
        outputSegmentInfo("snd", flagList, data.length);
    }

    private void sendACK(InetAddress senderIP, int senderPort) {

    }

    private void sendSYNACK(InetAddress senderIP, int senderPort) {

    }

    private void sendFINACK(InetAddress senderIP, int senderPort) {
        // TODO close connection?
    }

    /*
     * HANDLERS
     */

    // Method to handle a SYN packet
    private void handleSYN(byte[] recvPacketData, InetAddress senderIP, int senderPort) {
        this.ackNumber = this.extractSequenceNumber(recvPacketData) + 1;

        this.totalPacketsReceived += 1;

        this.sendSYNACK(senderIP, senderPort);
    }

    // Method to handle ACK reception
    private void handleACK(byte[] recvPacketData, InetAddress senderIP, int senderPort) {
        this.ackNumber = this.extractSequenceNumber(recvPacketData);

        this.totalPacketsReceived += 1;
    }

    // Method to handle FIN reception
    private void handleFIN(byte[] recvPacketData, InetAddress senderIP, int senderPort) {
        this.ackNumber = this.extractSequenceNumber(recvPacketData) + 1;

        this.totalPacketsReceived += 1;

        this.sendFINACK(senderIP, senderPort);
    }

    // Method to handle received data segment
    private void handleDATA(byte[] recvPacketData, InetAddress senderIP, int senderPort) throws IOException {
        int recvSeqNum = this.extractSequenceNumber(recvPacketData);
        
        // Only update ackNumber if received packet is continuous
        if (recvSeqNum == this.ackNumber + this.extractLength(recvPacketData)) {
            this.ackNumber = recvSeqNum + this.extractLength(recvPacketData);
        }

        // Should we be doing this regardless?
        this.totalPacketsReceived += 1;
        this.totalDataReceived += this.extractLength(recvPacketData);

        this.sendACK(senderIP, senderPort);
    }

    /*
     * MISC.
     */

    // Method to close the connection and print statistics
    private void printStatistics() {
        // Implement closing logic and print statistics here
    }

    // Method to output segment information
    private void outputSegmentInfo(String action, String flagList, int numBytes) {
        Date date = new Date();
        System.out.printf("%d %s %s %d %s %d %d %d\n", date.getTime(), action, flagList, this.sequenceNumber, numBytes,
                this.ackNumber);
    }

    private byte[] createHeader(int length, int afs) {
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

    private int getChecksum(byte[] data) {
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
        return (long) (header[15] & 0xFF) << 56 |
                (long) (header[14] & 0xFF) << 48 |
                (long) (header[13] & 0xFF) << 40 |
                (long) (header[12] & 0xFF) << 32 |
                (long) (header[11] & 0xFF) << 24 |
                (long) (header[10] & 0xFF) << 16 |
                (long) (header[9] & 0xFF) << 8 |
                (long) (header[8] & 0xFF);
    }

    private int extractLength(byte[] header) {
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
        System.out.println("isDATA: " + extractLength(data));
        return (extractLength(data) > 0);
    }

}
