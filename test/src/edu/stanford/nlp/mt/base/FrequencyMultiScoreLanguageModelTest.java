package edu.stanford.nlp.mt.base;

import static org.junit.Assert.*;

import java.util.TreeSet;
import java.util.Set;

import org.junit.Test;

import cern.colt.Arrays;

import edu.stanford.nlp.util.Pair;

public class FrequencyMultiScoreLanguageModelTest {
  
  @Test
  public void testCreateQuery() {
    Set<Pair<String, Long>> ngrams = new TreeSet<Pair<String,Long>>();
    ngrams.add(new Pair<String,Long>("This is the test", 1L));
    ngrams.add(new Pair<String,Long>("is the test", 10L));
    ngrams.add(new Pair<String,Long>("the test", 100L));
    ngrams.add(new Pair<String,Long>("test", 1000L));
    
    FrequencyMultiScoreLanguageModel fmslm = new FrequencyMultiScoreLanguageModel("test lm", 
        ngrams.size()*100, 10, 4, ngrams);
    double[] expectedLog10 = new double[]{3,2,1,0};
    double[] actual = fmslm.multiScore(IStrings.splitToIStrings("This is the test"));
    System.err.println(Arrays.toString(expectedLog10));
    System.err.println(Arrays.toString(actual));
    for (int i = 0; i < actual.length; i++) {
      assertEquals(expectedLog10[i]*Math.log(10), actual[i], Double.MIN_NORMAL);
    }
  }
}
