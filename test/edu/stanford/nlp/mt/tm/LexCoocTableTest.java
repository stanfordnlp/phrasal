package edu.stanford.nlp.mt.tm;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit test.
 * 
 * @author Spence Green
 *
 */
public class LexCoocTableTest {

  @Test
  public void testAddition() {
    LexCoocTable coocTable = new LexCoocTable(2);
    coocTable.addCooc(1, 2);
    assertEquals(1, coocTable.getJointCount(1, 2));
    assertEquals(0, coocTable.getJointCount(0, 2));
    assertEquals(1, coocTable.getSrcMarginal(1));
    assertEquals(0, coocTable.getSrcMarginal(2));
    assertEquals(1, coocTable.getTgtMarginal(2));
    assertEquals(0, coocTable.getTgtMarginal(1));
    coocTable.addCooc(3, 2);
    assertEquals(1, coocTable.getJointCount(3, 2));
    assertEquals(2, coocTable.getTgtMarginal(2));
  }
}
