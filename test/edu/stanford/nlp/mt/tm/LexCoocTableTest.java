package edu.stanford.nlp.mt.tm;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.mt.tm.DynamicTranslationModel.FeatureTemplate;
import edu.stanford.nlp.mt.util.ParallelCorpus;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;

/**
 * Unit test.
 * 
 * @author Spence Green
 *
 */
public class LexCoocTableTest {

  @Test
  public void testAddition() {
    ParallelSuffixArray sa = new ParallelSuffixArray(
        new ParallelCorpus());
    sa.build();
    DynamicTranslationModel<String> tm = new DynamicTranslationModel<>(sa);
    tm.configureAsForegroundTM(FeatureTemplate.DENSE);
    tm.coocTable.addCooc(1, 2);
    assertEquals(1, tm.coocTable.getJointCount(1, 2));
    assertEquals(0, tm.coocTable.getJointCount(0, 2));
    assertEquals(1, tm.coocTable.getSrcMarginal(1));
    assertEquals(0, tm.coocTable.getSrcMarginal(2));
    assertEquals(1, tm.coocTable.getTgtMarginal(2));
    assertEquals(0, tm.coocTable.getTgtMarginal(1));
    tm.coocTable.addCooc(3, 2);
    assertEquals(1, tm.coocTable.getJointCount(3, 2));
    assertEquals(2, tm.coocTable.getTgtMarginal(2));
  }
}
