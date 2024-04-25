import java.io.*;
import java.net.*;
import java.util.*;

public class TCPend {
    
    public static void main(String[] args){
        System.out.println(args.length);
        if (args.length == 12){         // Sender
            if (!args[0].equals("-p") || !args[2].equals("-s") || !args[4].equals("-a")
                                      || !args[6].equals("f") || !args[8].equals("-m") || !args[10].equals("-c")) {
                System.out.println("Usage for sender: java TCPend -p <port> -s <remote IP> -a <remote port> f <file name> -m <mtu> -c <sws>");
                return;
            }

            int port = Integer.parseInt(args[0]);
            String remoteIP = args[2];
            int remotePort = Integer.parseInt(args[4]);
            String fileName = args[6];
            int mtu = Integer.parseInt(args[8]);
            int sws = Integer.parseInt(args[10]);

            Sender sender = new Sender(remotePort, remoteIP, port, fileName, mtu, sws);

            sender.start();
            // Set up socket inside Sender class, call method here to initiate data transfer
                // Use DatagramSocket for UDP connection, does not create a stream
                // https://www.baeldung.com/udp-in-java
        }
        else if (args.length == 8){     // Receiver
            if (!args[0].equals("-p") || !args[2].equals("-m") || !args[4].equals("-c") || !args[6].equals("-f")) {
                System.out.println("Usage for receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
                return;
            }
    
            int port = Integer.parseInt(args[0]);
            int mtu = Integer.parseInt(args[2]);
            int sws = Integer.parseInt(args[4]);
            String fileName = args[6];

            Receiver receiver = new Receiver(port, mtu, sws, fileName);

            receiver.start();

            // Listen
        }
        else {                          // Invalid number of args
            System.out.println("Usage for sender: java TCPend -p <port> -s <remote IP> -a <remote port> f <file name> -m <mtu> -c <sws>");
            System.out.println("Usage for receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
            return;
        }
    }
}
