package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.InsertedStartEndToken;
import edu.stanford.nlp.mt.base.InsertedStartToken;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.util.Generics;

/**
 * Featurizer for n-gram language models.
 * 
 * @author danielcer
 * @author Spence Green
 */
public class NGramLanguageModelFeaturizer extends DerivationFeaturizer<IString, String> implements
   RuleFeaturizer<IString, String> {
  private static final boolean DEBUG = false;
  public static final String DEFAULT_FEATURE_NAME = "LM";
  
  // in srilm -99 is -infinity
  private static final double MOSES_LM_UNKNOWN_WORD_SCORE = -100;
 
  private final String featureName;
  private final LanguageModel<IString> lm;
  private final int lmOrder;
  
  private final boolean addContextFeatures;
  private String[] contextFeatureNames;


  /**
   * Constructor.
   * 
   * @param lm
   */
  public NGramLanguageModelFeaturizer(LanguageModel<IString> lm) {
    this.lm = lm;
    featureName = DEFAULT_FEATURE_NAME;
    this.lmOrder = lm.order();
    this.addContextFeatures = false;
  }

  /**
   *
   */
  public LanguageModel<IString> getLM() {
    return lm;
  }

  /**
   * Constructor called by Phrasal when NGramLanguageModelFeaturizer appears in
   * [additional-featurizers].
   */
  public NGramLanguageModelFeaturizer(String...args) throws IOException {
    if (args.length < 2)
      throw new RuntimeException(
          "At least two arguments are needed: LM file name and LM ID");
    featureName = args[1];
    this.lm = LanguageModelFactory.load(args[0]);
    this.lmOrder = lm.order();
    this.addContextFeatures = args.length > 2 ? true : false;
    contextFeatureNames = addContextFeatures ? new String[lmOrder] : null;
  }

  /**
   * @param f 
   * @param features 
	 *
	 */
  private double getScore(int startPos, int limit, Sequence<IString> translation, 
      Featurizable<IString, String> f, List<FeatureValue<String>> features) {
    double lmSumScore = 0;
    LMState state = null;
    for (int pos = startPos; pos < limit; pos++) {
      int seqStart = pos - lmOrder + 1;
      if (seqStart < 0)
        seqStart = 0;
      Sequence<IString> ngram = translation.subsequence(seqStart, pos + 1);
      state = lm.score(ngram);
      double ngramScore = state.getScore();
      if (ngramScore == Double.NEGATIVE_INFINITY || ngramScore != ngramScore) {
        lmSumScore += MOSES_LM_UNKNOWN_WORD_SCORE;
        continue;
      }
      if (addContextFeatures && features != null) {
        int stateLength = state.length();
        if (contextFeatureNames[stateLength] == null) {
          contextFeatureNames[stateLength] = 
              String.format("%sC%d", featureName, stateLength);
        }
        features.add(new FeatureValue<String>(contextFeatureNames[stateLength], 1.0));
      }
      lmSumScore += ngramScore;
      if (DEBUG) {
        System.out.printf("\tn-gram: %s score: %f\n", ngram, ngramScore);
      }
    }
    // The featurizer state is the result of the last n-gram query
    if (f != null) {
      // Don't set state for rule queries
      if (state == null) {
        // Target-deletion rule
        state = (LMState) f.prior.getState(this);
      }
      f.setState(this, state);
    } 
    return lmSumScore;
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    if (DEBUG) {
      System.out.printf("Sequence: %s\n\tNovel Phrase: %s\n",
          f.targetPrefix, f.targetPhrase);
      System.out.printf("Untranslated tokens: %d\n",
          f.numUntranslatedSourceTokens);
      System.out.println("ngram scoring:");
      System.out.println("===================");
    }

    IString startToken = lm.getStartToken();
    IString endToken = lm.getEndToken();

    // TODO(spenceg): If we remove targetPrefix---which we should---then we'd need to retrieve the
    // prior state to perform this calculation.
    Sequence<IString> partialTranslation;
    int startPos = f.targetPosition + 1;
    if (f.done) {
      partialTranslation = new InsertedStartEndToken<IString>(
          f.targetPrefix, startToken, endToken);
    } else {
      partialTranslation = new InsertedStartToken<IString>(
          f.targetPrefix, startToken);
    }
    int limit = partialTranslation.size();

    List<FeatureValue<String>> features = Generics.newLinkedList();
    double lmScore = getScore(startPos, limit, partialTranslation, f, features);
    features.add(new FeatureValue<String>(featureName, lmScore));

    if (DEBUG) {
      System.out.printf("Final score: %f\n", lmScore);
    }
    return features;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    assert (f.targetPhrase != null);
    double lmScore = getScore(0, f.targetPhrase.size(), f.targetPhrase, null, null);
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, lmScore));
    return features;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return true;
  }
}
