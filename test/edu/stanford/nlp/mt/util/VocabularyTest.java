package edu.stanford.nlp.mt.util;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Unit test for TranslationModelIndex.
 * 
 * @author Spence Green
 *
 */
public class VocabularyTest {

  protected Vocabulary index;

  @Before
  public void setUp() throws Exception {
    index = new Vocabulary();
    index.add("The");
    index.add("Beast");
  }

  /**
   * Test the index data structure.
   */
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

  /**
   * Test the thread-local data structure.
   */
  private MulticoreWrapper<Boolean,Boolean> wrapper;
  private final int nThreads = 2;


  @Test
  public void testSynchronization() {
    wrapper = new MulticoreWrapper<Boolean,Boolean>(nThreads, new StringIndexer());
    wrapper.put(true);
    wrapper.put(false);

    wrapper.join();
    while(wrapper.peek()) {
      boolean result = wrapper.poll();
      assertTrue(result);        
    }
  }

  /**
   * Sleeps for some random interval up to 3ms, then returns the input id.
   * 
   * @author Spence Green
   *
   */
  private static class StringIndexer implements ThreadsafeProcessor<Boolean,Boolean> {

    private static final String[] sentence = {"We", "three", "kings", "of", "orient"};

    @Override
    public Boolean process(Boolean startAtZero) {
      // These must be called inside process(), which is called from
      // within a threadpool thread.
      Vocabulary.setThreadLocalVocabulary(new Vocabulary());
      Vocabulary index = Vocabulary.getThreadLocalVocabulary();
      boolean ret = true;
      if (startAtZero) {
        index.add(sentence[0]);
        index.add(sentence[1]);
        index.add(sentence[2]);
        index.add(sentence[3]);
        index.add(sentence[4]);
        ret &= sentence.length == index.size();
        ret &= -2 == index.indexOf(sentence[0]);
        ret &= -3 == index.indexOf(sentence[1]);
        ret &= -4 == index.indexOf(sentence[2]);
        ret &= -5 == index.indexOf(sentence[3]);
        ret &= -6 == index.indexOf(sentence[4]);

      } else {
        index.add(sentence[4]);
        index.add(sentence[3]);
        index.add(sentence[2]);
        index.add(sentence[1]);
        index.add(sentence[0]);
        ret &= sentence.length == index.size();
        ret &= -2 == index.indexOf(sentence[4]);
        ret &= -3 == index.indexOf(sentence[3]);
        ret &= -4 == index.indexOf(sentence[2]);
        ret &= -5 == index.indexOf(sentence[1]);
        ret &= -6 == index.indexOf(sentence[0]);
      }
      return ret;
    }

    @Override
    public ThreadsafeProcessor<Boolean, Boolean> newInstance() {
      return this;
    }
  }
}
