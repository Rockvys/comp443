/*
    WUMP (specifically BUMP) in java. starter file
 */
import java.lang.*; //pld
import java.net.*; //pld
import java.io.*;
//import wumppkt;         // be sure wumppkt.java is in your current directory
//import java.io.Externalizable;

public class wclient1 {

	public static final int FULL_PKT_SIZE = 512;
	public static final int DALLY_MAX = 4;

	static wumppkt wp = new wumppkt(); // stupid inner-class nonsense

	// ============================================================
	// ============================================================

	static public void main(String args[]) {
		int destport = wumppkt.SERVERPORT; // 4716; server responds from same port
		String filename = "dup2";
		String desthost = "ulam2.cs.luc.edu";
		int winsize = 1;

		if (args.length > 0)
			filename = args[0];
		if (args.length > 1)
			winsize = Integer.parseInt(args[1]);
		if (args.length > 2)
			desthost = args[2];

		DatagramSocket s;
		try {
			s = new DatagramSocket();
		} catch (SocketException se) {
			System.err.println("no socket available");
			return;
		}

		try {
			s.setSoTimeout(wumppkt.INITTIMEOUT); // time in milliseconds
		} catch (SocketException se) {
			System.err.println("socket exception: timeout not set!");
		}

		// DNS lookup
		InetAddress dest;
		System.err.print("Looking up address of " + desthost + "...");
		try {
			dest = InetAddress.getByName(desthost);
		} catch (UnknownHostException uhe) {
			System.err.println("unknown host: " + desthost);
			return;
		}
		System.err.println(" got it!");

		// build REQ & send it
		wumppkt.REQ req = wp.new REQ(wumppkt.BUMPPROTO, winsize, filename); // ctor
																			// for
																			// REQ

		System.err.println("req size = " + req.size() + ", filename="
				+ req.filename());

		DatagramPacket reqDG = new DatagramPacket(req.write(), req.size(),
				dest, destport);
		try {
			s.send(reqDG);
		} catch (IOException ioe) {
			System.err.println("send() failed");
			return;
		}

		// ============================================================

		// now receive the response
		DatagramPacket replyDG // we don't set the address here!
		= new DatagramPacket(new byte[wumppkt.MAXSIZE], wumppkt.MAXSIZE);
		DatagramPacket ackDG = new DatagramPacket(new byte[0], 0);
		ackDG.setAddress(dest);
		ackDG.setPort(destport); // this is wrong for wumppkt.SERVERPORT version

		int expected_block = 1;
		long starttime = System.currentTimeMillis();
		long sendtime = starttime;

		wumppkt.DATA data = wp.new DATA();
		wumppkt.ACK ack = wp.new ACK(0);
		int proto; // for proto of incoming packets
		int opcode;
		int length;

		// ============================================================
		int expectedPort = 0;
		int timeoutCount = 0;
		boolean isDally = false;
		long lastTime = System.currentTimeMillis();
		while (true) {
			// Handle soft timeouts
						if ((System.currentTimeMillis() - lastTime) > 1000) {
							try {s.send(ackDG);}
				            catch (IOException ioe) {
				                System.err.println("send() failed");
				                return;
				            }
						}
						lastTime = System.currentTimeMillis();
			// get packet
			try {
				s.receive(replyDG);
			} catch (SocketTimeoutException ste) {
				System.err.println("hard timeout");
				timeoutCount++;
				// Check for dally
				if (isDally && timeoutCount >= DALLY_MAX) {
					System.err.println("Exiting after " + DALLY_MAX
							+ " timeouts");
					break;
				}
				// what do you do here??
				continue;
			} catch (IOException ioe) {
				System.err.println("receive() failed");
				return;
			}

			byte[] replybuf = replyDG.getData();
			proto = wumppkt.proto(replybuf);
			opcode = wumppkt.opcode(replybuf);
			length = replyDG.getLength();

			/*
			 * The new packet might not actually be a DATA packet. But we can
			 * still build one and see, provided: 1. proto = wumppkt.BUMPPROTO
			 * 2. opcode = wumppkt.DATAop 3. length >= wumppkt.DHEADERSIZE
			 */

			if (proto == wumppkt.BUMPPROTO && opcode == wumppkt.DATAop
					&& length >= wumppkt.DHEADERSIZE) {
				data = wp.new DATA(replyDG.getData(), length);
			} else {
				data = null;
			}

			// Check IP address
			if (!replyDG.getAddress().equals(dest)) {
				System.err.println("Packet received from invalid host");
				continue;
			}

			// Latch on the port for DATA[1]
			if (data.blocknum() == 1) {
				expectedPort = replyDG.getPort();
			}
			// Check if data is from the right port send an error packet
			if (replyDG.getPort() != expectedPort) {
				System.err.println("Packet received from invalid port");
				// Send error packet
				wumppkt.ERROR err = wp.new ERROR(wumppkt.BUMPPROTO,
						(short) wumppkt.EBADPORT);
				DatagramPacket errDG = new DatagramPacket(err.write(),
						err.size());
				try {
					s.send(errDG);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				continue;
			}
			// Check block number
			if (data.blocknum() != expected_block) {
				continue; // Wrong block number
			}


			// the following seven items we can print always
			System.err.print("rec'd packet: len=" + length);
			System.err.print("; proto=" + proto);
			System.err.print("; opcode=" + opcode);
			System.err.print("; src=(" + replyDG.getAddress().getHostAddress()
        			+ "/" + replyDG.getPort()+ ")");
			System.err.print("; time=" + (System.currentTimeMillis()-starttime));
			System.err.println();
        
			if (data==null)
				System.err.println("         packet does not seem to be a data packet");
			else {
				System.err.println("         DATA packet blocknum = " + data.blocknum());
				System.out.write(data.data(), 0, data.size() - wumppkt.DHEADERSIZE);
			}
			// The following is for you to do:
			// check port, packet size, type, block, etc
			// latch on to port, if block == 1

			// send ack
			ack = wp.new ACK(wumppkt.BUMPPROTO, expected_block);
			ackDG.setData(ack.write());
			ackDG.setLength(ack.size());
			ackDG.setPort(expectedPort); // Set the right port
			try {
				s.send(ackDG);
			} catch (IOException ioe) {
				System.err.println("send() failed");
				return;
			}
			sendtime = System.currentTimeMillis();

			// Check if it's last packet and possibly set dally
			if ((data.size() - wumppkt.DHEADERSIZE) < FULL_PKT_SIZE) {
				isDally = true;
			}

			// if it passes all the checks:
			// write data, increment expected_block
			// exit if data size is < 512
			expected_block++;
		} // while

		// Close the socket
		s.close();
	}
}