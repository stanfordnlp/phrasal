package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.InsertedStartEndToken;
import edu.stanford.nlp.mt.base.InsertedStartToken;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.sparse.SparseFeatureUtils;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

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
  private final IString startToken;
  private final IString endToken;

  private final boolean isClassBased;
  private final TargetClassMap targetClassMap;

  /**
   * Constructor.
   * 
   * @param lm
   */
  public NGramLanguageModelFeaturizer(LanguageModel<IString> lm) {
    this.lm = lm;
    featureName = DEFAULT_FEATURE_NAME;
    this.lmOrder = lm.order();
    this.startToken = lm.getStartToken();
    this.endToken = lm.getEndToken();
    this.isClassBased = false;
    this.targetClassMap = null;
  }

  /**
   * Constructor called by Phrasal when NGramLanguageModelFeaturizer appears in
   * [additional-featurizers].
   * 
   * The first argument is always the language model filename and the second
   * argument is always the feature name.
   * 
   * Additional arguments are named parameters.
   */
  public NGramLanguageModelFeaturizer(String...args) throws IOException {
    if (args.length < 2) {
      throw new RuntimeException(
          "At least two arguments are needed: LM file name and LM ID");
    }
    // Load the LM
    this.lm = LanguageModelFactory.load(args[0]);
    this.lmOrder = lm.order();
    this.startToken = lm.getStartToken();
    this.endToken = lm.getEndToken();

    // Set the feature name
    this.featureName = args[1];

    // Named parameters
    Properties options = SparseFeatureUtils.argsToProperties(args);
    this.isClassBased = PropertiesUtils.getBool(options, "classBased", false);
    this.targetClassMap = isClassBased ? TargetClassMap.getInstance() : null;
  }

  /**
   * Getter for the underlying <code>LanguageModel</code>.
   * 
   * @return
   */
  public LanguageModel<IString> getLM() {
    return lm;
  }

  /**
   * Score a target sequence.
   * 
   * @param startPos
   * @param limit
   * @param targetSequence
   * @param f
   * @param features
   * @return
   */
  private double getScore(int startPos, Sequence<IString> targetSequence, 
      Featurizable<IString, String> f) {
    if (isClassBased) {
      int leftEdge = Math.max(0, startPos - lmOrder + 1);
      targetSequence = toClassRepresentation(leftEdge, targetSequence);
    }

    // Score targetSequence
    double lmSumScore = 0.0;
    LMState state = null;
    for (int pos = startPos, limit = targetSequence.size(); pos < limit; pos++) {
      final int seqStart = Math.max(0, pos - lmOrder + 1);
      Sequence<IString> ngram = targetSequence.subsequence(seqStart, pos + 1);
      state = lm.score(ngram);
      double ngramScore = state.getScore();
      if (ngramScore == Double.NEGATIVE_INFINITY || ngramScore != ngramScore) {
        lmSumScore += MOSES_LM_UNKNOWN_WORD_SCORE;
        continue;
      }
      lmSumScore += ngramScore;
      if (DEBUG) {
        System.err.printf("  n-gram: %s score: %f%n", ngram, ngramScore);
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

  /**
   * Convert a lexical n-gram to a class-based n-gram.
   * 
   * @param leftEdge 
   * @param targetSequence
   * @return
   */
  private Sequence<IString> toClassRepresentation(int leftEdge, Sequence<IString> targetSequence) {
    // No need to copy the elements to the left of leftEdge, but allocate
    // space for them so that the indices don't need to be changed.
    IString[] array = new IString[targetSequence.size()];
    for (int i = leftEdge; i < array.length; ++i) {
      array[i] = targetClassMap.get(targetSequence.get(i));
    }
    return new SimpleSequence<IString>(true, array);
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    if (DEBUG) {
      System.err.printf("Sequence: %s%n\tNovel Phrase: %s%n",
          f.targetPrefix, f.targetPhrase);
      System.err.printf("Untranslated tokens: %d%n", f.numUntranslatedSourceTokens);
      System.err.println("ngram scoring:");
    }
    
    // TODO(spenceg): If we remove targetPrefix---which we should---then we'd need to retrieve the
    // prior state to perform this calculation.
    int startPos = f.targetPosition + 1;
    final Sequence<IString> partialTranslation = f.done ? new InsertedStartEndToken<IString>(
        f.targetPrefix, startToken, endToken) :
          new InsertedStartToken<IString>(f.targetPrefix, startToken);

    final double lmScore = getScore(startPos, partialTranslation, f);
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, lmScore));

    if (DEBUG) {
      System.err.printf("Final score: %f%n", lmScore);
      System.err.println("===================");
    }
    return features;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    assert (f.targetPhrase != null);
    double lmScore = getScore(0, f.targetPhrase, null);
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
