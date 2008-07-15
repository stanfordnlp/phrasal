package mt.syntax.util;
import java.io.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;


/*Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/


public class SocketUtility {
		
	//############# client side #########
	//connect to server
	public static ClientConnection open_connection_client(String hostname, int port){
		ClientConnection res = new ClientConnection();
		res.hostname=hostname;
		res.port=port;
	    try {
	            InetAddress addr = InetAddress.getByName(hostname);
	            SocketAddress sockaddr = new InetSocketAddress(addr, port);
	          
	            res.socket = new Socket();  // Create an unbound socket
	            // This method will block no more than timeoutMs If the timeout occurs, SocketTimeoutException is thrown.
	            int timeoutMs = 3000;   // 2 seconds
	            res.socket.connect(sockaddr, timeoutMs);
	            res.socket.setKeepAlive( true ); 
	            //file
	            res.in = new BufferedReader( new InputStreamReader(res.socket.getInputStream()));
	            res.out = new PrintWriter( new OutputStreamWriter(res.socket.getOutputStream()));
//	            res.data_in = new DataInputStream( new BufferedInputStream( res.socket.getInputStream())) ;
//	            res.data_out = new DataOutputStream( new BufferedOutputStream (res.socket.getOutputStream()));

	
	    } catch (UnknownHostException e) {
		System.out.println("unknown host exception");
		System.exit(0);
	    } catch (SocketTimeoutException e) {
		System.out.println("socket timeout exception");
		System.exit(0);
	    } catch (IOException e) {
		System.out.println("io exception");
		System.exit(0);
	    }
	    return res;
	}
	
	
	public static class ClientConnection{
		String hostname;//server name
		int port;//server port
		Socket socket;
		public BufferedReader in;
	    public PrintWriter out;
           //public DataOutputStream data_out; //debug
           //public DataInputStream data_in; //debug

	    
	    public String exe_request(String line_out){
	    	String line_res=null;
	    	try {
	    		out.println(line_out);
	            out.flush();
	            line_res = in.readLine(); //TODO block function, big bug, the server may close the section (e.g., the server thread is dead due to out of memory(which is possible due to cache) )
	        } catch(IOException ioe) {
	            ioe.printStackTrace();
	        }
	        return line_res;
	    }
	    
	    public void write_line(String line_out){	    	
	    	out.println(line_out);
	        out.flush();
	    }
	    
	    public void write_int(int line_out){	    	
	    	out.println(line_out);
	        out.flush();
	    }
	    
	    public String read_line(){
	    	String line_res=null;
	    	try {	    		
	            line_res = in.readLine(); //TODO block function, big bug, the server may close the section (e.g., the server thread is dead due to out of memory(which is possible due to cache) )
	        } catch(IOException ioe) {
	            ioe.printStackTrace();
	        }
	        return line_res;
	    }
	    
	    
	    public  void close(){
	    	try {
	    		socket.close();
	        } catch(IOException ioe) {
	            ioe.printStackTrace();
	        }	    	
	    }

	   public static double readDoubleLittleEndian( DataInputStream d_in){
		   long accum = 0;
	    	try {	    		

		   for ( int shiftBy=0; shiftBy<64; shiftBy+=8 ){
		      // must cast to long or shift done modulo 32
		      accum |= ( (long)( d_in.readByte() & 0xff ) ) << shiftBy;
	      	   }
                } catch(IOException ioe) {
	            ioe.printStackTrace();
	        }

		   return Double.longBitsToDouble( accum );	
	   // there is no such method as Double.reverseBytes( d );
          }

   }
	
}
