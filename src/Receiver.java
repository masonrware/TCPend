import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver {
    private final Object lock = new Object(); // Object for locking shared resources

    private static final int HEADER_SIZE = 24 * Byte.SIZE;

    private static final int ACK = 0b001;
    private static final int SYNACK = 0b101;
    private static final int FINACK = 0b011;

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

    private int lastSeqNumber = 0;
    private int lastSize = 0;

    private int port;
    private int mtu;
    private int remotePort;
    private int sws;
    private String fileName;
    private DatagramSocket socket;
    private InetAddress remoteAddress;
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

    public void start() {
        // Attempt handshake
        try {
            this.handshake();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread receiverThread = new Thread(() -> {
            // Receive forever (until we get a FIN)
            while (true) {
                try {
                    // Receive a TCP Packet (for handshake)
                    DatagramPacket inboundPacket = new DatagramPacket(this.buffer, this.buffer.length);
                    this.socket.receive(inboundPacket); // blocking!
                    this.remoteAddress = inboundPacket.getAddress();
                    this.remotePort = inboundPacket.getPort();

                    this.handlePacket(inboundPacket.getData());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        receiverThread.start();
    }

    private void handshake() throws IOException {
        try {
            DatagramPacket synPacket = new DatagramPacket(this.buffer, this.buffer.length);
            this.socket.receive(synPacket); // blocking !
            synchronized (lock) {

                if (this.isSYN(synPacket.getData())) {
                    this.handlePacket(synPacket.getData());
                } else {
                    throw new IOException("Handshake Failed -- did not receive SYN packet from sender.");
                }
            }

            DatagramPacket ackPacket = new DatagramPacket(this.buffer, this.buffer.length);
            this.socket.receive(ackPacket); // blocking !

            synchronized (lock) {
                if (this.isACK(ackPacket.getData())) {
                    // Make sure the ack number is correct (seqNum + 1)
                    if (this.extractAcknowledgmentNumber(ackPacket.getData()) == this.sequenceNumber + 1) {
                        this.handlePacket(ackPacket.getData());
                    }
                } else {
                    throw new IOException("Handshake Failed -- did not receive correct ACK from sender.");
                }
            }

            // Only increment sequence number and total packet count if handshake succeeds
            synchronized(lock) {
                this.sequenceNumber += 1;
                this.totalPacketsSent += 2;
            }   
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void sendPacket(int flagNum, String flagStr) {
        synchronized (lock) {
            byte[] hdr = createHeader(HEADER_SIZE, flagNum);
            int checksum = getChecksum(hdr);
            hdr[22] = (byte) (checksum & 0xFF);
            hdr[23] = (byte) ((checksum >> 8) & 0xFF);

            try {
                sendUDPPacket(hdr, flagStr);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Method to send UDP packet
    private void sendUDPPacket(byte[] data, String flagList) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, this.remoteAddress, this.remotePort);
        this.socket.send(packet);

        // Output information about the sent packet
        outputSegmentInfo("snd", flagList, this.sequenceNumber, data.length, this.ackNumber);
    }

    private void handlePacket(byte[] recvPacketData) {
        synchronized (lock) {
            this.totalPacketsReceived += 1;
            this.totalDataReceived += extractLength(recvPacketData);

            this.lastSeqNumber = extractSequenceNumber(recvPacketData);
            this.lastSize = extractLength(recvPacketData);

            String flagList = "- - - -";
            int flagNum = 0;

            // SYN
            if(extractSYNFlag(recvPacketData)) {
                flagList = "S - - -";

                this.outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData), extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Update ack num
                this.ackNumber = this.extractSequenceNumber(recvPacketData) + 1;
                
                // Respond with a SYN-ACK
                flagList = "S A - -";
                flagNum = SYNACK;
    
                this.sendPacket(flagNum, flagList);
            } // FIN
            else if (extractFINFlag(recvPacketData)) {
                flagList = "- - F -";

                this.outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData), extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Update ack num
                this.ackNumber = this.extractSequenceNumber(recvPacketData) + 1;
                
                // Respond with a FIN-ACK
                flagList = "- A F -";
                flagNum = FINACK;
    
                this.sendPacket(flagNum, flagList);
            } // ACK
            else if (extractACKFlag(recvPacketData)) {
                flagList = "- A - -";

                this.outputSegmentInfo("rcv", "- A - -", extractSequenceNumber(recvPacketData), extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));
            } // DATA 
            else {
                flagList = "- - - D";

                this.outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData), extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Only update ackNumber if received packet is continuous
                int recvSeqNum = this.extractSequenceNumber(recvPacketData);
                if ((this.lastSeqNumber + this.lastSize) == this.ackNumber) {
                    this.ackNumber = recvSeqNum + this.extractLength(recvPacketData);
                }

                // Respond with ACK
                flagList = "- A - -";
                flagNum = ACK;

                this.sendPacket(flagNum, flagList);
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
    private void outputSegmentInfo(String action, String flagList, int sequenceNumber, int numBytes, int ackNumber) {
        Date date = new Date();
        System.out.printf("%d %s %s %d %s %d %d %d\n", date.getTime(), action, flagList, sequenceNumber, numBytes,
                ackNumber);
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

            int checksum = getChecksum(byteArray);

            byteArray[22] = (byte) (checksum & 0xFF);
            byteArray[23] = (byte) ((checksum >> 8) & 0xFF);

            return byteArray;

        } catch (IOException e) {
            e.printStackTrace();
            return null; // is this fine?
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

    private boolean extractSYNFlag(byte[] header) {
        return ((header[19]) & 0x1) == 1;
    }

    private boolean extractFINFlag(byte[] header) {
        return ((header[19] >> 1) & 0x1) == 1;
    }

    private boolean extractACKFlag(byte[] header) {
        return ((header[19] >> 2) & 0x1) == 1;
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
