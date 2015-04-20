package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for TranslationModelIndex.
 * 
 * @author Spence Green
 *
 */
public class VocabularyTest {

  protected Vocabulary vocab;

  @Before
  public void setUp() throws Exception {
    vocab = new Vocabulary();
    vocab.add("The");
    vocab.add("Beast");
  }

  /**
   * Test the index data structure.
   */
  @Test
  public void testSize() {
    assertEquals(2,vocab.size());
  }

  @Test
  public void testGet() {
    assertEquals("The",vocab.get(0));
    assertEquals("Beast",vocab.get(1));
  }

  @Test
  public void testIndexOf() {
    assertEquals(0,vocab.indexOf("The"));
    assertEquals(1,vocab.indexOf("Beast"));
  }

  /**
   * Test the system data structure.
   */
  @Test
  public void testSystemIndex() {
    // Sanity check
    Vocabulary.systemClear();
    
    Vocabulary.systemAdd("We");
    Vocabulary.systemAdd("three");
    Vocabulary.systemAdd("kings");
    assertEquals(3, Vocabulary.systemSize());
    assertEquals(0, Vocabulary.systemIndexOf("We"));
    assertEquals(1, Vocabulary.systemIndexOf("three"));
    assertEquals(2, Vocabulary.systemIndexOf("kings"));
    
    // Clean up after ourselves
    Vocabulary.systemClear();
  }
}
