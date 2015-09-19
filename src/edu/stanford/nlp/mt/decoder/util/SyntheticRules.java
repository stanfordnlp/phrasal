package edu.stanford.nlp.mt.decoder.util;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * For constructing synthetic rules.
 * 
 * @author Spence Green
 *
 */
public final class SyntheticRules {

  private static final PhraseAlignment UNIGRAM_ALIGNMENT = PhraseAlignment.getPhraseAlignment("(0)");
  public static final String PHRASE_TABLE_NAME = "synthetic";
  
  private SyntheticRules() {}
  
  /**
   * Create a synthetic translation rule.
   * 
   * @param source
   * @param target
   * @param sourceIndex
   * @param phraseScoreNames
   * @return
   */
  public static ConcreteRule<IString,String> makeSyntheticRule(Sequence<IString> source, Sequence<IString> target, 
      CoverageSet sourceCoverage, String[] phraseScoreNames, Scorer<String> scorer,
      FeatureExtractor<IString,String> featurizer,
      double cnt_f_e, int cnt_e, int cnt_f, InputProperties inputProperties, Sequence<IString> sourceSequence,
      int sourceInputId) {
    // Baseline dense features
    float[] scores = new float[phraseScoreNames.length];
    scores[0] = (float) (Math.log(cnt_f_e) - Math.log(cnt_e));
    scores[1] = scores[0];
    scores[2] = (float) (Math.log(cnt_f_e) - Math.log(cnt_f));
    scores[3] = scores[2];
    if (scores.length == 6) {
      // Extended features
      scores[4] = cnt_f_e > 1 ? (float) Math.log(cnt_f_e) : 0.0f;
      scores[5] = cnt_f_e <= 1 ? -1.0f : 0.0f;
    }

    Rule<IString> abstractRule = new Rule<>(scores, phraseScoreNames, target, source, 
        UNIGRAM_ALIGNMENT, PHRASE_TABLE_NAME);
    ConcreteRule<IString,String> rule = new ConcreteRule<>(abstractRule, sourceCoverage, featurizer, 
        scorer, sourceSequence, sourceInputId, inputProperties);
    return rule;
  }
  
  /**
   * Create a new rule from an existing rule by replacing the target side.
   * 
   * @param base
   * @param target
   * @param scorer
   * @param featurizer
   * @param sourceSequence
   * @param inputProperties
   * @param sourceInputId
   * @return
   */
  public static ConcreteRule<IString,String> makeSyntheticRule(ConcreteRule<IString,String> base,
      Sequence<IString> target, Scorer<String> scorer, FeatureExtractor<IString,String> featurizer,
      Sequence<IString> sourceSequence, InputProperties inputProperties, int sourceInputId) {
    Rule<IString> baseRule = base.abstractRule;
    Rule<IString> newRule = new Rule<>(baseRule.scores, baseRule.phraseScoreNames, target, baseRule.source, 
        UNIGRAM_ALIGNMENT, PHRASE_TABLE_NAME);
    ConcreteRule<IString,String> rule = new ConcreteRule<>(newRule, base.sourceCoverage, featurizer, 
        scorer, sourceSequence, sourceInputId, inputProperties);
    return rule;
  }
}
