import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver {
    private static final String[] FLAGS = { "-", "S", "A", "F", "D" };

    // Variables to track statistics
    private int totalDataReceived = 0;
    private int totalPacketsReceived = 0;
    private int totalOutOfSequencePackets = 0;
    private int totalPacketsWithIncorrectChecksum = 0;
    private int totalDuplicateAcks = 0;

    private int port;
    private int mtu;
    private int sws;
    private String fileName;

    
    public Receiver(int p, int m, int s, String fname){
        port = p;
        mtu = m;
        sws = s;
        fileName = fname;
    }

    // Method to output segment information
    private void outputSegmentInfo(String flagList, int seqNumber, int numBytes, int ackNumber) {
        Date date = new Date();
        System.out.printf("%d rcv %s %d %d %d\n", date.getTime(), flagList, seqNumber, numBytes, ackNumber);
    }

    // Method to handle received data segment
    private void handleDataSegment(DatagramPacket packet) {
        // Implement handling logic here
    }

    // Method to send ACK segment
    private void sendACK(DatagramSocket socket, InetAddress senderIP, int senderPort, int ackNumber) {
        // Implement sending ACK logic here
    }

    // Method to handle FIN segment and close connection
    private void handleFIN(DatagramSocket socket, InetAddress senderIP, int senderPort) {
        // Implement handling FIN logic here
    }

    // Method to close the connection and print statistics
    private void closeConnectionAndPrintStatistics() {
        // Implement closing logic and print statistics here
    }
}
