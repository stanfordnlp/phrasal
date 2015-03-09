package edu.stanford.nlp.mt.decoder.util;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TranslationModelFeaturizer;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.FlatPhraseTable;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

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
      PHRASE_SCORE_NAMES[i] = String.format("%s.%d", FlatPhraseTable.DEFAULT_FEATURE_PREFIX, i);
      PHRASE_SCORES[i] = -99.0f;
    }
  }
  private static final RuleFeaturizer<IString,String> featurizer = 
      new TranslationModelFeaturizer(NUM_SYNTHETIC_SCORES);

  
  @Before
  public void setUp() throws Exception {
  }

  @Test
  public void test() {
    Sequence<IString> sourceSequence = IStrings.tokenize("This is a test sentence.");
    Sequence<IString> targetSequence = IStrings.tokenize("TEST");
    
    RuleGrid<IString,String> ruleGrid = new RuleGrid<IString,String>(sourceSequence.size());
    ConcreteRule<IString,String> syntheticRule = makeSyntheticRule(sourceSequence.subsequence(0, 1), 
        targetSequence, 0, sourceSequence);
    ruleGrid.addEntry(syntheticRule);
    syntheticRule = makeSyntheticRule(sourceSequence.subsequence(2, 3), 
        targetSequence, 2, sourceSequence);
    ruleGrid.addEntry(syntheticRule);
    
    int numRules = 0;
    for (ConcreteRule<IString,String> rule : ruleGrid) {
      System.out.println(rule.toString());
      ++numRules;
    }
    assertEquals(2, numRules);
  }

/**
 * Create a synthetic translation rule.
 * 
 * @param source
 * @param target
 * @param sourceIndex
 * @param phraseScoreNames
 * @return
 */
private ConcreteRule<IString, String> makeSyntheticRule(Sequence<IString> source, Sequence<IString> target, 
    int sourceIndex, Sequence<IString> sourceSequence) {
  // Downweight the TM features
  Rule<IString> abstractRule = new Rule<IString>(PHRASE_SCORES, PHRASE_SCORE_NAMES,
      target, source, ALIGNMENT);

  CoverageSet sourceCoverage = new CoverageSet();
  sourceCoverage.set(sourceIndex);
  ConcreteRule<IString,String> rule = new ConcreteRule<IString,String>(abstractRule,
      sourceCoverage, featurizer, null, sourceSequence, 
      PHRASE_TABLE_NAME, 0, null);
  
  // Deterministically set the isolation score since we didn't provide a scorer to the
  // ConcreteRule constructor.
  rule.isolationScore = SYNTHETIC_ISOLATION_SCORE;
  return rule;
}
}
