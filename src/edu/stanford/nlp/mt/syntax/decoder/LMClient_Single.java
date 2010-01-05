package edu.stanford.nlp.mt.syntax.decoder;

/* Zhifei Li, <zhifei.work@gmail.com>
* Johns Hopkins University
*/

import java.util.ArrayList;
import java.util.HashMap;

import edu.stanford.nlp.mt.syntax.util.SocketUtility;

@SuppressWarnings({"unchecked", "unused"})
public class LMClient_Single extends LMClient {
	SocketUtility.ClientConnection p_client;
	HashMap request_cache = new HashMap();
	int cache_size_limit= 3000000;
    static int BYTES_PER_CHAR =2;//TODO big bug
    
    public LMClient_Single(String hostname, int port){
    	p_client = SocketUtility.open_connection_client(hostname, port);
    }
    
    //TODO
    @Override
		public void close_client(){
    	//p_client.close; //TODO
    }
    
    //cmd: prob order wrd1 wrd2 ...
    @Override
		public double get_prob(ArrayList ngram, int order){ 
    	return get_prob(Support.sub_int_array(ngram, 0, ngram.size()), order);
    }
    
    //cmd: prob order wrd1 wrd2 ...
    @Override
		public double get_prob(int[] ngram, int order){ 
    	double res=0.0;
    	String packet= encode_packet("prob", order, ngram);    	
    	String cmd_res = exe_request(packet);    	
    	res = new Double(cmd_res);
    	return res;
    }
 
    
  //TODO 
  public double get_prob_msrlm(String ngram, int order){
      	double res=0.0;/*
 	String[] wrds = ngram.split("\\s+");
        try {
		p_client.data_out.writeInt(wrds.length);     
	        for(int i=0; i<wrds.length; i++){
			p_client.data_out.writeInt(wrds[i].length());     
			p_client.data_out.writeChars(wrds[i]); 
                }
		p_client.data_out.flush();
        	//res =  p_client.data_in.readDouble(); 
		res =  p_client.readDoubleLittleEndian(p_client.data_in);
        } catch(IOException ioe) {
	         ioe.printStackTrace();
	}*/
	return res;
    }



 /*
    public double get_prob_msrlm(String ngram, int order){
      	double res=0.0;
 	String[] wrds = ngram.split("\\s+");
        
        int len_bytes = 4; 
        for(int i=0; i<wrds.length; i++){
		len_bytes += wrds[i].length()*BYTES_PER_CHAR+4;
        }
   
        ByteBuffer bb=ByteBuffer.allocate(len_bytes);
        bb.putInt(wrds.length);     
        for(int i=0; i<wrds.length; i++){
		bb.putInt(wrds[i].length());
                for(int j=0; j<wrds[i].length(); j++)
			bb.putChar(wrds[i].charAt(j));
        }
	bb.order(ByteOrder.LITTLE_ENDIAN);

        try {
        p_client.data_out.write(bb.array(),0,len_bytes);
        //p_client.out.println();  
	p_client.data_out.flush();
        //String cmd_res = p_client.read_line();
        //System.out.println("cmd_res: " + cmd_res);
        //return new Double(cmd_res);
        res =  p_client.data_in.readDouble(); 
        } catch(IOException ioe) {
	          ioe.printStackTrace();
	}
	return res;
    }



    public double get_prob_msrlm(String ngram, int order){
	String[] wrds = ngram.split("\\s+");
        int len_bytes = 4; 
        for(int i=0; i<wrds.length; i++){
		len_bytes += (wrds[i].length()+1)*BYTES_PER_CHAR+4;
        }
        System.out.println("num of bytes for " + ngram + " ; is " + len_bytes);
        p_client.out.print(len_bytes);
        p_client.out.print(wrds.length);
        for(int i=0; i<wrds.length; i++){
		p_client.out.print(wrds[i].length()+1);
		p_client.out.print(wrds[i]);
		p_client.out.print('\0');

        }
        p_client.out.println();  
	p_client.out.flush();
        String cmd_res = p_client.read_line();
        System.out.println("cmd_res: " + cmd_res);
        return new Double(cmd_res);
    }


    public double get_prob_msrlm(String ngram, int order){
	String[] wrds = ngram.split("\\s+");
    	StringBuffer packet = new StringBuffer();  	
        packet.append(wrds.length);
        for(int i=0; i<wrds.length; i++){
         	packet.append(wrds[i].length()+1);   	
        	packet.append(wrds[i]);   	
        	packet.append("\\0");   	

        }  
        System.out.println("input ngram: " +ngram + "; packet is: "+packet.toString());
 	String cmd_res = exe_request(packet.toString());
        System.out.println("cmd_res: " + cmd_res);
        return new Double(cmd_res);
    }

   
    public double get_prob_msrlm(int[] ngram, int order){
    	StringBuffer packet = new StringBuffer();  	
        for(int i=0; i<ngram.length; i++){
        	packet.append(ngram[i]);   	
        }   
        return get_prob_msrlm(packet.toString(), order);
    }
    
    public double get_prob_msrlm(String ngram, int order){         
        String cmd_res = (String)request_cache.get(ngram);//cache lookup
    	if(cmd_res==null){
    		//first sent the buf_size
    		p_client.write_int(ngram.length()*BYTES_PER_CHAR);
    		
    		//then write the buffer, and get the reply
    		cmd_res =p_client.exe_request(ngram);
    		
    		//cache
    		if(request_cache.size()>cache_size_limit)
        		request_cache.clear();
        	request_cache.put(ngram, cmd_res);
    	}
        return new Double(cmd_res);
    }
  */  
    
    //cmd: prob order wrd1 wrd2 ...
    @Override
		public double get_prob_backoff_state(int[] ngram, int n_additional_bow){
    	System.out.println("Error: call get_prob_backoff_state in lmclient, must exit");
 		System.exit(0);
 		return -1;
    	/*double res=0.0;
    	String packet= encode_packet("problbo", n_additional_bow, ngram);    	
    	String cmd_res = exe_request(packet);    	
    	res = new Double(cmd_res);
    	return res;*/
    }
    
   
    @Override
		public int[] get_left_euqi_state(int[] original_state_wrds, int order, double[] cost){
    	System.out.println("Error: call get_left_euqi_state in lmclient, must exit");
 		System.exit(0);
 		return null;
    	/*
    	double res=0.0;    	
    	String packet= encode_packet("leftstate", order, original_state_wrds);    	
    	String cmd_res = exe_request(packet);   
    	res = new Double(cmd_res);		
    	return null;//big bug*/
    }
    
   
    @Override
		public int[] get_right_euqi_state(int[] original_state, int order){
    	System.out.println("Error: call get_right_euqi_state in lmclient, must exit");
 		System.exit(0);
 		return null;
    	/*
    	double res=0.0;
    	String packet= encode_packet("rightstate", order, original_state);    	
    	String cmd_res = exe_request(packet);    	
    	res = new Double(cmd_res);
    	return null;//big bug*/
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
    
    
    
    private String exe_request(String packet){
    	//search cache
    	String cmd_res = (String)request_cache.get(packet);
    	if(cmd_res==null){
    		cmd_res =p_client.exe_request(packet.toString());
    		if(request_cache.size()>cache_size_limit)
        		request_cache.clear();
        	request_cache.put(packet, cmd_res);
    	}
    	return cmd_res;
    }
}
