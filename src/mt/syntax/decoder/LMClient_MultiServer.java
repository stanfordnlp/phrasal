package mt.syntax.decoder;

import java.util.ArrayList;
import java.util.HashMap;

import mt.syntax.util.SocketUtility;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

@SuppressWarnings("unchecked")
public class LMClient_MultiServer extends LMClient{
	public static SocketUtility.ClientConnection[] l_clients=null;
	public static double[] probs=null;
	public static double[] weights = null;
	public static LMThread[] l_thread_handlers=null;
	public static int num_lm_servers=1;
	public static String g_packet=null;

	public static long delayMillis = 5000; //5 seconds

	HashMap request_cache = new HashMap();//cmd with result
	int cache_size_limit= 3000000;

	//thread communcation
	static boolean[] response_ready;//set by the children-thread, read by the main thread
	static boolean request_ready;//set by the main thread, read by children-threads
	static Thread p_main_thread;
	static boolean should_finish=false;
	static long g_time_interval=5000; //5 seconds

	//stat
	static int g_n_request=0;
	static int g_n_cache_hit=0;

    public LMClient_MultiServer(String[] hostnames, int[] ports, double[] wghts, int n_servers){
    	p_main_thread=Thread.currentThread();

    	num_lm_servers=n_servers;
    	l_clients = new  SocketUtility.ClientConnection[n_servers];
    	probs = new double[n_servers];
    	weights = new double[n_servers];
    	l_thread_handlers = new LMThread[n_servers];
    	response_ready=new boolean[n_servers];
    	request_ready=false;

    	for(int i=0; i<n_servers; i++){
    		l_clients[i]=SocketUtility.open_connection_client(hostnames[i], ports[i]);
    		weights[i] = wghts[i];

    		//thread
    		response_ready[i]=false;
    		l_thread_handlers[i]=new LMThread(i);
    		l_thread_handlers[i].start();
    	}
   }

    @Override
		public void close_client(){//TODO
    	//TODO close socket

    	//END all the threads
    	should_finish=true;
    	for(int i=0; i<num_lm_servers; i++){
		  l_clients[i].close();
  		  l_thread_handlers[i].interrupt();
    	}
    }

    //cmd: prob order wrd1 wrd2 ...
    @Override
		public double get_prob(ArrayList ngram, int order){
    	return get_prob(Support.sub_int_array(ngram, 0, ngram.size()), order);
    }

    //cmd: prob order wrd1 wrd2 ...
    @Override
		public double get_prob(int[] ngram, int order){
    	String packet= encode_packet("prob", order, ngram);
    	return exe_request(packet);
    }

    //cmd: prob order wrd1 wrd2 ...
    @Override
		public double get_prob_backoff_state(int[] ngram, int n_additional_bow){
    	System.out.println("Error: call get_prob_backoff_state in lmclient, must exit");
 		System.exit(0);
 		return -1;
    	//double res=0.0;
    	//String packet= encode_packet("problbo", n_additional_bow, ngram);
    	//String cmd_res = exe_request(packet);
    	//res = new Double(cmd_res);
    	//return res;
    }


    @Override
		public int[] get_left_euqi_state(int[] original_state_wrds, int order, double[] cost){
    	System.out.println("Error: call get_left_euqi_state in lmclient, must exit");
 		System.exit(0);
 		return null;

    	//double res=0.0;
    	//String packet= encode_packet("leftstate", order, original_state_wrds);
    	//String cmd_res = exe_request(packet);
    	//res = new Double(cmd_res);
    	//return null;//big bug
    }


    @Override
		public int[] get_right_euqi_state(int[] original_state, int order){
    	System.out.println("Error: call get_right_euqi_state in lmclient, must exit");
 		System.exit(0);
 		return null;

    	//double res=0.0;
    	//String packet= encode_packet("rightstate", order, original_state);
    	//String cmd_res = exe_request(packet);
    	//res = new Double(cmd_res);
    	//return null;//big bug
    }



    private String encode_packet(String cmd, int num, int[] wrds){
    	StringBuffer packet= new StringBuffer();
    	packet.append(cmd);
    	packet.append(" ");
    	packet.append(num);
    	for(int i=0; i<wrds.length; i++){
    		packet.append(" ");
    		packet.append(wrds[i]);
    	}
    	return packet.toString();
    }

    private String encode_packet(String cmd, int num, ArrayList wrds){
    	StringBuffer packet= new StringBuffer();
    	packet.append(cmd);
    	packet.append(" ");
    	packet.append(num);
    	for(int i=0; i<wrds.size(); i++){
    		packet.append(" ");
    		packet.append(wrds.get(i));
    	}
    	return packet.toString();
    }

    private double exe_request(String packet){
    	//search cache
    	Double cmd_res = (Double)request_cache.get(packet);
    	g_n_request++;
    	//cache fail
    	if(cmd_res==null){
    		//exe the request
    		cmd_res = process_request_parallel(packet);
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

//  This is the funciton that application specific
    private double process_request_parallel(String packet){
    	g_packet=packet;
    	request_ready=true;
    	//##### init the threads
    	for(int i=0; i<num_lm_servers; i++){
    		  probs[i]=0.0;//reset to zero
    		  response_ready[i]=false;
    		  l_thread_handlers[i].interrupt();
    	}

    	//##### wait until all are finished
    	boolean all_finished=false;
    	while(!all_finished){
	    	try {
		        Thread.sleep(g_time_interval);//sleep foroever until get interupted, big bug
		    } catch (InterruptedException e) {//at least a new one is finished or timer expired
		    	all_finished=true;
		    	for(int i=0; i<num_lm_servers; i++){
		    		if(response_ready[i]==false){
		    			all_finished=false;
		    			break;
		    		}
		    	}
		    }
    	}
    	request_ready=false;

    	//#### linear interpolate the results, all threads are done
    	double sum=0;
    	for(int i=0; i<num_lm_servers; i++){
    		sum += probs[i]*weights[i];
    		//System.out.println("prob "+i+" is " + probs[i] + " weight is "+weights[i]+" sum is "+sum);
    	}
    	//System.out.println("sum is " + sum);
    	return sum;
    }


    //a thread to a single lm server
    private static class LMThread extends Thread {
    	//TODO: if the thread is dead due to exception, we should we start the thread
		int pos;//remember where should i write back the results

	    public LMThread(int p){
	    	pos = p;
	    }

	    @Override
			public void run() {
	    	while(true){
    			try {
    		        Thread.sleep(g_time_interval);//sleep foroever until get interupted
    		    } catch (InterruptedException e) {//three possibilities: expired, request_ready, or should_finish
    		    	if(request_ready==true){
	    		    	String cmd_res = l_clients[pos].exe_request(g_packet);
				if(cmd_res==null){
					System.out.println("cmd_res is null, must exit");
					System.exit(0);
				}else{
                                  probs[pos] = Double.parseDouble(cmd_res);
				    	response_ready[pos]=true;
				    	p_main_thread.interrupt();
				}
			}
    		    	if(should_finish==true)
    		    		break;
    		    }
	    	}
	    }


    }
}


/*
public class LMClient_MultiServer extends LMClient{
	public static SocketUtility.ClientConnection[] l_clients=null;
	public static double[] probs=null;
	public static double[] weights = null;
	public static LMThread[] l_thread_handlers=null;
	public static int num_lm_servers=1;

	public static long delayMillis = 5000; //5 seconds

	HashMap request_cache = new HashMap();//cmd with result
	int cache_size_limit= 3000000;

    public LMClient_MultiServer(String[] hostnames, int[] ports, double[] wghts, int n_servers){
    	num_lm_servers=n_servers;
    	l_clients = new  SocketUtility.ClientConnection[n_servers];
    	probs = new double[n_servers];
    	weights = new double[n_servers];
    	l_thread_handlers = new LMThread[n_servers];

    	for(int i=0; i<n_servers; i++){
    		l_clients[i]=SocketUtility.open_connection_client(hostnames[i], ports[i]);
    		weights[i] = wghts[i];
    	}
   }

    //TODO
    public void close_client(){

    }

    //cmd: prob order wrd1 wrd2 ...
    public double get_prob(ArrayList ngram, int order){
    	return get_prob(Support.sub_int_array(ngram, 0, ngram.size()), order);
    }

    //cmd: prob order wrd1 wrd2 ...
    public double get_prob(int[] ngram, int order){
    	String packet= encode_packet("prob", order, ngram);
    	return exe_request(packet);
    }

    //cmd: prob order wrd1 wrd2 ...
    public double get_prob_backoff_state(int[] ngram, int n_additional_bow){
    	System.out.println("Error: call get_prob_backoff_state in lmclient, must exit");
 		System.exit(0);
 		return -1;
    	//double res=0.0;
    	//String packet= encode_packet("problbo", n_additional_bow, ngram);
    	//String cmd_res = exe_request(packet);
    	//res = new Double(cmd_res);
    	//return res;
    }


    public int[] get_left_euqi_state(int[] original_state_wrds, int order, double[] cost){
    	System.out.println("Error: call get_left_euqi_state in lmclient, must exit");
 		System.exit(0);
 		return null;

    	//double res=0.0;
    	//String packet= encode_packet("leftstate", order, original_state_wrds);
    	//String cmd_res = exe_request(packet);
    	//res = new Double(cmd_res);
    	//return null;//big bug
    }


    public int[] get_right_euqi_state(int[] original_state, int order){
    	System.out.println("Error: call get_right_euqi_state in lmclient, must exit");
 		System.exit(0);
 		return null;

    	//double res=0.0;
    	//String packet= encode_packet("rightstate", order, original_state);
    	//String cmd_res = exe_request(packet);
    	//res = new Double(cmd_res);
    	//return null;//big bug
    }



    private String encode_packet(String cmd, int num, int[] wrds){
    	StringBuffer packet= new StringBuffer();
    	packet.append(cmd);
    	packet.append(" ");
    	packet.append(num);
    	for(int i=0; i<wrds.length; i++){
    		packet.append(" ");
    		packet.append(wrds[i]);
    	}
    	return packet.toString();
    }

    private String encode_packet(String cmd, int num, ArrayList wrds){
    	StringBuffer packet= new StringBuffer();
    	packet.append(cmd);
    	packet.append(" ");
    	packet.append(num);
    	for(int i=0; i<wrds.size(); i++){
    		packet.append(" ");
    		packet.append(wrds.get(i));
    	}
    	return packet.toString();
    }

    private double exe_request(String packet){
    	//search cache
    	Double cmd_res = (Double)request_cache.get(packet);

    	//cache fail
    	if(cmd_res==null){
    		//exe the request
    		cmd_res = process_request_parallel(packet);

    		//update cache
    		if(request_cache.size()>cache_size_limit)
        		request_cache.clear();
        	request_cache.put(packet, cmd_res);
    	}

    	return cmd_res;
    }

//  This is the funciton that application specific
    private double process_request_parallel(String packet){
    	//init the threads
    	for(int i=0; i<num_lm_servers; i++){
    		  LMThread handler = new LMThread(i,packet);
    		  probs[i]=0.0;//reset to zero
    		  l_thread_handlers[i]=handler;
              handler.start();
    	}

    	//wait for all the threads finish or timeout (in which case the prob will be zero for that server)
    	for(int i=0; i<num_lm_servers; i++){
    		 try {
    			 l_thread_handlers[i].join(delayMillis);
             } catch (InterruptedException e) {
            	  System.out.println("Warning: thread is interupted for server " + i);
             }
    	}

    	//linear interpolate the results, all threads are done
    	double sum=0;
    	for(int i=0; i<num_lm_servers; i++){
    		sum += probs[i]*weights[i];
    		//System.out.println("prob "+i+" is " + probs[i] + " weight is "+weights[i]+" sum is "+sum);
    	}
    	//System.out.println("sum is " + sum);
    	return sum;
    }


    //a thread to a single lm server
    private static class LMThread extends Thread {
		int pos;//remember where should i write back the results
		String packet;

	    public LMThread(int p, String pkt){
	    	pos = p;
	    	packet = pkt;
	    }

	    public void run() {
	    	String cmd_res = l_clients[pos].exe_request(packet);
	    	probs[pos] = new Double(cmd_res).doubleValue();
	    }
    }
}
*/

