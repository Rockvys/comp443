import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

// java forwarder class
// waits for an inbound connection A on port INPORT
// when it is received, it launches a connection B to <OUTHOST,OUTPORT>
// and creates threads to read-B-write-A and read-A-write-B.

class Forwarder {

	public static String OUTHOST;
	public static InetAddress OUTDEST;
	public static short OUTPORT = 22;
	public static short INPORT = 2345;
	public static boolean DEBUG = true;

	public static void main(String[] args) {

		// get command-line parameters
		if (args.length < 3) {
			System.err.println("Usage: Forwarder inport outhost outport");
			System.exit(1);
		}
		INPORT = Short.parseShort(args[0]);
		OUTHOST = args[1];
		OUTPORT = Short.parseShort(args[2]); 
		
		// DNS lookup, done just once!
		System.err.print("Looking up address of " + OUTHOST + "...");
		try {
			OUTDEST = InetAddress.getByName(OUTHOST);
		} catch (UnknownHostException uhe) {
			System.err.println("unknown host: " + OUTHOST);
			System.exit(1);;
		}
		System.err.println(" got it!");

		// initialize LISTENER socket
		// wait for connection
		ServerSocket ss = null;
		try {
			ss = new ServerSocket(INPORT);
		} catch (IOException e) {
			e.printStackTrace();
		} // needs try-catch

		// Establish connection
		Socket s1, s2;
		s1 = s2 = null;
		while (true) { // accept loop
			try {
				s1 = ss.accept();
			} catch (IOException e) {
				System.err.println("Error getting socket1");
			} 
			try {
				s2 = new Socket(OUTDEST, OUTPORT);
			} catch (IOException e) {
				System.err.println("Error getting socket2");
			}
			Copier inbound = new Copier(s1,s2);
			Copier outbound = new Copier(s2,s1);
			
			(new Thread(inbound)).start();
			(new Thread(outbound)).start();
		} // accept loop
	} // main
}

/**
 * The Copier class handles unidirectional copying from one socket to
 * another. You will need to create two of these in the main loop above, one
 * for each direction. You create the Copier object, and then create and
 * start a Thread object that runs that Copier. If c is your Copier instance
 * (created with Copier c = new Copier(sock1, sock2)), then the thread is
 * Thread t = new Thread(c), and you start the thread with t.start(). Or, in
 * one step, (new Thread(c)).start()
 */
class Copier implements Runnable {
	private Socket _from;
	private Socket _to;

	public Copier(Socket from, Socket to) {
		_from = from;
		_to = to;
	}

	public void run() {
		InputStream fis;
		OutputStream tos;
		try {
			fis = _from.getInputStream();
			tos = _to.getOutputStream();
		} catch (IOException ioe) {
			System.err.println("can't get IO streams from sockets");
			return;
		}

		byte[] buf = new byte[2048];

		int readsize;

		while (true) {

			try {
				readsize = fis.read(buf);
			} catch (IOException ioe) {
				break;
			}

			if (readsize <= 0)
				break;

			try {
				tos.write(buf, 0, readsize);
			} catch (IOException ioe) {
				break;
			}
		}

		// these should be safe close() calls!!
		try {
			fis.close();
			tos.close();

			_from.close();
			_to.close();
		} catch (IOException ioe) {
			System.err.println("can't close sockets or streams");
			return;
		}
	}
} // class Copier
