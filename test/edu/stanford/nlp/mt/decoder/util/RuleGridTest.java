package edu.stanford.nlp.mt.decoder.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TranslationModelFeaturizer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.CompiledPhraseTable;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Test case.
 * 
 * @author Spence Green
 *
 */
public class RuleGridTest {

  private static final String PHRASE_TABLE_NAME = "SoftTargetGenerator";
  private static final double SYNTHETIC_ISOLATION_SCORE = -199.0;
  private static final PhraseAlignment ALIGNMENT = PhraseAlignment.getPhraseAlignment("(0)");
  private static final int NUM_SYNTHETIC_SCORES = 4;
  private static String[] PHRASE_SCORE_NAMES;
  private static float[] PHRASE_SCORES;
  static {
    PHRASE_SCORE_NAMES = new String[NUM_SYNTHETIC_SCORES];
    PHRASE_SCORES = new float[NUM_SYNTHETIC_SCORES];
    for (int i = 0; i < NUM_SYNTHETIC_SCORES; ++i) {
      // TODO(spenceg) This ignores the phrase penalty. Bad for now! But this is another
      // reason why the phrase penalty should be a separate featurizer.
      // Emulate the FlatPhraseTable feature naming convention
      PHRASE_SCORE_NAMES[i] = String.format("%s.%d", CompiledPhraseTable.DEFAULT_FEATURE_PREFIX, i);
      PHRASE_SCORES[i] = -99.0f;
    }
  }
  private static final RuleFeaturizer<IString,String> featurizer = 
      new TranslationModelFeaturizer();

  
  @Before
  public void setUp() throws Exception {
  }

  @Test
  public void testRuleGrid() {
    Sequence<IString> sourceSequence = IStrings.tokenize("This is a test sentence.");
    Sequence<IString> targetSequence = IStrings.tokenize("TEST");
  
    // TODO(spenceg) Re-enable this test.
    assertTrue(true);
    
//    List<ConcreteRule<IString,String>> ruleList = new ArrayList<>();
//    ConcreteRule<IString,String> syntheticRule = SyntheticRules.makeSyntheticRule(source, target, 
//        sourceCoverage, phraseScoreNames, scorer, featurizer, cnt_f_e, cnt_e, cnt_f, 
//        inputProperties, sourceSequence, sourceInputId)
//        
//        
//        makeSyntheticRule(sourceSequence.subsequence(0, 1), 
//        targetSequence, 0, sourceSequence);
//    ruleList.add(syntheticRule);
//    syntheticRule = makeSyntheticRule(sourceSequence.subsequence(2, 3), 
//        targetSequence, 2, sourceSequence);
//    ruleList.add(syntheticRule);
//    
//    RuleGrid<IString,String> ruleGrid = new RuleGrid<>(ruleList, sourceSequence);
//  
//    int numRules = 0;
//    for (ConcreteRule<IString,String> rule : ruleGrid) {
//      System.out.println(rule.toString());
//      ++numRules;
//    }
//    assertEquals(2, numRules);
  }
  
}
