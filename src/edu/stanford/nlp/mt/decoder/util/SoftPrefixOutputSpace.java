package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TranslationModelFeaturizer;

/**
 * Constrained output space for prefix decoding. Uses the phrase table
 * to allow alternate translations for prefixes.
 * 
 * 
 * @author Spence Green
 *
 * @param <IString>
 * @param <String>
 */
public class SoftPrefixOutputSpace implements OutputSpace<IString, String> {

  // Hyperparameters for constructing synthetic rules
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
      // Emulate the FlatPhraseTable feature naming convention
      PHRASE_SCORE_NAMES[i] = String.format("%s.%d", FlatPhraseTable.FEATURE_PREFIX, i);
      PHRASE_SCORES[i] = -99.0f;
    }
  }
  private static final RuleFeaturizer<IString,String> featurizer = 
      new TranslationModelFeaturizer(NUM_SYNTHETIC_SCORES);

  private Sequence<IString> sourceSequence;
  private final Sequence<IString> allowablePrefix;
  private final int allowablePrefixLength;
  private final int sourceInputId;

  /**
   * Constructor.
   * 
   * @param sourceSequence
   * @param allowablePrefix
   * @param sourceInputId
   */
  public SoftPrefixOutputSpace(Sequence<IString> allowablePrefix, int sourceInputId) {
    this.allowablePrefix = allowablePrefix;
    this.allowablePrefixLength = allowablePrefix.size();
    this.sourceInputId = sourceInputId;
  }


  @Override
  public void setSourceSequence(Sequence<IString> sourceSequence) {
    this.sourceSequence = sourceSequence;
  }
  
  @Override
  public List<ConcreteRule<IString, String>> filter(List<ConcreteRule<IString, String>> ruleList) {
    // Allow any target word to map anywhere into the source, but with high
    // cost so that only OOVs and words outside the distortion limit will
    // be used.
    for (int i = 0, limit = sourceSequence.size(); i < limit; ++i) {
      final Sequence<IString> source = sourceSequence.subsequence(i,i+1);
      for (int j = 0, size = allowablePrefix.size(); j < size; ++j) {
        ConcreteRule<IString,String> syntheticRule = makeSyntheticRule(source, 
            allowablePrefix.subsequence(j, j+1), i);
        ruleList.add(syntheticRule);
      }
    }
    return ruleList;
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
      int sourceIndex) {
    // Downweight the TM features
    Rule<IString> abstractRule = new Rule<IString>(PHRASE_SCORES, PHRASE_SCORE_NAMES,
        target, source, ALIGNMENT);

    CoverageSet sourceCoverage = new CoverageSet();
    sourceCoverage.set(sourceIndex);
    ConcreteRule<IString,String> rule = new ConcreteRule<IString,String>(abstractRule,
        sourceCoverage, featurizer, null, sourceSequence, 
        PHRASE_TABLE_NAME, sourceInputId, null);
    
    // Deterministically set the isolation score since we didn't provide a scorer to the
    // ConcreteRule constructor.
    rule.isolationScore = SYNTHETIC_ISOLATION_SCORE;
    return rule;
  }

  @Override
  public boolean allowableContinuation(Featurizable<IString, String> featurizable,
      ConcreteRule<IString, String> rule) {
    final Sequence<IString> prefix = featurizable == null ? null : featurizable.targetPrefix;
    return exactMatch(prefix, rule.abstractRule.target);
  }

  private boolean exactMatch(Sequence<IString> prefix, Sequence<IString> rule) {
    if (prefix == null) {
      return allowablePrefix.size() > rule.size() ? allowablePrefix.startsWith(rule) :
        rule.startsWith(allowablePrefix);

    } else {
      int prefixLength = prefix.size();
      int upperBound = Math.min(prefixLength + rule.size(), allowablePrefixLength);
      for (int i = 0; i < upperBound; i++) {
        IString next = i >= prefixLength ? rule.get(i-prefixLength) : prefix.get(i);
        if ( ! allowablePrefix.get(i).equals(next)) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public boolean allowableFinal(Featurizable<IString, String> featurizable) {
    // Allow everything except for the NULL hypothesis
    return featurizable != null;
  }

  @Override
  public List<Sequence<IString>> getAllowableSequences() {
    // null has the semantics of the full (unconstrained) target output space.
    // This is what we want for prefix decoding because we don't pruning to happen
    // at the point of the phrase table query.
    return null;
  }
}
