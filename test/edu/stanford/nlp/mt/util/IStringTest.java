package edu.stanford.nlp.mt.util;

import edu.stanford.nlp.mt.util.IString;

import junit.framework.TestCase;

import org.junit.Test;

/**
 * Unit test
 * 
 * @author danielcer
 * @author Spence Green
 *
 */
public class IStringTest extends TestCase {
	
  @Test
  public void testIdConsistency() {
		IString test1 = new IString("Test1");
		IString test2 = new IString("Test2");
		IString test3 = new IString("Test3");
		assertTrue(test1.id != test2.id && test2.id != test3.id && test1.id != test3.id);
		assertTrue(test1.id == (new IString("Test1")).id);
		assertTrue(test2.id == (new IString("Test2")).id);
		assertTrue(test3.id == (new IString("Test3")).id);
	}
	
  @Test
	public void testSystemIndex() {
    // Sanity check
    Vocabulary.systemClear();

    IString test1 = new IString("AnIString1");
		IString test2 = new IString("AnotherIString2");
		IString test3 = new IString("YetAnotherIString2");
		assertTrue(test1.id == Vocabulary.systemIndexOf(test1.toString()));
		assertTrue(test2.id == Vocabulary.systemIndexOf(test2.toString()));
		assertTrue(test3.id == Vocabulary.systemIndexOf(test3.toString()));
		
		// Clean up for other unit tests
		Vocabulary.systemClear();
	}
	
  @Test
	public void testCreationById() {
		IString test1 = new IString("a");
		IString test2 = new IString("large");
		IString test3 = new IString("puppy");
		assertTrue(test1.equals(new IString(test1.id)));
		assertTrue(test2.equals(new IString(test2.id)));
		assertTrue(test3.equals(new IString(test3.id)));
	}
}
