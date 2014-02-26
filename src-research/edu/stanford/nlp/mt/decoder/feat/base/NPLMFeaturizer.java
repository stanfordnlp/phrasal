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
import edu.stanford.nlp.mt.lm.KenLanguageModel;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.util.Generics;

/**
 * Featurizer for Neural Probabilistic Language Models (NPLMs).
 * Based on the NGramLanguageModelFeaturizer code.
 * 
 * @author Thang Luong
 */
public class NPLMFeaturizer extends DerivationFeaturizer<IString, String> implements
   RuleFeaturizer<IString, String> {
  private static final boolean DEBUG = true;
  public static final String DEFAULT_FEATURE_NAME = "NPLM";
  
  // in srilm -99 is -infinity
  private static final double MOSES_LM_UNKNOWN_WORD_SCORE = -100;
 
  private final String featureName;
  private final KenLanguageModel lm; // explicitly use KenLanguageModel which supports loading of NPLMs
  private final int lmOrder;
  
  private final boolean addContextFeatures;
  private String[] contextFeatureNames;

//Thang Feb14: for src-conditioned NPLM
// private boolean srcConditioned = false;
// private int srcOrder; // tgtOrder = order - srcOrder
// private int tgtVocabSize; // the src-conditioned NPLM has as vocab tgtVocabSize tgt words followed by src words.
// // Thang Feb14: for src-conditioned NPLM
// this.srcOrder = srcOrder;
// this.tgtVocabSize = tgtVocabSize;
// this.srcConditioned = true;

  /**
   * Constructor.
   * 
   * @param lm
   */
  public NPLMFeaturizer(KenLanguageModel lm) {
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
  public NPLMFeaturizer(String...args) throws IOException {
    if (args.length < 2)
      throw new RuntimeException(
          "At least two arguments are needed: LM file name and LM ID");
    featureName = args[1];
    
    // load KenLM
    lm = new KenLanguageModel(args[0]);
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
    
    // Thang Feb14
    if(DEBUG && f!=null && !f.rule.abstractRule.alignment.toString().equals("I-I")){
      if(f.sourcePhrase.size()>1 && f.targetPhrase.size()>1) { 
        System.err.println("# NPLMFeaturizer featurize: " + f);
        System.err.printf("# Sequence: %s\tNovel Phrase: %s\t", f.targetPrefix, f.targetPhrase);
        System.err.printf("# Untranslated tokens: %d\n", f.numUntranslatedSourceTokens);
        
        if(f.prior!=null){
          System.err.println("# Prior: " + f.prior);
          System.err.printf("# Sequence: %s\tNovel Phrase: %s\t", f.prior.targetPrefix, f.prior.targetPhrase);
          System.err.printf("# Untranslated tokens: %d\n", f.prior.numUntranslatedSourceTokens);
        }
        
//        System.err.printf("Final score: %f\n", lmScore);
      }
//      System.exit(1);
    }
    for (int pos = startPos; pos < limit; pos++) {
      int seqStart = pos - lmOrder + 1;
      if (seqStart < 0)
        seqStart = 0;
      Sequence<IString> ngram = translation.subsequence(seqStart, pos + 1);
      state = lm.score(ngram);
      
      // Thang Feb14
      if(DEBUG && f!=null && !f.rule.abstractRule.alignment.toString().equals("I-I")){
        if(f.sourcePhrase.size()>1 && f.targetPhrase.size()>1) { 
          System.err.println("ngram: " + ngram + "\tstate: " + state);
        }
      }
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
//      if (DEBUG) {
//        System.out.printf("\tn-gram: %s score: %f\n", ngram, ngramScore);
//      }
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
    // Thang Feb14
    System.err.println("# NPLMFeaturizer initialize");
    for (ConcreteRule<IString,String> option : options) {
      System.err.println(option);
    }
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return true;
  }
}
