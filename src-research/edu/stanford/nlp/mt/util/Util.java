/**
 * 
 */
package edu.stanford.nlp.mt.util;


import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * @author Minh-Thang Luong <lmthang@stanford.edu>, created on Mar 6, 2014
 *
 */
public class Util {
  /**
   * Convert an int[] to a byte[]. Follow http://stackoverflow.com/questions/2183240/java-integer-to-byte-array.
   * 
   * @param ints
   * @param length
   * @return
   */
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
  public static byte[] toByteArray(int[] ints){
    return toByteArray(ints, ints.length);
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
 
//  public static int[] reverseArray(int[] values){
//    int numElements = values.length;
//    int[] reverseValues = new int[numElements];
//    for(int i=0; i<numElements; i++){
//      reverseValues[i] = values[numElements-i-1];
//    }
//    return reverseValues;
//  }
  
//  public static String intArrayToString(int[] values){
//    StringBuilder sb = new StringBuilder();
//    for (int value : values) {
//      sb.append(value + " ");
//    }
//    sb.deleteCharAt(sb.length()-1);
//    return sb.toString();
//  }

//  public static void error(boolean cond, String message){
//  	if(cond){
//  	  throw new RuntimeException(message);
//  	}
//  }
}
