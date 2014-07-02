package edu.stanford.nlp.mt.base;

import java.util.Iterator;

import edu.stanford.nlp.mt.util.CoverageSet;

import junit.framework.TestCase;

public class CoverageSetTest  extends TestCase {
   public void testClone() {
	 CoverageSet cs1 = new CoverageSet(10);
	 cs1.set(1); cs1.set(2); cs1.set(3);
	 CoverageSet cs2 = new CoverageSet(10);
	 cs2.set(0); cs2.set(2); cs2.set(3);
	 CoverageSet cs1Clone = cs1.clone();
	 CoverageSet cs2Clone = cs2.clone();
	 assertTrue(cs1.equals(cs1Clone));
	 assertTrue(cs2.equals(cs2Clone));
	 assertFalse(cs1.equals(cs2Clone));
	 assertFalse(cs2.equals(cs1Clone));
	 cs1Clone.set(5);
	 cs2Clone.set(9);
	 assertFalse(cs1.equals(cs1Clone));
	 assertFalse(cs2.equals(cs2Clone));
   }
   
   public void testIsContiguous() {
      CoverageSet csContiguous1 = new CoverageSet(10);
      csContiguous1.set(1); csContiguous1.set(2); csContiguous1.set(3);
      CoverageSet csContiguous2 = new CoverageSet(10);
      csContiguous2.set(0); csContiguous1.set(1); csContiguous1.set(2);
      CoverageSet csContiguous3 = new CoverageSet(10);
      csContiguous3.set(7); csContiguous3.set(8); csContiguous3.set(9);
      
      CoverageSet csNonContiguous1 = new CoverageSet(10);
      csNonContiguous1.set(1); csNonContiguous1.set(2); csNonContiguous1.set(4);
      CoverageSet csNonContiguous2 = new CoverageSet(10);
      csNonContiguous1.set(0); csNonContiguous1.set(2); csNonContiguous1.set(4);
      
      assertTrue(csContiguous1.isContiguous());
      assertTrue(csContiguous2.isContiguous());
      assertTrue(csContiguous3.isContiguous());
      assertFalse(csNonContiguous1.isContiguous());
      assertFalse(csNonContiguous2.isContiguous());
   }
   
   public void testIterator() {
	   int[] bitSet = new int[]{1,45,23,100};
	   int[] bitOrderedSet = new int[]{1,23,45,100};
	   
	   CoverageSet cs = new CoverageSet();
	   for (int i : bitSet) {
		   cs.set(i);
	   }
	   Iterator<Integer> iter = cs.iterator();
	   for (int i = 0; iter.hasNext(); i++) {
		   Integer bit = iter.next();
		   assertTrue(bit.intValue() == bitOrderedSet[i]);
	   }
   }
}
