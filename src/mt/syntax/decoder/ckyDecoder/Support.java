package mt.syntax.decoder.ckyDecoder;

import java.util.ArrayList;

public class Support {
	static int DEBUG=0;
	static int INFO=1;
	static int PANIC=2;
	static int ERROR=3;
	static int log_level= INFO;//0:debug, panic, error
	
	public static int[] sub_int_array(int[] in, int start, int end){//start: inclusive; end: exclusive
		int[] res = new int[end-start];
		for(int i=start; i<end; i++)
			res[i-start]=in[i];
		return res;
	}
	
	public static int[] sub_int_array(ArrayList in, int start, int end){//start: inclusive; end: exclusive
		int[] res = new int[end-start];
		for(int i=start; i<end; i++)
			res[i-start]=(Integer)in.get(i);
		return res;
	}
	
	public static void  write_log_line(String mesg, int level){
		if(level>=Support.log_level)
			System.out.println(mesg);
	}

	public static void  write_log(String mesg, int level){
		if(level>=Support.log_level)
			System.out.print(mesg);
	}
	
	public static long  current_time(){
		return 0;
		//return System.currentTimeMillis();
		//return System.nanoTime();
	}
	
	
	public static long getMemoryUse(){
	    putOutTheGarbage();
	    long totalMemory = Runtime.getRuntime().totalMemory();//all the memory I get from the system
	    putOutTheGarbage();
	    long freeMemory = Runtime.getRuntime().freeMemory();
	    return (totalMemory - freeMemory)/1024;//in terms of kb
	  }

	  private static void putOutTheGarbage() {
	    collectGarbage();
	    collectGarbage();
	  }
	  
	  private static void collectGarbage() {
		 long fSLEEP_INTERVAL = 100;
	    try {
	      System.gc();
	      Thread.currentThread().sleep(fSLEEP_INTERVAL);
	      System.runFinalization();
	      Thread.currentThread().sleep(fSLEEP_INTERVAL);
	    }
	    catch (InterruptedException ex){
	      ex.printStackTrace();
	    }
	  }
	
	
//	-------------------------------------------------- arrayToString2()
//	 Convert an array of strings to one string.
//	 Put the 'separator' string between each element.

	public static String arrayToString(String[] a, String separator) {
	    StringBuffer result = new StringBuffer();
	    if (a.length > 0) {
	        result.append(a[0]);
	        for (int i=1; i<a.length; i++) {
	            result.append(separator);
	            result.append(a[i]);
	        }
	    }
	    return result.toString();
	}
	
	public static String arrayToString(int[] a, String separator) {
	    StringBuffer result = new StringBuffer();
	    if (a.length > 0) {
	        result.append(a[0]);
	        for (int i=1; i<a.length; i++) {
	            result.append(separator);
	            result.append(a[i]);
	        }
	    }
	    return result.toString();
	}
}
