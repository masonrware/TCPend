import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private final Object lock = new Object(); // Object for locking shared resources

    private static final int MAX_RETRANSMISSIONS = 3;
    private static final int HEADER_SIZE = 24 * Byte.SIZE;

    private static final int SYN = 0b100;
    private static final int FIN = 0b010;
    private static final int ACK = 0b001;
    private static final int DATA = 0b000;

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

                // Buffer is of size mtu
                while ((bytesRead = fileInputStream.read(this.buffer)) != -1) {
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);

                    // Send data segment
                    this.sendPacket(data, DATA, "- - - D");
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

                    // Handle inbound packet
                    this.handlePacket(inboundPacket.getData());
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
                this.sendPacket(empty_data, SYN, "S - - -");

                // Wait for SYN-ACK from receiver
                DatagramPacket synackPacket = new DatagramPacket(this.buffer, this.buffer.length);
                socket.receive(synackPacket); // blocking!

                // Process SYN-ACK packet
                if (this.isSYNACK(synackPacket.getData())) {
                    // Make sure the ack number is correct (syn+1)
                    if (this.extractAcknowledgmentNumber(synackPacket.getData()) == this.sequenceNumber + 1) {
                        // Handle the ack packet
                        this.handlePacket(synackPacket.getData());
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

    private void sendPacket(byte[] data, int flagNum, String flagList) {        
        synchronized (lock) {
            if (flagNum == DATA) {
                flagNum &= ACK; // Add ACK flag

                byte[] dataPkt = new byte[HEADER_SIZE + data.length];
                byte[] dataHdr = createHeader(HEADER_SIZE, flagNum);

                System.arraycopy(dataHdr, 0, dataPkt, 0, HEADER_SIZE);
                System.arraycopy(data, 0, dataPkt, HEADER_SIZE, data.length);

                int checksum = getChecksum(dataPkt);

                dataPkt[22] = (byte) (checksum & 0xFF);
                dataPkt[23] = (byte) ((checksum >> 8) & 0xFF);

                try {
                    sendUDPPacket(dataPkt, flagList);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else { // Non data transfer, only need header

            }

            // Book-keeping
            this.sequenceNumber += extractLength(data);
            this.totalDataTransferred += extractLength(data);
        }
    }

    // Method to send UDP packet
    private void sendUDPPacket(byte[] data, String flagList) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, this.remoteAddress, this.remotePort);
        this.socket.send(packet);

        this.totalPacketsSent += 1;

        // Output information about the sent packet
        outputSegmentInfo("snd", flagList, this.sequenceNumber, data.length, this.ackNumber);
    }

    private void handlePacket(byte[] recvPacketData) {
        synchronized (lock) {
            this.totalPacketsReceived += 1;
            this.totalDataReceived += extractLength(recvPacketData);

            String flagList = "- - - -";
            int flagNum = 0;

            // SYN-ACK
            if (extractSYNFlag(recvPacketData) && extractACKFlag(recvPacketData)) {
                flagList = "S A - -";

                this.outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData),
                        extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Update ack num and seq num
                this.ackNumber = this.extractSequenceNumber(recvPacketData) + 1;
                this.sequenceNumber += 1;

                // Respond with an ack
                flagList = "- A - -";
                flagNum = ACK;

                byte[] empty_data = new byte[0];
                this.sendPacket(empty_data, flagNum, flagList);
            } // FIN-ACK
            else if (extractFINFlag(recvPacketData) && extractACKFlag(recvPacketData)) {
                flagList = "F A - -";

                this.outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData),
                        extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Update ack num and seq num
                this.ackNumber = this.extractSequenceNumber(recvPacketData) + 1;
                this.sequenceNumber += 1;

                // Respond with an ack
                flagList = "- A - -";
                flagNum = ACK;

                byte[] empty_data = new byte[0];
                this.sendPacket(empty_data, flagNum, flagList);
            } // ACK
            else if (extractACKFlag(recvPacketData)) {
                flagList = "- A - -";

                outputSegmentInfo("rcv", "- A - -", extractSequenceNumber(recvPacketData),
                        extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Check if ack num is the size of our file (initiate fin)
                int recvAckNum = this.extractAcknowledgmentNumber(recvPacketData);

                if (recvAckNum == (this.fileSize + 1)) {
                    // Respond with a fin
                    flagList = "- - F -";
                    flagNum = FIN;

                    byte[] empty_data = new byte[0];
                    this.sendPacket(empty_data, flagNum, flagList);
                }

                // TODO: go back N
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
    private boolean isSYNACK(byte[] data) {
        int flags = (int) (data[19]);
        return ((flags & 0b1010) == 0b1010);
    }
}