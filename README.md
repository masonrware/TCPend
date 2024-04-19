# TCPend
Implemention a Transmission Control Protocol (TCP) which incorporates the following features on top of the unreliable UDP sockets: reliability (with proper re-transmissions in the event of packet losses / corruption), data Integrity (with checksums), connection management (SYN and FIN), and optimizations (fast retransmit on >= 3 duplicate ACKs)
