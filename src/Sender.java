import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private final Object lock = new Object(); // Object for locking shared resources

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
    private long fileSize;
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

        File file = new File(fname);

        // Check if the file exists and is a file (not a directory)
        if (file.exists() && file.isFile()) {
            // Get the total size of the file in bytes
            this.fileSize = file.length();
        } else {
            System.out.println("File not found or is not a file.");
        }
    }

    /*
     * STARTUP CODE
     */

    public void start() {
        // Attempt handshake
        try {
            this.handshake();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread senderThread = new Thread(() -> {
            try {
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
                    this.sendPacket(data, "D");
                }

                fileInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread receiverThread = new Thread(() -> {
            try {
                // Receive forever (until we are done sending)
                while (true) {
                    // Wait for any inbound packet type
                    DatagramPacket inboundPacket = new DatagramPacket(this.buffer, this.buffer.length);
                    socket.receive(inboundPacket); // blocking!

                    // TODO: handle checksum!!!

                    // Handle different types of inbound packets
                    if (isFINACK(inboundPacket.getData())) {
                        this.handlePacket("AF", inboundPacket.getData());
                    } else if (isACK(inboundPacket.getData())) {
                        this.handlePacket("A", inboundPacket.getData());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        senderThread.start();
        receiverThread.start();
    }

    // Method to handle TCP handshake only if no packets have been sent
    private void handshake() throws IOException {
        try {
            byte[] empty_data = new byte[0];
            synchronized (lock) {
                // Send SYN packet
                this.sendPacket(empty_data, "S");

                // Wait for SYN-ACK from receiver
                DatagramPacket synackPacket = new DatagramPacket(this.buffer, this.buffer.length);
                socket.receive(synackPacket); // blocking!

                // Process SYN-ACK packet
                if (this.isSYNACK(synackPacket.getData())) {
                    if (this.extractAcknowledgmentNumber(synackPacket.getData()) == this.sequenceNumber + 1) {
                        // Handle the ack packet
                        this.handlePacket("SA", synackPacket.getData());
                    } else {
                        throw new IOException("Handshake Failed -- did not receive correct SYN-ACK from receiver.");
                    }
                } else {
                    throw new IOException("Handshake Failed -- did not receive SYN-ACK from receiver.");
                }

                // Only increment total packet count if handshake succeeds
                totalPacketsSent += 2;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * SENDERS
     */

    private void sendPacket(byte[] data, String flag) {
        // this.socket, this.remoteAddress, this.remotePort
        synchronized (lock) {

            if (flag == "S") {

            } else if (flag == "A") {

            } else if (flag == "F") {

            } else if (flag == "D") {
                this.sequenceNumber += data.length;
                this.totalDataTransferred += data.length;
            }
        }
    }

    // Method to send UDP packet
    private void sendUDPPacket(byte[] data, String flagList) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, this.remoteAddress, this.remotePort);
        this.socket.send(packet);

        this.totalPacketsSent += 1;

        // Output information about the sent packet
        outputSegmentInfo("snd", flagList, data.length);
    }

    /*
     * HANDLER
     */

    private void handlePacket(String flag, byte[] recvPacketData) {
        synchronized (lock) {

            this.totalPacketsReceived += 1;
            this.totalDataReceived += extractLength(recvPacketData);

            if (flag == "SA" || flag == "FA") {
                String flagList = "- - - -";

                if (flag == "SA") {
                    flagList = "S A - -";
                } else if (flag == "FA") {
                    flagList = "- A F -";
                }

                this.outputSegmentInfo("rcv", flagList, extractLength(recvPacketData));

                this.ackNumber = this.extractSequenceNumber(recvPacketData) + 1;
                this.sequenceNumber += 1;

                byte[] empty_data = new byte[0];
                this.sendPacket(empty_data, "A");
            } else if (flag == "A") {
                outputSegmentInfo("rcv", "- A - -", extractLength(recvPacketData));

                int recvAckNUm = this.extractAcknowledgmentNumber(recvPacketData);

                // Check if we are done
                if (recvAckNUm == this.fileSize) {
                    byte[] empty_data = new byte[0];
                    this.sendPacket(empty_data, "F");
                }

                // TODO: implement go-back-N?
            }
        }
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

    private int extractLength(byte[] header) { // 0001 1111
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
    private boolean isSYNACK(byte[] data) {
        int flags = (int) (data[19]);
        System.out.println("isSYNACK: " + flags);
        return ((flags & 0b1010) == 0b1010);
    }

    private boolean isACK(byte[] data) {
        int flags = (int) (data[19]);
        System.out.println("isACK: " + flags);
        return ((flags & 0b1000) == 0b1000);
    }

    private boolean isFINACK(byte[] data) {
        int flags = (int) (data[19]);
        System.out.println("isFINACK: " + flags);
        return ((flags & 0b1100) == 0b1100);
    }
}