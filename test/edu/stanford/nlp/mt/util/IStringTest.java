package edu.stanford.nlp.mt.util;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.util.Index;
import junit.framework.TestCase;

/**
 * 
 * @author danielcer (http://dmcer.net)
 *
 */
public class IStringTest extends TestCase {
	public void testIdConsistency() {
		IString test1 = new IString("Test1");
		IString test2 = new IString("Test2");
		IString test3 = new IString("Test3");
		assertTrue(test1.id != test2.id && test2.id != test3.id && test1.id != test3.id);
		assertTrue(test1.id == (new IString("Test1")).id);
		assertTrue(test2.id == (new IString("Test2")).id);
		assertTrue(test3.id == (new IString("Test3")).id);
	}
	
	public void testIdentityIndex() {
		Index<IString> idIndex = IString.identityIndex();
		IString test1 = new IString("AnIString1");
		IString test2 = new IString("AnotherIString2");
		IString test3 = new IString("YetAnotherIString2");
		assertTrue(idIndex.indexOf(test1) != idIndex.indexOf(test2) &&
				   idIndex.indexOf(test2) != idIndex.indexOf(test3) &&
				   idIndex.indexOf(test3) != idIndex.indexOf(test1));
		assertTrue(test1.id == idIndex.indexOf(test1));
		assertTrue(test2.id == idIndex.indexOf(test2));
		assertTrue(test3.id == idIndex.indexOf(test3));
	}
	
	public void testCreationById() {
		IString test1 = new IString("a");
		IString test2 = new IString("large");
		IString test3 = new IString("puppy");
		assertTrue(test1.equals(new IString(test1.id)));
		assertTrue(test2.equals(new IString(test2.id)));
		assertTrue(test3.equals(new IString(test3.id)));
	}
	
}
