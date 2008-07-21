package mt.syntax.decoder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import mt.syntax.util.FileUtility;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

@SuppressWarnings("unchecked")
public class LMServer {
    //common options
	static public int port = 9800;
    static boolean use_srilm=true;
    public static boolean use_left_euqivalent_state=false;
	public static boolean use_right_euqivalent_state=false;
    static  int g_lm_order=3;
    static double lm_ceiling_cost=100;//TODO: make sure LMGrammar is using this number
    static String remote_symbol_tbl=null;;

	//lm specific
	static String lm_file=null;
	static Double interpolation_weight=null;//the interpolation weight of this lm
	static String g_host_name=null;

	//pointer
	static LMGrammar p_lm;
	static HashMap request_cache = new HashMap();//cmd with result
	static int cache_size_limit= 3000000;

//	stat
	static int g_n_request=0;
	static int g_n_cache_hit=0;

    public static void main(String[] args) {
    	if(args.length!=1){
			System.out.println("wrong command, correct command should be: java LMServer config_file");
			System.out.println("num of args is "+ args.length);
			for(int i=0; i <args.length; i++)
				System.out.println("arg is: " + args[i]);
			System.exit(0);
		}
		String config_file = args[0].trim();
		read_config_file(config_file);

        ServerSocket serverSocket = null;
        LMServer server = new LMServer();

        if(use_srilm==true)
		     System.loadLibrary("srilm");

		//p_lm.write_vocab_map_srilm(remote_symbol_tbl);
	   	     //####write host infomation
	    //String hostname=LMServer.findHostName();//this one is not stable, sometimes throw exception
	    //String hostname="unknown";


		//### begin loop
        try {
        	 serverSocket = new ServerSocket(port);
		if(serverSocket==null){
			System.out.println("Error: server socket is null");
			System.exit(0);
		}
		init_lm_grammar();
		load_lm_grammar_file(lm_file);
		System.out.println("finished lm reading, wait for connection");

         //   serverSocket = new ServerSocket(0);//0 means any free port
         //   port = serverSocket.getLocalPort();
            while(true) {//forever
                Socket socket = serverSocket.accept();
                System.out.println("accept a connection from client");
                ClientHandler handler = new ClientHandler(socket,server);
                handler.start();
            }
        } catch(IOException ioe) {
            System.out.println("cannot create serversocket at port or connection fail");
            ioe.printStackTrace();
        } finally {
            try {
                serverSocket.close();
            } catch(IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public static void init_lm_grammar(){
    	if(use_srilm==true){
			if(use_left_euqivalent_state==true || use_right_euqivalent_state==true){
				System.out.println("use local srilm, we cannot use suffix stuff");
				System.exit(0);
			}
			p_lm = new LMGrammar_SRILM(g_lm_order);
			Symbol.init_sym_tbl_from_file(remote_symbol_tbl,false);
		}else{
			//p_lm = new LMGrammar_JAVA(g_lm_order, lm_file, use_left_euqivalent_state);
			//big bug: should load the consistent symbol files
			Symbol.init_sym_tbl_from_file(remote_symbol_tbl,true);
			p_lm = new LMGrammar_JAVA_GENERAL(g_lm_order, use_left_euqivalent_state, use_right_euqivalent_state);
		}
	}

	public static void load_lm_grammar_file(String new_lm_file){
		System.out.println("############## load lm from file" + new_lm_file);
		lm_file = new_lm_file;
		p_lm.read_lm_grammar_from_file(lm_file);
	}

    public static void  read_config_file(String config_file){
		BufferedReader t_reader_config = FileUtility.getReadFileStream(config_file,"UTF-8");
		String line;
		while((line=FileUtility.read_line_lzf(t_reader_config))!=null){
			//line = line.trim().toLowerCase();
			line = line.trim();
			if(  (line.matches("^\\s*\\#.*$")==true)//comment line
			   || (line.matches("^\\s*$")==true))//empty line
				continue;

			if(line.indexOf("=")!=-1){//parameters
				String[] fds = line.split("\\s*=\\s*");
				if(fds.length!=2){
					Support.write_log_line("Wrong config line: " + line, Support.ERROR);
					System.exit(0);
				}
				if(fds[0].compareTo("lm_file")==0){
					lm_file = fds[1].trim();
					System.out.println(String.format("lm file: %s", lm_file));
				}else if(fds[0].compareTo("use_srilm")==0){
                                  use_srilm = Boolean.valueOf(fds[1]);
					System.out.println(String.format("use_srilm: %s", use_srilm));
				}else if(fds[0].compareTo("lm_ceiling_cost")==0){
					lm_ceiling_cost = new Double(fds[1]);
					System.out.println(String.format("lm_ceiling_cost: %s", lm_ceiling_cost));
				}else if(fds[0].compareTo("use_left_euqivalent_state")==0){
                                  use_left_euqivalent_state = Boolean.valueOf(fds[1]);
					System.out.println(String.format("use_left_euqivalent_state: %s", use_left_euqivalent_state));
				}else if(fds[0].compareTo("use_right_euqivalent_state")==0){
                                  use_right_euqivalent_state = Boolean.valueOf(fds[1]);
					System.out.println(String.format("use_right_euqivalent_state: %s", use_right_euqivalent_state));
				}else if(fds[0].compareTo("order")==0){
                                  g_lm_order = Integer.valueOf(fds[1]);
					System.out.println(String.format("g_lm_order: %s", g_lm_order));
				}else if(fds[0].compareTo("remote_lm_server_port")==0){
                                  port = Integer.valueOf(fds[1]);
					System.out.println(String.format("remote_lm_server_port: %s", port));
				}else if(fds[0].compareTo("remote_symbol_tbl")==0){
					remote_symbol_tbl = new String(fds[1]);
					System.out.println(String.format("remote_symbol_tbl: %s", remote_symbol_tbl));
				}else if(fds[0].compareTo("hostname")==0){
					g_host_name = fds[1].trim();
					System.out.println(String.format("host name is: %s", g_host_name));
				}else if(fds[0].compareTo("interpolation_weight")==0){
					interpolation_weight = new Double(fds[1]);
					System.out.println(String.format("interpolation_weightt: %s", interpolation_weight));
				}else{
					Support.write_log_line("Warning: not used config line: " + line, Support.ERROR);
					//System.exit(0);
				}
			}
		}
		FileUtility.close_read_file(t_reader_config);
	}



   private static String read_host_name(String fhostname){
		BufferedReader t_reader_config = FileUtility.getReadFileStream(fhostname,"UTF-8");
		String res=null;
		String line;
		while((line=FileUtility.read_line_lzf(t_reader_config))!=null){
			//line = line.trim().toLowerCase();
			line = line.trim();
			if(  (line.matches("^\\s*\\#.*$")==true)//comment line
			   || (line.matches("^\\s*$")==true))//empty line
				continue;
			res = line;
			break;
		}
		FileUtility.close_read_file(t_reader_config);
		return res;
	}


    static private String findHostName() {
        try {
            //return InetAddress.getLocalHost().getHostName();
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
        	System.out.println("Unknown host address");
			System.exit(0);
			return null;
        }
    }

//	  used by server to process diffent Client
	public static class ClientHandler extends Thread {
		public class DecodedStructure{
			String cmd;
			int num;
			int[] wrds;
		}

		LMServer parent;
	    private Socket socket;
	    private BufferedReader in;
	    private PrintWriter out;

	    public ClientHandler(Socket sock, LMServer pa) throws IOException {
	    	parent = pa;
	        socket = sock;
	        in = new BufferedReader( new InputStreamReader(socket.getInputStream()));
	        out = new PrintWriter( new OutputStreamWriter(socket.getOutputStream()));
	    }

	    public void run() {
	        String line_in;
	        String line_out;
	        try {
	            while((line_in = in.readLine())!=null){;//TODO block read
	                //System.out.println("coming in: " + line);
	            	//line_out = process_request(line_in);
			line_out = process_request_no_cache(line_in);

	                out.println(line_out);
	                out.flush();
	            }
	        } catch(IOException ioe) {
	            ioe.printStackTrace();
	        } finally {
	            try {
	                in.close();
	                out.close();
	                socket.close();
	            } catch(IOException ioe){
	                ioe.printStackTrace();
	            }
	        }
	    }
        private String process_request_no_cache(String packet){
       		//search cache
	    	g_n_request++;
    		String cmd_res = process_request_helper(packet);
	    	if(g_n_request%50000==0){
	    		System.out.println("n_requests: " + g_n_request);
	    	}
	    	return cmd_res;
    	}

        private String process_request(String packet){
       		//search cache
	    	String cmd_res = (String)request_cache.get(packet);
	    	g_n_request++;

	    	if(cmd_res==null){//cache fail
    			cmd_res = process_request_helper(packet);
    			//update cache
	    		if(request_cache.size()>cache_size_limit)
        			request_cache.clear();
        		request_cache.put(packet, cmd_res);
    		}else{
    			g_n_cache_hit++;
    		}

	    	if(g_n_request%50000==0){
	    		System.out.println("n_requests: " + g_n_request + "; n_cache_hits: "+ g_n_cache_hit + "; cache size= " +request_cache.size() +"; hit rate= " + g_n_cache_hit*1.0/g_n_request);
	    	}
	    	return cmd_res;
    	}


	    //This is the funciton that application specific
	    private String process_request_helper(String line){
	    	DecodedStructure ds = decode_packet(line);

	    	if(ds.cmd.compareTo("prob")==0){
	    		return get_prob(ds);
	    	}else if(ds.cmd.compareTo("prob_bow")==0){
	    		return get_prob_backoff_state(ds);
	    	}else if(ds.cmd.compareTo("equiv_left")==0){
	    		return get_left_equiv_state(ds);
	    	}else if(ds.cmd.compareTo("equiv_right")==0){
	    		return get_right_equiv_state(ds);
	    	}else{
	    		System.out.println("error : Wrong request line: " + line);
				//System.exit(0);
				return "";
	    	}

	    }


	    //format: prob order wrds
	    private String get_prob(DecodedStructure ds){
	    	Double res =  p_lm.get_prob(ds.wrds, ds.num, false);
	    	return res.toString();
	    }

	    //	  format: prob order wrds
	    private String get_prob_backoff_state(DecodedStructure ds){
	    	System.out.println("Error: call get_prob_backoff_state in lmserver, must exit");
			System.exit(0);
			return null;
	    	/*Double res =  p_lm.get_prob_backoff_state(ds.wrds, ds.num, ds.num);
	    	return res.toString();*/
	    }

	    //	  format: prob order wrds
	    private String get_left_equiv_state(DecodedStructure ds){
	    	System.out.println("Error: call get_left_equiv_state in lmserver, must exit");
			System.exit(0);
			return null;
	    }
	    //format: prob order wrds
	    private String get_right_equiv_state(DecodedStructure ds){
	    	System.out.println("Error: call get_right_equiv_state in lmserver, must exit");
			System.exit(0);
			return null;
	    }


	    private DecodedStructure decode_packet(String packet){
	    	String[] fds = packet.split("\\s+");
	    	DecodedStructure res = new DecodedStructure();
	    	res.cmd = fds[0].trim();
              res.num = Integer.valueOf(fds[1]);
	    	int[] wrds = new int[fds.length-2];
	    	for(int i=2; i< fds.length; i++)
                      wrds[i - 2] = Integer.valueOf(fds[i]);
	    	res.wrds=wrds;
	    	return res;
	    }
	}
}
