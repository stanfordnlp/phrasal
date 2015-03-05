package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import java.util.Iterator;

import org.junit.*;

import edu.stanford.nlp.util.Index;

/**
 * Unit test.
 * 
 * @author Spence Green
 *
 */
public class DecoderLocalIndexTest {

  protected Index<String> index;

  @Before
  public void setUp() {
    index = new DecoderLocalIndex<String>();
    index.add("The");
    index.add("Beast");
  }

  @Test
  public void testSize() {
    assertEquals(2,index.size());
  }

  @Test
  public void testGet() {
    assertEquals(2,index.size());
    assertEquals("The",index.get(0));
    assertEquals("Beast",index.get(1));
  }

  @Test
  public void testIndexOf() {
    assertEquals(2,index.size());
    assertEquals(-2,index.indexOf("The"));
    assertEquals(-3,index.indexOf("Beast"));
  }

  @Test
  public void testIterator() {
    Iterator<String> i = index.iterator();
    assertEquals("The", i.next());
    assertEquals("Beast", i.next());
    assertEquals(false, i.hasNext());
  }

  @Test
  public void testToArray() {
    String[] strs = new String[2];
    strs = index.objectsList().toArray(strs);
    assertEquals("The", strs[0]);
    assertEquals("Beast", strs[1]);
    assertEquals(2, strs.length);
  }
}
