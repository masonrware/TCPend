import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.util.*;

public class Receiver {
    private final Object lock = new Object(); // Object for locking shared resources

    private static final int HEADER_SIZE = 24;

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
    private FileOutputStream outputStream;

    private Map<Integer, byte[]> swMap = new HashMap<>();

    public Receiver(int p, int m, int s, String fname) {
        this.port = p;
        this.mtu = m;
        this.sws = s;
        this.fileName = fname;
        this.buffer = new byte[mtu];

        try {
            this.socket = new DatagramSocket(port);
            this.outputStream = new FileOutputStream(fname);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * STARTUP CODE
     */

    public void start() {
        // Attempt handshake infinitely -- is this a good idea?
        System.out.println("[REC] Listening on port " + this.port + ", waiting for handshake...");
        while(true) {
            boolean res = this.handshake();
            if(res) {
                break;
            }
        }

        System.out.println("[REC] Handshake complete, ready to receive data...");
        Thread receiverThread = new Thread(() -> {
            // Receive forever (until we get a FIN)
            while (true) {
                try {
                    // Receive a TCP Packet (for handshake)
                    DatagramPacket inboundPacket = new DatagramPacket(this.buffer, this.buffer.length);
                    this.socket.receive(inboundPacket); // blocking!
                    
                    this.remoteAddress = inboundPacket.getAddress();
                    this.remotePort = inboundPacket.getPort();

                    // Handle inbound packet
                    this.handlePacket(inboundPacket.getData());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        receiverThread.start();
    }

    private boolean handshake() {
        try {
            DatagramPacket synPacket = new DatagramPacket(this.buffer, this.buffer.length);
            this.socket.receive(synPacket); // blocking !
            synchronized (lock) {
                // Expect a SYN-ACK packet
                this.remoteAddress = synPacket.getAddress();
                this.remotePort = synPacket.getPort();

                if (extractSYNFlag(synPacket.getData())) {
                    // Only init connection if the syn packet's seq num is 0
                    if(extractSequenceNumber(synPacket.getData()) == 0) {
                        this.handlePacket(synPacket.getData());
                    } else {
                        System.out.println("Handshake Failed -- received SYN packet with non-zero sequence number.");
                        return false;
                    }
                } else {
                    System.out.println("Handshake Failed -- did not receive SYN packet from sender.");
                    return false;
                }
            }

            DatagramPacket ackPacket = new DatagramPacket(this.buffer, this.buffer.length);
            this.socket.receive(ackPacket); // blocking !

            synchronized (lock) {
                if (extractACKFlag(ackPacket.getData())) {
                    // Make sure the ack number is correct (seqNum + 1)
                    if (this.extractAcknowledgmentNumber(ackPacket.getData()) == this.sequenceNumber + 1) {
                        this.handlePacket(ackPacket.getData());
                    }
                } else {
                    System.out.println("Handshake Failed -- did not receive correct ACK from sender.");
                    return false;
                }
            }

            // Only increment sequence number and total packet count if handshake succeeds
            synchronized(lock) {
                this.sequenceNumber += 1;
                this.totalPacketsSent += 2;
            }   
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    
    private void sendPacket(int flagNum, String flagList, long timeStamp) {
        synchronized (lock) {
            byte[] dataPkt = new byte[HEADER_SIZE];

            byte[] hdr = createHeader(0, flagNum, timeStamp);
            // System.out.println(hdr);

            System.arraycopy(hdr, 0, dataPkt, 0, HEADER_SIZE);

            int checksum = getChecksum(dataPkt);

            dataPkt[22] = (byte) (checksum & 0xFF);
            dataPkt[23] = (byte) ((checksum >> 8) & 0xFF);

            try {
                sendUDPPacket(dataPkt, flagList, this.sequenceNumber);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Method to send UDP packet
    private void sendUDPPacket(byte[] data, String flagList, int sequenceNumber) throws IOException {
        // DatagramPacket packet = new DatagramPacket(data, data.length, this.remoteAddress, this.remotePort);
        DatagramPacket packet = new DatagramPacket(data, data.length, this.remoteAddress, this.port);
        this.socket.send(packet);

        // Output information about the sent packet
        outputSegmentInfo("snd", flagList, this.sequenceNumber, extractLength(data), this.ackNumber);
    }

    private void handlePacket(byte[] recvPacketData) {
        synchronized (lock) {
            this.totalPacketsReceived += 1;
            this.totalDataReceived += extractLength(recvPacketData);

            String flagList = "- - - -";
            int flagNum = 0;

            // SYN
            if(extractSYNFlag(recvPacketData)) {
                flagList = "S - - -";

                this.outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData), extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Update ack num
                // this.ackNumber = this.extractSequenceNumber(recvPacketData) + 1;
                this.ackNumber += 1;
                
                // Respond with a SYN-ACK
                flagList = "S A - -";
                flagNum = SYNACK;
    
                this.sendPacket(flagNum, flagList, extractTimestamp(recvPacketData));
            } // FIN
            else if (extractFINFlag(recvPacketData)) {
                flagList = "- - F -";

                this.outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData), extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Update ack num
                // this.ackNumber = this.extractSequenceNumber(recvPacketData) + 1;
                this.ackNumber += 1;
                
                // Respond with a FIN-ACK
                flagList = "- A F -";
                flagNum = FINACK;
    
                this.sendPacket(flagNum, flagList, extractTimestamp(recvPacketData));

                printStatistics();

                // Successfully exit
                System.exit(1);
            } // ACK (not ACK DATA)
            else if (extractACKFlag(recvPacketData) && (extractLength(recvPacketData) == 0)) {
                flagList = "- A - -";

                this.outputSegmentInfo("rcv", "- A - -", extractSequenceNumber(recvPacketData), extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));
            } // DATA 
            else {
                flagList = "- A - D";

                this.outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData), extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Only update ackNumber if received packet is continuous
                int recvSeqNum = this.extractSequenceNumber(recvPacketData);
                if (recvSeqNum == this.ackNumber) {
                    byte[] payload = extractPayload(recvPacketData);

                    try {
                        this.outputStream.write(payload);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }

                    this.ackNumber += this.extractLength(recvPacketData);

                    byte[] data = swMap.get(this.ackNumber);
                    while (data != null){
                        System.out.println("WHILE LOOP 248");
                        try {
                            System.out.println("Writing data to " + this.fileName);
                            this.outputStream.write(data);
                        }
                        catch (IOException e){
                            System.out.println("WRITING BACK TO FILE FAILED");
                            e.printStackTrace();
                        }
                        this.ackNumber += extractLength(data);
                        data = swMap.get(this.ackNumber);
                    }
                }
                else {  // Data out of order
                    if (swMap.size() < this.sws){   // There is space to stash data
                        System.out.println("No room in sliding window, store in buffer");
                        swMap.put(recvSeqNum, recvPacketData);
                    }
                }

                // Respond with ACK
                flagList = "- A - -";
                flagNum = ACK;

                this.sendPacket(flagNum, flagList, extractTimestamp(recvPacketData));
            }

            this.lastSeqNumber = extractSequenceNumber(recvPacketData);
            this.lastSize = extractLength(recvPacketData);
        }
    }

    /*
     * MISC.
     */

    // Method to close the connection and print statistics
    private void printStatistics() {
        System.out.println("[DONE] Finished communicating with" + this.remoteAddress +"\n Final statistics:");
        System.out.println("Total Data Transferred: \t\t\t" + totalDataTransferred + " bytes");
        System.out.println("Total Data Received: \t\t\t\t" + totalDataReceived + " bytes");
        System.out.println("Total Packets Sent: \t\t\t\t" + totalPacketsSent + " packets");
        System.out.println("Total Packets Received: \t\t\t" + totalPacketsReceived + " packets");
        System.out.println("Total Out-of-Sequence Packets: \t\t\t" + totalOutOfSequencePackets + " packets");
        System.out.println("Total Packets Discarded Due To Checksum: \t" + totalPacketsReceived + " packets");
        System.out.println("Total Number of Retransmissions: \t\t" + totalRetransmissions + " retransmits");
        System.out.println("Total Duplicate Acknowledgements: \t\t" + totalDuplicateAcks + " ACKs");
    }

    // Method to output segment information
    private void outputSegmentInfo(String action, String flagList, int sequenceNumber, int numBytes, int ackNumber) {
        System.out.printf("%s %d %s %d %d %d\n", action, System.nanoTime(), flagList, sequenceNumber, numBytes,
                ackNumber);
    }

    private byte[] createHeader(int length, int afs, long timeStamp) {
        // seqnum and acknum are globals, get length and afs from cmd line
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try {
            // Create a DataOutputStream to write data to the ByteArrayOutputStream
            DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

            // Write different data types to the byte array
            dataOutputStream.writeInt(this.sequenceNumber);
            dataOutputStream.writeInt(this.ackNumber);
            dataOutputStream.writeLong(timeStamp);
            dataOutputStream.writeInt((length << 3) | afs); // Corrected the order of bit shifting
            dataOutputStream.writeInt(0);

            // Close the DataOutputStream
            dataOutputStream.close();

            // Get the byte array
            byte[] byteArray = byteArrayOutputStream.toByteArray();

            int checksum = getChecksum(byteArray);

            // should these be flipped? i.e. byte 23 gets first byte of checksum
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

    public void printHeader(byte[] byteArray) {
        int limit = Math.min(byteArray.length, 24); // Limit to the first 24 bytes
        for (int i = 0; i < limit; i += 4) {
            StringBuilder chunk = new StringBuilder();
            for (int j = 0; j < 4 && i + j < limit; j++) {
                // Convert byte to binary string and append to chunk
                chunk.append(String.format("%8s", Integer.toBinaryString(byteArray[i + j] & 0xFF)).replace(' ', '0'));
            }
            System.out.println(chunk);
        }
    }

    public void printPacket(byte[] byteArray) {
        for (int i = 0; i < byteArray.length; i += 4) {
            StringBuilder chunk = new StringBuilder();
            for (int j = 0; j < 4 && i + j < byteArray.length; j++) {
                // Convert byte to binary string and append to chunk
                chunk.append(String.format("%8s", Integer.toBinaryString(byteArray[i + j] & 0xFF)).replace(' ', '0'));
            }
            System.out.println(chunk);
        }
    }

    public void printLen(int number) {
        // Use Integer.toBinaryString to get the binary representation
        String binary = Integer.toBinaryString(number);
        System.out.println(binary);
    }

    private byte[] extractPayload(byte[] packet){
        int dataLen = extractLength(packet);
        System.out.println("[extractPayload]: data length is " + dataLen);
        printPacket(packet);
        byte[] data = new byte[dataLen];
        System.arraycopy(packet, 24, data, 0, dataLen);

        return data;
    }

    private int extractSequenceNumber(byte[] header) {
        return (header[0] & 0xFF) << 24 |
               (header[1] & 0xFF) << 16 |
               (header[2] & 0xFF) << 8 |
               (header[3] & 0xFF);
    }

    private int extractAcknowledgmentNumber(byte[] header) {
        return (header[4] & 0xFF) << 24 |
               (header[5] & 0xFF) << 16 |
               (header[6] & 0xFF) << 8 |
               (header[7] & 0xFF);
    }

    private long extractTimestamp(byte[] header) {
        return (long)(header[8] & 0xFF) << 56 |
               (long)(header[9] & 0xFF) << 48 |
               (long)(header[10] & 0xFF) << 40 |
               (long)(header[11] & 0xFF) << 32 |
               (long)(header[12] & 0xFF) << 24 |
               (long)(header[13] & 0xFF) << 16 |
               (long)(header[14] & 0xFF) << 8 |
               (long)(header[15] & 0xFF);
    }

    private int extractLength(byte[] header) {
        return (header[16] & 0xFF) << 21 |
               (header[17] & 0xFF) << 13 |
               (header[18] & 0xFF) << 5 |
               ((header[19] >> 3) & 0x1F);
    }

    private int extractChecksum(byte[] header) {
        return (header[20] & 0xFF) << 8 |
               (header[21] & 0xFF);
    }

    private boolean extractACKFlag(byte[] header) {
        return ((header[19]) & 0x1) == 1;
    }

    private boolean extractFINFlag(byte[] header) {
        return ((header[19] >> 1) & 0x1) == 1;
    }

    private boolean extractSYNFlag(byte[] header) {
        return ((header[19] >> 2) & 0x1) == 1;
    }

    private boolean extractDATAFlag(byte[] header) {
        return ((header[19] >> 2) & 0x1) == 1;
    }
}
