import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private final Object lock = new Object(); // Object for locking shared resources

    private static final int MAX_RETRANSMISSION_ATTEMPTS = 16; // Maximum number of retransmission attempts
    private static final int HEADER_SIZE = 24 * Byte.SIZE;

    // Constants for smoothing factors
    private static final double ALPHA = 0.125;
    private static final double BETA = 0.25;

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

        // Initialize duplicate ACKs count for each sequence number
        for (int i = 0; i < MAX_RETRANSMISSIONS; i++) {
            duplicateAcksCount.put(i, 0);
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
                    String flagList = "- - - D";
                    int flagNum = DATA;

                    this.sendPacket(data, flagNum, flagList);
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

        // Thread for monitoring retransmissions and handling timeouts
        Thread timeoutThread = new Thread(() -> {
            while (true) {
                synchronized (lock) {
                    // Check for expired retransmission timers
                    for (Map.Entry<Integer, Timer> entry : retransmissionTimers.entrySet()) {
                        Timer timer = entry.getValue();
                        if (timer.hasExpired()) {
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

        senderThread.start();
        receiverThread.start();
        timeoutThread.start();
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

    private void sendPacket(byte[] data, int flagNum, String flagList) {        
        synchronized (lock) {
            if (flagNum == DATA) {
                flagNum &= ACK; // Add ACK flag
            }
            
            byte[] dataPkt = new byte[HEADER_SIZE + data.length];
            byte[] dataHdr = createHeader(HEADER_SIZE, flagNum);

            System.arraycopy(dataHdr, 0, dataPkt, 0, HEADER_SIZE);
            System.arraycopy(data, 0, dataPkt, HEADER_SIZE, data.length);

            int checksum = getChecksum(dataPkt);

            dataPkt[22] = (byte) (checksum & 0xFF);
            dataPkt[23] = (byte) ((checksum >> 8) & 0xFF);

            try {
                sendUDPPacket(dataPkt, flagList, this.sequenceNumber);
                // Log the timer for retransmission
                Timer timer = new Timer(timeoutDuration);
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        resendPacket(sequenceNumber);
                    }
                });
                retransmissionTimers.put(sequenceNumber, timer);
                // Store the sent packet in sentPackets for tracking
                sentPackets.put(sequenceNumber, dataPkt);
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            // Book-keeping
            this.sequenceNumber += extractLength(data);
            this.totalDataTransferred += extractLength(data);
        }
    }

    // Method to resend a packet given its sequence number
    private void resendPacket(int sequenceNumber) {
        byte[] packet = sentPackets.get(sequenceNumber);
        if (packet != null) {
            // Check if maximum retransmission attempts reached
            int attempts = retransmissionAttempts.getOrDefault(sequenceNumber, 0);
            if (attempts >= MAX_RETRANSMISSION_ATTEMPTS) {
                // Stop retransmitting and report error
                System.err.println("Maximum retransmission attempts reached for sequence number: " + sequenceNumber);
                // TODO: we may want to handle this error condition appropriately (e.g., close the connection, notify the user, etc.)
                return;
            }
            // Resend the packet
            try {
                sendUDPPacket(packet, "- A - D", sequenceNumber);
                // Restart the timer
                Timer timer = retransmissionTimers.get(sequenceNumber);
                if (timer != null) {
                    timer.restart();
                }
                // Increment total retransmissions for statistics tracking
                totalRetransmissions++;
                // Increment the retransmission attempts counter for the current sequence number
                retransmissionAttempts.put(sequenceNumber, retransmissionAttempts.getOrDefault(sequenceNumber, 0) + 1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Method to send UDP packet
    private void sendUDPPacket(byte[] data, String flagList, int sequenceNumber) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, this.remoteAddress, this.remotePort);
        this.socket.send(packet);

        this.totalPacketsSent += 1;

        // Output information about the sent packet
        outputSegmentInfo("snd", flagList, sequenceNumber, data.length, this.ackNumber);
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
            if (extractACKFlag(recvPacketData)) {
                if (extractSYNFlag(recvPacketData) || extractFINFlag(recvPacketData)) {
                    flagList = extractSYNFlag(recvPacketData) ? "S A - -" : "F A - -";
                    outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData),
                            extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));
                    ackNumber++;
                    sequenceNumber++;
                    flagList = "- A - -";
                    flagNum = ACK;
                }
            } else { // Handle regular ACK
                flagList = "- A - -";
                outputSegmentInfo("rcv", flagList, extractSequenceNumber(recvPacketData),
                        extractLength(recvPacketData), extractAcknowledgmentNumber(recvPacketData));

                // TODO: The below note applies to this as well
                int recvSeqNum = extractAcknowledgmentNumber(recvPacketData);
                if (recvSeqNum > lastAckedSeqNum) {
                    lastAckedSeqNum = recvSeqNum;
                }

                // Check if ACK acknowledges all sent data (indicating end of transmission)
                if (lastAckedSeqNum == ((fileSize + 1) + (totalPacketsSent * HEADER_SIZE))) {
                    flagList = "- - F -";
                    flagNum = FIN;
                    byte[] empty_data = new byte[0];
                    sendPacket(empty_data, flagNum, flagList);
                }
            }

            // TODO: I don't think any of this works...

            // Check for duplicate ACKs
            if (lastAckedSeqNum >= 0 && extractAcknowledgmentNumber(recvPacketData) <= lastAckedSeqNum) {
                int duplicateAcks = duplicateAcksCount.getOrDefault(extractSequenceNumber(recvPacketData), 0);
                duplicateAcksCount.put(extractSequenceNumber(recvPacketData), duplicateAcks + 1);
                totalDuplicateAcks++;
                if (duplicateAcks == 3) {
                    resendPacket(extractSequenceNumber(recvPacketData));
                    duplicateAcksCount.put(extractSequenceNumber(recvPacketData), 0); // Reset duplicate ACK count
                }
            }
        }
    }

    // Method to handle acknowledgment of a packet
    private void handleAcknowledgment(int sequenceNumber, long ackTimestamp) {
        synchronized (lock) {
            // Remove the acknowledged packet from the sent packets data structure
            sentPackets.remove(sequenceNumber);

            // Cancel the retransmission timer associated with the acknowledged packet
            Timer timer = retransmissionTimers.remove(sequenceNumber);
            if (timer != null) {
                timer.cancel();
            }

            // Calculate the timeout duration based on the acknowledgment timestamp
            calculateTimeoutDuration(ackTimestamp);

            // TODO sliding window adjustment

            // Adjust sliding window
            // Perform necessary actions based on the sliding window
            // (e.g., slide the window, send more packets if window allows, etc.)
            // Example: slideWindow(newWindow);
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
        System.out.printf("%d %s %s %d %s %d %d %d\n", System.nanoTime(), action, flagList, sequenceNumber, numBytes,
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

    // Inner class representing a timer
    public class Timer {
        private final long timeout;
        private long startTime;
        private TimerTask timerTask;

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

        // Method to cancel the timer
        public void cancel() {
            if (timerTask != null) {
                timerTask.cancel();
            }
        }

        // Method to schedule a task for the timer
        public void schedule(TimerTask task) {
            this.timerTask = task;
            new java.util.Timer().schedule(task, timeout);
        }
    }

}