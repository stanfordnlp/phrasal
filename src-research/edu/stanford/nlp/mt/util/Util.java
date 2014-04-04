/**
 * 
 */
package edu.stanford.nlp.mt.util;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.base.PhraseAlignment;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;

/**
 * @author Minh-Thang Luong <lmthang@stanford.edu>, created on Mar 6, 2014
 *
 */
public class Util {
  public static int[] reverseArray(int[] values){
    int numElements = values.length;
    int[] reverseValues = new int[numElements];
    for(int i=0; i<numElements; i++){
      reverseValues[i] = values[numElements-i-1];
    }
    return reverseValues;
  }

  /**
   * Convert an int[] to a byte[]. Follow http://stackoverflow.com/questions/2183240/java-integer-to-byte-array.
   * 
   * @param ints
   * @return
   */
  public static byte[] toByteArray(int[] ints){
  	return toByteArray(ints, ints.length);
  }
 
  public static byte[] toByteArray(int[] ints, int length){
  	byte[] bytes = new byte[length*4];
	 	for (int i = 0; i < length; i++) {
			bytes[4*i] = (byte) (ints[i]>>>24);
			bytes[4*i+1] = (byte) (ints[i]>>>16);
			bytes[4*i+2] = (byte) (ints[i]>>>8);
			bytes[4*i+3] = (byte) ints[i];
		}
	 	return bytes;
  }
  
  /**
   * Convert an int[] to a byte[] using ByteBuffer as suggested in http://stackoverflow.com/questions/1086054/java-how-to-convert-int-to-byte.
   * 
   * @param ints
   * @return
   */
  public static byte[] toByteArray1(int[] ints){
 	 ByteBuffer byteBuffer = ByteBuffer.allocate(ints.length * 4);        
    IntBuffer intBuffer = byteBuffer.asIntBuffer();
    intBuffer.put(ints);
    return byteBuffer.array();
 }
 
  
  public static String intArrayToString(int[] values){
    StringBuffer sb = new StringBuffer();
    for (int value : values) {
      sb.append(value + " ");
    }
    sb.deleteCharAt(sb.length()-1);
    return sb.toString();
  }

  public static void error(boolean cond, String message){
  	if(cond){
  		System.err.println(message);
  		System.exit(1);
  	}
  }
  
  // print int array
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
