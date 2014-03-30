/**
 * 
 */
package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

/**
 * @author Thang Luong
 *
 */
public class UtilTest {

	@Test
	public void testToByteArray() {
		// create a random array of ints
		int size = 100;
		int[] ints = new int[size];
		Random r = new Random(System.currentTimeMillis());
		for (int i = 0; i < ints.length; i++) {
			ints[i] = r.nextInt();
		}
		
		byte[] bytes = Util.toByteArray(ints);
		byte[] bytes1 = Util.toByteArray1(ints);
		
		assertEquals(bytes.length, bytes1.length);
		for (int i = 0; i < bytes.length; i++) {
			assertEquals(bytes[i], bytes1[i]);
		}
	}

}
