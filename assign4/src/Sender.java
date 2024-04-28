import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private final Object lock = new Object(); // Object for locking shared resources
    private Thread senderThread;
    private Thread receiverThread;
    private Thread timeoutThread;

    private static final int MAX_RETRANSMISSION_ATTEMPTS = 16; // Maximum number of retransmission attempts
    private static final int HEADER_SIZE = 24;

    // Constants for smoothing factors
    private static final double ALPHA = 0.125;
    private static final double BETA = 0.25;

    // Variables for sliding window
    private int baseSeqNumber = 0; // Sequence number of the oldest unacknowledged packet
    private int nextSeqNumber = 0; // Sequence number of the next packet to send

    // Variables for timeout calculation
    private long estimatedRTT = 0;
    private long estimatedDeviation = 0;
    private long smoothedRTT = 0;
    private long smoothedDeviation = 0;
    private long timeoutDuration = 5000; // Initial timeout duration set to 5 seconds

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

    private int lastAckedSeqNum = -1; // Initialize with an invalid value

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
    private Queue<swStruct> swQueue;

    // Map to store timers for each sent packet
    private Map<Integer, Timer> retransmissionTimers = new HashMap<>();

    // Map to store the number of duplicate ACKs received for each sequence number
    private Map<Integer, Integer> duplicateAcksCount = new HashMap<>();

    // Map to store sent packets for tracking and resending
    private Map<Integer, byte[]> sentPackets = new HashMap<>();

    // Map to store the number of retransmission attempts for each sequence number
    private Map<Integer, Integer> retransmissionAttempts = new HashMap<>();

    public Sender(int p, String remIP, int remPort, String fname, int m, int s) {
        this.port = p;
        this.remoteIP = remIP;
        this.remotePort = remPort;
        this.fileName = fname;
        this.mtu = m;
        this.sws = s;
        // Leave space for the header
        this.buffer = new byte[mtu - HEADER_SIZE];
        this.swQueue = new LinkedList<swStruct>();

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
        System.out.println("[SND] Attempting handshake on port " + this.port + "...");
        try {
            this.handshake();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("[SND] Sending data to " + this.remoteIP + ":" + this.remotePort + "...");
        this.senderThread = new Thread(() -> {
            try{ 
                // Open the file for reading
                FileInputStream fileInputStream = new FileInputStream(fileName);
                int bytesRead;

                // Buffer is of size mtu
                while ((bytesRead = fileInputStream.read(this.buffer)) != -1) {
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);

                    String flagList = "- A - D";
                    int flagNum = (DATA | ACK);

                    System.out.println("PRINTING PACKET: 136");
                    this.sendPacket(data, flagNum, flagList);
                }

                fileInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        this.receiverThread = new Thread(() -> {
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

        // Thread for monitoring retransmissions and handling timeouts
        this.timeoutThread = new Thread(() -> {
            while (true) {
                synchronized (lock) {
                    // Check for expired retransmission timers
                    for (Map.Entry<Integer, Timer> entry : retransmissionTimers.entrySet()) {
                        Timer timer = entry.getValue();
                        if (!timer.isDead() && timer.hasExpired()) {
                            int sequenceNumber = entry.getKey();
                            resendPacket(sequenceNumber);
                            // Restart the timer
                            timer.restart();
                        }
                    }
                }

                // Sleep for a short duration before checking again
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        this.senderThread.start();
        this.receiverThread.start();
        this.timeoutThread.start();
    }

    // Method to handle TCP handshake only if no packets have been sent
    private void handshake() throws IOException {
        try {
            byte[] empty_data = new byte[0];
            synchronized (lock) {
                String flagList = "S - - -";
                int flagNum = SYN;

                // Send SYN packet
                this.sendPacket(empty_data, flagNum, flagList);

                // Wait for SYN-ACK from receiver
                DatagramPacket synackPacket = new DatagramPacket(this.buffer, this.buffer.length);
                socket.receive(synackPacket); // blocking!

                // Process SYN-ACK packet
                if (extractSYNFlag(synackPacket.getData()) && extractACKFlag(synackPacket.getData())) {
                    // Make sure the ack number is correct (syn+1)
                    if (this.extractAcknowledgmentNumber(synackPacket.getData()) == this.sequenceNumber + 1) {
                        // Handle the syn-ack packet
                        this.handlePacket(synackPacket.getData());
                    } else {
                        throw new IOException("Handshake Failed -- did not receive correct SYN-ACK from receiver.");
                    }
                } else {
                    throw new IOException("Handshake Failed -- did not receive SYN-ACK from receiver.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * SENDERS
     */

    private void sendPacket(byte[] data, int flagNum, String flagList) {
        synchronized (lock) {
            byte[] dataPkt = new byte[HEADER_SIZE + data.length];
            byte[] dataHdr = createHeader(data.length, flagNum);

            System.arraycopy(dataHdr, 0, dataPkt, 0, HEADER_SIZE);
            System.arraycopy(data, 0, dataPkt, HEADER_SIZE, data.length);

            int checksum = getChecksum(dataPkt);

            dataPkt[22] = (byte) (checksum & 0xFF);
            dataPkt[23] = (byte) ((checksum >> 8) & 0xFF);

            if (sentPackets.size() <= this.sws) {
                try {
                    sendUDPPacket(dataPkt, flagList, this.sequenceNumber);
                    if(this.sequenceNumber != 1) {
                        // Log the timer for retransmission
                        Timer timer = new Timer(timeoutDuration);
                        retransmissionTimers.put(this.sequenceNumber, timer);
                    }

                    // Store the sent packet in sentPackets for tracking
                    sentPackets.put(this.sequenceNumber, dataPkt);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Book-keeping
                this.sequenceNumber += extractLength(dataHdr);
                this.totalDataTransferred += extractLength(dataHdr);
            }
            else {  // No space in sliding window, create swStruct and add to queue
                System.out.println("No room to send in sliding window, adding to buffer");
                System.out.println(Arrays.toString(dataPkt));
                swStruct qPkt = new swStruct(dataPkt, flagNum, flagList);
                swQueue.add(qPkt);
                this.sequenceNumber += extractLength(dataHdr);
            }
        }
    }

    // Method to resend a packet given its sequence number
    private void resendPacket(int seqNum) {
        synchronized(lock){
            byte[] packet = sentPackets.get(seqNum);

            String flagList = "";
            // Build flagList
            flagList += extractSYNFlag(packet) ? "S " : "- ";
            flagList += extractACKFlag(packet) ? "A " : "- ";
            flagList += extractFINFlag(packet) ? "F " : "- ";
            flagList += (extractLength(packet) > 0) ? "D " : "- ";

            if (packet != null) {
                // Check if maximum retransmission attempts reached
                int attempts = retransmissionAttempts.getOrDefault(seqNum, 0);
                if (attempts >= MAX_RETRANSMISSION_ATTEMPTS) {
                    // Stop retransmitting and report error
                    System.err.println("Maximum retransmission attempts reached for sequence number: " + seqNum);
                    Timer timer = retransmissionTimers.get(seqNum);
                    timer.markDead();

                    // we may want to handle this error condition appropriately (e.g., close the connection, notify the user, etc.)
                    return;
                }
                // Resend the packet
                try {
                    sendUDPPacket(packet, flagList, seqNum);
                    // Restart the timer
                    Timer timer = retransmissionTimers.get(seqNum);
                    if (timer != null) {
                        timer.restart();
                    }
                    // Increment total retransmissions for statistics tracking
                    totalRetransmissions++;
                    // Increment the retransmission attempts counter for the current sequence number
                    retransmissionAttempts.put(seqNum, retransmissionAttempts.getOrDefault(seqNum, 0) + 1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Method to send UDP packet
    private void sendUDPPacket(byte[] data, String flagList, int seqNum) throws IOException {
        // DatagramPacket packet = new DatagramPacket(data, data.length, this.remoteAddress, this.remotePort);
        DatagramPacket packet = new DatagramPacket(data, data.length, this.remoteAddress, this.port);
        this.socket.send(packet);

        this.totalPacketsSent += 1;

        // Output information about the sent packet
        outputSegmentInfo("snd", flagList, seqNum, extractLength(data), this.ackNumber);
    }

    /*
     * HANDLERS
     */

    private void handlePacket(byte[] recvPacketData) {
        synchronized (lock) {
            totalPacketsReceived++;
            totalDataReceived += extractLength(recvPacketData);

            String flagList = "- - - -";
            int flagNum = 0;

            // Handle SYN-ACK and FIN-ACK
            if (extractSYNFlag(recvPacketData)) {
                flagList = "S A - -";
                outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData),
                        extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));
                
                sentPackets.remove(0);
                retransmissionTimers.remove(0);

                ackNumber++;
                sequenceNumber++;
                flagList = "- A - -";
                flagNum = ACK;

                byte[] empty_data = new byte[0];
                sendPacket(empty_data, flagNum, flagList);
            } else if (extractFINFlag(recvPacketData)) {
                flagList = "- A F -";
                outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData),
                        extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));
                
                ackNumber++;
                sequenceNumber++;
                flagList = "- A - -";
                flagNum = ACK;

                byte[] empty_data = new byte[0];
                sendPacket(empty_data, flagNum, flagList);

                printStatistics();
                
                // Successfully exit
                System.exit(1);
            } else { // Handle regular ACK
                flagList = "- A - -";
                outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData),
                        extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // Handle unacked packet
                handleAcknowledgment(extractAcknowledgmentNumber(recvPacketData), extractTimestamp(recvPacketData));

                // Check if ACK acknowledges all sent data (indicating end of transmission)
                if (extractAcknowledgmentNumber(recvPacketData) == (fileSize + 1)) {
                    flagList = "- - F -";
                    flagNum = FIN;
                    byte[] empty_data = new byte[0];
                    sendPacket(empty_data, flagNum, flagList);
                }
            }
        }
    }

    // Method to handle acknowledgment of a packet
    private void handleAcknowledgment(int seqNum, long ackTimestamp) {
        synchronized (lock) {
            // Only remove the acknowledged packet from the sent packets data structure if we have
            // an ack for the next successive packet
            Iterator<Map.Entry<Integer, byte[]>> unAckedIterator = sentPackets.entrySet().iterator();
            while (unAckedIterator.hasNext()) {
                Map.Entry<Integer, byte[]> entry = unAckedIterator.next();
                if (entry.getKey() < seqNum) {
                    System.out.println("REMOVING SEQNUM " + seqNum + " FROM SENTPACKETS");
                    unAckedIterator.remove(); // Safe removal using iterator
                    System.out.println("Space available in sliding window, sending packet from queue");
                    swStruct nextPkt = swQueue.poll();
                    if(nextPkt!=null) {
                        sendPacket(nextPkt.getPkt(), nextPkt.getFlagNum(), nextPkt.getFlagList());
                    }
                }
            }

            Iterator<Map.Entry<Integer, Timer>> retransTimerIterator = retransmissionTimers.entrySet().iterator();
            while (retransTimerIterator.hasNext()) {
                Map.Entry<Integer, Timer> entry = retransTimerIterator.next();
                if (entry.getKey() < seqNum) {
                    retransTimerIterator.remove(); // Safe removal using iterator
                }
            }

            // Cancel the retransmission timer associated with the acknowledged packet
            // retransmissionTimers.remove(seqNum);


            // Calculate the timeout duration based on the acknowledgment timestamp
            // calculateTimeoutDuration(ackTimestamp);

            // Check if this is a duplicate ack
            int duplicateAcks = duplicateAcksCount.getOrDefault(seqNum, 0);
            duplicateAcksCount.put(seqNum, duplicateAcks + 1);
            if (duplicateAcks == 3) {
                // Trigger retransmission logic for the packet with this sequence number
                resendPacket(seqNum);
                duplicateAcksCount.put(seqNum, 0); // Reset duplicate ACK count
            }

            // TODO sliding window adjustment

            // if (swQueue.size() > 0){
            //     System.out.println("Space available in sliding window, sending packet from queue");
            //     swStruct nextPkt = swQueue.poll();
            //     sendPacket(nextPkt.getPkt(), nextPkt.getFlagNum(), nextPkt.getFlagList());
            // }
        }
    }

    /*
     * MISC.
     */

    // Method to close the connection and print statistics
    private void printStatistics() {
        System.out.println("[DONE] Finished communicating with" + this.remoteAddress +"\nFinal statistics:");
        System.out.println("Total Data Transferred: \t\t\t" + totalDataTransferred + " bytes");
        System.out.println("Total Data Received: \t\t\t\t" + totalDataReceived + " bytes");
        System.out.println("Total Packets Sent: \t\t\t\t" + totalPacketsSent + " packets");
        System.out.println("Total Packets Received: \t\t\t" + totalPacketsReceived + " packets");
        System.out.println("Total Packets Discarded Due To Checksum: \t" + totalPacketsReceived + " packets");
        System.out.println("Total Number of Retransmissions: \t\t" + totalRetransmissions + " retransmits");
        System.out.println("Total Duplicate Acknowledgements: \t\t" + totalDuplicateAcks + " ACKs");
    }

    // Method to output segment information
    private void outputSegmentInfo(String action, String flagList, int sequenceNumber, int numBytes, int ackNumber) {
        System.out.printf("%s %d %s %d %d %d\n", action, System.nanoTime(), flagList, sequenceNumber, numBytes,
                ackNumber);
    }

    // Method to calculate timeout duration based on provided pseudo code
    private void calculateTimeoutDuration(long ackTimestamp) {
        synchronized (lock) {
            long currentTime = System.nanoTime() / 1000000; // Convert nanoseconds to milliseconds

            if (ackTimestamp == 0) {
                // First acknowledgment received
                estimatedRTT = currentTime - ackTimestamp;
                estimatedDeviation = 0;
                timeoutDuration = 2 * estimatedRTT;
            } else {
                // Subsequent acknowledgments received
                long sampleRTT = currentTime - ackTimestamp;
                long deviation = Math.abs(sampleRTT - estimatedRTT);
                smoothedRTT = (long) (ALPHA * smoothedRTT + (1 - ALPHA) * sampleRTT);
                smoothedDeviation = (long) (BETA * smoothedDeviation + (1 - BETA) * deviation);
                timeoutDuration = smoothedRTT + 4 * smoothedDeviation;
            }
        }
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
            // dataOutputStream.writeInt(length | (afs << 13));
            dataOutputStream.writeInt((length << 3) | afs); // Corrected the order of bit shifting
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

    // Do we need to return as a short?
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

    public void printLen(int number) {
        // Use Integer.toBinaryString to get the binary representation
        String binary = Integer.toBinaryString(number);
        System.out.println(binary);
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
        return (long) (header[8] & 0xFF) << 56 |
                (long) (header[9] & 0xFF) << 48 |
                (long) (header[10] & 0xFF) << 40 |
                (long) (header[11] & 0xFF) << 32 |
                (long) (header[12] & 0xFF) << 24 |
                (long) (header[13] & 0xFF) << 16 |
                (long) (header[14] & 0xFF) << 8 |
                (long) (header[15] & 0xFF);
    }

    private int extractLength(byte[] header) {
        return (header[16] & 0xFF) << 21 |
                (header[17] & 0xFF) << 13 |
                (header[18] & 0xFF) << 5 |
                ((header[19] >> 3) & 0x1F);
    }

    private int extractChecksum(byte[] header) {
        return (header[22] & 0xFF) << 8 |
                (header[23] & 0xFF);
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

    public class swStruct {
        private byte[] pkt;
        private int flagNum;
        private String flagList;

        public swStruct(byte[] pkt, int flagNum, String flagList){
            this.pkt = pkt;
            this.flagNum = flagNum;
            this.flagList = flagList;
        }

        public byte[] getPkt(){
            return this.pkt;
        }

        public int getFlagNum(){
            return this.flagNum;
        }

        public String getFlagList(){
            return this.flagList;
        }
    }

    // Inner class representing a timer
    public class Timer {
        private final long timeout;
        private long startTime;
        private boolean dead = false;
        // private TimerTask timerTask;

        public Timer(long timeout) {
            this.timeout = timeout;
            restart();
        }

        public void restart() {
            startTime = System.currentTimeMillis();
        }

        public boolean hasExpired() {
            return System.currentTimeMillis() - startTime >= timeout;
        }

        public void markDead() {
            this.dead = true;
        }

        public boolean isDead() {
            return this.dead;
        }

        // // Method to cancel the timer
        // public void cancel() {
        //     if (timerTask != null) {
        //         timerTask.cancel();
        //     }
        // }

        // // Method to schedule a task for the timer
        // public void schedule(TimerTask task) {
        //     this.timerTask = task;
        //     new java.util.Timer().schedule(task, timeout);
        // }
    }

}