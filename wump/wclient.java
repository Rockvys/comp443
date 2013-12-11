/*
    WUMP (specifically BUMP) in java. starter file
 */
import java.lang.*; //pld
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.io.*;
//import wumppkt;         // be sure wumppkt.java is in your current directory
//import java.io.Externalizable;

public class wclient {
	public static final int MIN_SIZE = 512;			
	
	static wumppkt wp = new wumppkt(); // stupid inner-class nonsense

	// ============================================================
	// ============================================================

	static public void main(String args[]) {

		int destPort = wumppkt.SERVERPORT; 			// Destination Port number
		String destHostname = "ulam2.cs.luc.edu";	// Destination hostname
		String filename = "vanilla";				// The file at destination host
		int winSize = 1;							// Window size

		// Set parameters based on commandline arguments (if any)
		if (args.length > 0)
			filename = args[0];
		if (args.length > 1)
			winSize = Integer.parseInt(args[1]);
		if (args.length > 2)
			destHostname = args[2];

		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
		} catch (SocketException se) {
			System.err.println("no socket available");
			return;
		}

		try {
			socket.setSoTimeout(wumppkt.INITTIMEOUT); // time in milliseconds
		} catch (SocketException se) {
			System.err.println("socket exception: timeout setting failed!");
		}

//		if (args.length != 2) {
//			System.err.println("usage: wclient filename  [winsize [hostname]]");
//			// exit(1);
//		}

		// DNS lookup for the destination
		InetAddress destInet = null;
		System.err.print("Looking up address of " + destHostname + "...");
		try {
			destInet = InetAddress.getByName(destHostname);
		} catch (UnknownHostException uhe) {
			System.err.println("unknown host: " + destHostname);
			return;
		}
		System.err.println(" got it!");

		// build REQ & send it
		wumppkt.REQ request = wp.new REQ(wumppkt.BUMPPROTO, winSize, filename); // ctor
																			// for
																			// REQ

		System.err.println("req size = " + request.size() + 
				", filename=" + request.filename());

		DatagramPacket requestDatagram = 
				new DatagramPacket(request.write(), request.size(), destInet, destPort);
		
		try {
			socket.send(requestDatagram);
		} catch (IOException ioe) {
			System.err.println("send() failed");
			ioe.printStackTrace();
			return;
		}

		// now receive the response
		DatagramPacket responseDatagram = 
				new DatagramPacket(new byte[wumppkt.MAXSIZE], wumppkt.MAXSIZE); 
		// we don't set the address here!
		
		DatagramPacket ackDatagram = new DatagramPacket(new byte[0], 0);
		ackDatagram.setAddress(destInet);
		ackDatagram.setPort(destPort); // this is wrong for wumppkt.SERVERPORT version

		int expectedBlock = 1;
		long startTime = System.currentTimeMillis();
		long sendTime = startTime;

		wumppkt.DATA data = wp.new DATA();
		wumppkt.ACK ack = wp.new ACK(0);

		int proto; // for proto of incoming packets
		int opcode;
		int length;

		// ============================================================
		int serverPort = 0;
		while (true) {
			// get packet
			try {
				socket.receive(responseDatagram);
			} catch (SocketTimeoutException ste) {
				System.err.println("hard timeout");
				// what do you do here??
				continue;
			} catch (IOException ioe) {
				System.err.println("receive() failed");
				return;
			}
			
			
			// latch on to port, if block == 1
			if (data.blocknum() == 1) {
				serverPort = responseDatagram.getPort();
			}
			
			// Check for host
			if (!(responseDatagram.getAddress().equals(destInet))) {
				System.err.println("Incorrect destination address");
				return;
			}
			// Check the port; 
			if (responseDatagram.getPort() != serverPort) {
				continue;
			}
			// if size < 512 exit
			if (data.size() < MIN_SIZE) {
				System.err.println("Reached end of transfer... program exiting...");
				break;
			}
			
			// Check opcode
			
			
			
			
			byte[] responseBuffer = responseDatagram.getData();
			proto = wumppkt.proto(responseBuffer);
			opcode = wumppkt.opcode(responseBuffer);
			length = responseDatagram.getLength();

			/*
			 * The new packet might not actually be a DATA packet. But we can
			 * still build one and see, provided: 1. proto = wumppkt.BUMPPROTO
			 * 2. opcode = wumppkt.DATAop 3. length >= wumppkt.DHEADERSIZE
			 */
			
			if (proto == wumppkt.BUMPPROTO && opcode == wumppkt.DATAop && 
					length >= wumppkt.DHEADERSIZE) {
				data = wp.new DATA(responseDatagram.getData(), length);
			} else {
				data = null;
			}

			// the following seven items we can print always
			System.err.print("rec'd packet: len=" + length);
			System.err.print("; proto=" + proto);
			System.err.print("; opcode=" + opcode);
			System.err.print("; src=(" + responseDatagram.getAddress().getHostAddress()
					+ "/" + responseDatagram.getPort() + ")");
			System.err.print("; time="
					+ (System.currentTimeMillis() - startTime));
			System.err.println();

			if (data == null)
				System.err.println("         packet does not seem to be a data packet");
			else {
				System.err.println("         DATA packet blocknum = " + 
						data.blocknum());
				System.out.write(data.data(), 0, data.size() - wumppkt.DHEADERSIZE);
			}

			
			
			

			// send ack
			ack = wp.new ACK(wumppkt.BUMPPROTO, expectedBlock);
			ackDatagram.setData(ack.write());
			ackDatagram.setLength(ack.size());
			ackDatagram.setPort(serverPort); // GTUX
			try {
				socket.send(ackDatagram);
			} catch (IOException ioe) {
				System.err.println("send() failed");
				return;
			}
			sendTime = System.currentTimeMillis();

			
			
			// if it passes all the checks:
			// write data, increment expected_block
			// exit if data size is < 512
			
						

		} // while
	}
}