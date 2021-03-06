/*
    WUMP (specifically BUMP) in java. starter file
 */
import java.lang.*;     //pld
import java.net.*;      //pld
import java.io.*;
//import wumppkt;         // be sure wumppkt.java is in your current directory
//import java.io.Externalizable;

public class wclient {

    static wumppkt wp = new wumppkt();    // stupid inner-class nonsense

    //============================================================
    //============================================================

    static public void main(String args[]) {
        int destport = wumppkt.SERVERPORT;
	destport = wumppkt.SAMEPORT;		// 4716; server responds from same port
        String filename = "vanilla";
        String desthost = "ulam2.cs.luc.edu";
        int winsize = 1;

        if (args.length > 0) filename = args[0];
        if (args.length > 1) winsize = Integer.parseInt(args[1]);
        if (args.length > 2) desthost = args[2];

        DatagramSocket s;
        try {
            s = new DatagramSocket();
        }
        catch (SocketException se) {
            System.err.println("no socket available");
            return;
        }

        try {
            s.setSoTimeout(wumppkt.INITTIMEOUT);       // time in milliseconds
        } catch (SocketException se) {
            System.err.println("socket exception: timeout not set!");
        }

       if (args.length != 2) {
            System.err.println("usage: wclient filename  [winsize [hostname]]");
            //exit(1);
        }

		// DNS lookup
        InetAddress dest;
        System.err.print("Looking up address of " + desthost + "...");
        try {
            dest = InetAddress.getByName(desthost);
        }
        catch (UnknownHostException uhe) {
            System.err.println("unknown host: " + desthost);
            return;
        }
        System.err.println(" got it!");

		// build REQ & send it
        wumppkt.REQ req = wp.new REQ(wumppkt.BUMPPROTO, winsize, filename); // ctor for REQ

        System.err.println("req size = " + req.size() + ", filename=" + req.filename());

        DatagramPacket reqDG
            = new DatagramPacket(req.write(), req.size(), dest, destport);
        try {s.send(reqDG);}
        catch (IOException ioe) {
            System.err.println("send() failed");
            return;
        }

        //============================================================

        // now receive the response
        DatagramPacket replyDG            // we don't set the address here!
            = new DatagramPacket(new byte[wumppkt.MAXSIZE] , wumppkt.MAXSIZE);
        DatagramPacket ackDG = new DatagramPacket(new byte[0], 0);
        ackDG.setAddress(dest);
	ackDG.setPort(destport);		// this is wrong for wumppkt.SERVERPORT version

        int expected_block = 1;
        long starttime = System.currentTimeMillis();
        long sendtime = starttime;

        wumppkt.DATA data = wp.new DATA();
        wumppkt.ACK  ack  = wp.new ACK(0);

        int proto;        // for proto of incoming packets
        int opcode;
        int length;

        //============================================================

        while (true) {
            // get packet
            try {
                s.receive(replyDG);
            }
            catch (SocketTimeoutException ste) {
				System.err.println("hard timeout");
				// what do you do here??
				continue;
            }
            catch (IOException ioe) {
                System.err.println("receive() failed");
                return;
            }

            byte[] replybuf = replyDG.getData();
            proto = wumppkt.proto(replybuf);
            opcode = wumppkt.opcode(replybuf);
            length = replyDG.getLength();

            /* The new packet might not actually be a DATA packet.
             * But we can still build one and see, provided:
             *   1. proto =   wumppkt.BUMPPROTO
             *   2. opcode =  wumppkt.DATAop
             *   3. length >= wumppkt.DHEADERSIZE
             */

			if (  proto == wumppkt.BUMPPROTO
			      && opcode == wumppkt.DATAop
			      && length >= wumppkt.DHEADERSIZE)
			{
            		data = wp.new DATA(replyDG.getData(), length);
            } else {
            		data = null;
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
            try {s.send(ackDG);}
            catch (IOException ioe) {
                System.err.println("send() failed");
                return;
            }
            sendtime = System.currentTimeMillis();

	    // if it passes all the checks:
            //write data, increment expected_block
	    // exit if data size is < 512

        } // while
    }
}