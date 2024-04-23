import java.io.*;
import java.net.*;
import java.util.*;

public class TCPend {
    
    public static void main(String[] args){

        if (args.length == 13){         // Sender
            if (!args[1].equals("-p") || !args[3].equals("-s") || !args[5].equals("-a")
                                      || !args[7].equals("f") || !args[9].equals("-m") || !args[11].equals("-c")) {
                System.out.println("Usage for sender: java TCPend -p <port> -s <remote IP> -a <remote port> f <file name> -m <mtu> -c <sws>");
                return;
            }

            int port = Integer.parseInt(args[1]);
            String remoteIP = args[3];
            int remotePort = Integer.parseInt(args[5]);
            String fileName = args[7];
            int mtu = Integer.parseInt(args[9]);
            int sws = Integer.parseInt(args[11]);

            Sender sender = new Sender(remotePort, remoteIP, port, fileName, mtu, sws);

            sender.start();
            // Set up socket inside Sender class, call method here to initiate data transfer
                // Use DatagramSocket for UDP connection, does not create a stream
                // https://www.baeldung.com/udp-in-java
        }
        else if (args.length == 9){     // Receiver
            if (!args[1].equals("-p") || !args[3].equals("-m") || !args[5].equals("-c") || !args[7].equals("-f")) {
                System.out.println("Usage for receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
                return;
            }
    
            int port = Integer.parseInt(args[1]);
            int mtu = Integer.parseInt(args[3]);
            int sws = Integer.parseInt(args[5]);
            String fileName = args[7];

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
