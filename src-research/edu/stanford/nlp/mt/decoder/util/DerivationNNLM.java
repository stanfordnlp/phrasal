package edu.stanford.nlp.mt.decoder.util;
 
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Extend Derivation with NNLM scores.
 * 
 * @author Thang Luong
 * 
 * @param <TK>
 */
public class DerivationNNLM<TK, FV> extends Derivation<TK, FV> {
  private static final boolean DEBUG = false;
  // neural score for beam reranking, mainly targeting at sorting derivations in BundleBeam.groupBundles()
  // we delay the computation of nnlmScore (in order to do batch ngram query) by keeping track of 
  // the nnlm score of the previous derivation 
  private double prevNNLMScore;
  private double nnlmScore;
  
  private StringBuilder nnlmDebugStringBuilder;
  
  /**
   * Constructor for null hypotheses (root of translation lattice).
   * 
   * @param sourceInputId
   * @param sourceSequence
   * @param sourceInputProperties 
   * @param heuristic
   * @param scorer
   * @param ruleList
   */
  public DerivationNNLM(int sourceInputId, Sequence<TK> sourceSequence,
      InputProperties sourceInputProperties, SearchHeuristic<TK, FV> heuristic,
      Scorer<FV> scorer,
      List<List<ConcreteRule<TK,FV>>> ruleList) {
    super(sourceInputId, sourceSequence, sourceInputProperties, heuristic, scorer, ruleList);
    prevNNLMScore = 0;
    nnlmScore = 0;
    if (DEBUG) { nnlmDebugStringBuilder = new StringBuilder(); }
  }

  /**
   * Constructor for derivation/hypothesis expansion.
   * 
   * @param sourceInputId
   * @param rule
   * @param insertionPosition
   * @param base
   * @param featurizer
   * @param scorer
   * @param heuristic
   */
  public DerivationNNLM(int sourceInputId,
      ConcreteRule<TK,FV> rule, int insertionPosition,
      DerivationNNLM<TK, FV> base, FeatureExtractor<TK, FV> featurizer,
      Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic) {
    super(sourceInputId, rule, insertionPosition, base, featurizer, scorer, heuristic);
    prevNNLMScore = base.nnlmScore;
    if (DEBUG) { nnlmDebugStringBuilder = new StringBuilder(base.nnlmDebugStringBuilder.toString()); }
  }

  @Override
  public String toString() {
    if (DEBUG) { 
      return String.format("%s %s [%.3f (score=%.3f, h=%.3f), prev_nnlm: %.3f, nnlm: %.3f, debug: %s]", 
          targetSequence.toString(), sourceCoverage.toString(), score + h, score, h, prevNNLMScore, nnlmScore, nnlmDebugStringBuilder.toString());
    } else {
      return String.format("%s %s [%.3f (score=%.3f, h=%.3f), prev_nnlm: %.3f, nnlm: %.3f]", 
          targetSequence.toString(), sourceCoverage.toString(), score + h, score, h, prevNNLMScore, nnlmScore);
    }
  }
  
  public void updateDebugMsg(String msg) {
    nnlmDebugStringBuilder.append("\n" + msg);
  }
  
  public double getPrevNNLMScore() {
    return prevNNLMScore;
  }
  
  /**
   * Set the nnlm score for the current derivation. 
   * Also replace the traditional lm score with the nnlm score.
   * 
   * @param incNNLMScore
   * @param lmWeight
   */
  public void updateNNLMScore(double incNNLMScore, double localLMScore, double lmWeight){
    // update rnnlmScore
    this.nnlmScore = prevNNLMScore + incNNLMScore;
    
    // replace lmScore by rnnlmScore
    score += (incNNLMScore-localLMScore)*lmWeight;
  }
  
  /**
   * @return the local LM score for this recently added phrase pair.
   */
  public double getLocalLMScore(){
    double lmScore = 0;
    for (FeatureValue<FV> feature : localFeatures) {
      if(feature.name.equals(NGramLanguageModelFeaturizer.DEFAULT_FEATURE_NAME)){ // LM
        lmScore = feature.value;
      }
    }
    return lmScore;
  }
}
