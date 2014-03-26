/**
 * 
 */
package edu.stanford.nlp.util;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;

/**
 * @author Thang Luong
 *
 */
public class Util {
	public static void error(boolean cond, String message){
  	if(cond){
  		System.err.println(message);
  		System.exit(1);
  	}
  }
  
  public static void log(int verbose, int threshold, String message){
  	if(verbose>=threshold){
  		System.err.println(message);
  	}
  }

  // print double array
  public static String sprint(int[] values){
  	StringBuffer sb = new StringBuffer("[");

  	if(values.length > 0){
  		for(int value : values){
  			sb.append(value + ", ");
  		}
  		sb.delete(sb.length()-2, sb.length());
  	}
  	sb.append("]");
  	return sb.toString();
  }

  //print double array
  public static String sprint(double[] values){
  	StringBuffer sb = new StringBuffer("[");

  	if(values.length > 0){
  		for(double value : values){
  			sb.append(value + ", ");
  		}
  		sb.delete(sb.length()-2, sb.length());
  	}
  	sb.append("]");
  	return sb.toString();
  }

  
  public static Sequence<IString> getIStringSequence(String[] tokens){
  	List<IString> istringList = new ArrayList<IString>();
  	for (String token : tokens) {
			istringList.add(new IString(token));
		}
  	return new SimpleSequence<IString>(istringList);
  }
}
