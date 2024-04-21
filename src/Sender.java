import java.io.*;
import java.net.*;
import java.util.*;

public class Sender {
    private static final String[] FLAGS = { "-", "S", "A", "F", "D" };
    private static final int MAX_RETRANSMISSIONS = 3;

    // Variables to track statistics
    private int totalDataTransferred = 0;
    private int totalPacketsSent = 0;
    private int totalRetransmissions = 0;

    private int port;
    private String remoteIP;
    private int remotePort;
    private String fileName;
    private int mtu;
    private int sws;

    
    public Sender(int p, String remIP, int remPort, String fname, int m, int s){
        port = p;
        remoteIP = remIP;
        remotePort = remPort;
        fileName = fname;
        mtu = m;
        sws = s;

    }

    // Method to output segment information
    private void outputSegmentInfo(String flagList, int seqNumber, int numBytes, int ackNumber) {
        Date date = new Date();
        System.out.printf("%d snd %s %d %s %d %d %d\n", date.getTime(), flagList, seqNumber, numBytes, ackNumber);
    }

    // Method to send data segment
    private void sendDataSegment(DatagramSocket socket, InetAddress receiverIP, int receiverPort, byte[] data, int sequenceNumber) {
        // Implement sending logic here
    }

    // Method to handle ACK reception
    private void handleACK(int ackNumber) {
        // Implement ACK handling logic here
    }

    // Method to handle retransmissions
    private void handleRetransmission(DatagramSocket socket, InetAddress receiverIP, int receiverPort, byte[] data, int sequenceNumber) {
        // Implement retransmission logic here
    }

    // Method to send FIN segment and close connection
    private void sendFIN(DatagramSocket socket, InetAddress receiverIP, int receiverPort) {
        // Implement FIN segment sending logic here
    }

    // Method to close the connection and print statistics
    private void closeConnectionAndPrintStatistics() {
        // Implement closing logic and print statistics here
    }
}