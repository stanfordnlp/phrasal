package edu.stanford.nlp.mt.decoder.util;
 
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.InputProperties;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;

/**
 * Extend Derivation with NNLM scores.
 * 
 * @author Thang Luong
 * 
 * @param <TK>
 */
public class DerivationNNLM<TK, FV> extends Derivation<TK, FV> {
  // neural score for beam reranking, mainly targeting at sorting derivations in BundleBeam.groupBundles()
  // we delay the computation of nnlmScore (in order to do batch ngram query) by keeping track of 
  // the nnlm score of the previous derivation 
  private double prevNNLMScore;
  private double nnlmScore;
  private double lmScore = 0; // traditional LM score
  
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
      DerivationNNLM<TK, FV> base, CombinedFeaturizer<TK, FV> featurizer,
      Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic) {
    super(sourceInputId, rule, insertionPosition, base, featurizer, scorer, heuristic);
    prevNNLMScore = base.nnlmScore;
  }

  @Override
  public String toString() {
    return String.format("tgt=%s, cov=%s [%.3f (score=%0.3f, h=%.3f), prev_nnlm: %3f, nnlm: %3f, lm: %3f]", 
        targetSequence.toString(), sourceCoverage.toString(), score + h, score, h, prevNNLMScore, nnlmScore, lmScore);
  }
  
//  @Override
//  public int compareTo(Derivation<TK, FV> competitor) {
//    //System.err.println("DerivationNNLM compareTo: " + this + " vs. " + ((DerivationNNLM<TK, FV>) competitor));
//    // compare using nnlmScore first
//    int cmp = (int) Math.signum(((DerivationNNLM<TK, FV>) competitor).nnlmScore - nnlmScore);
//    if (cmp != 0) {
//      return cmp;
//    }
//    return super.compareTo(competitor);
//  }
  
  public double getPrevNNLMScore() {
    return prevNNLMScore;
  }
  
//  public void setNNLMScore(double nnlmScore) {
//    this.nnlmScore = nnlmScore;
//  }
  
  public void substituteNNLMScore(double nnlmScore, double lmWeight){
    // set rnnlmScore
    this.nnlmScore = nnlmScore;
    
    // get and set lm score
    for (FeatureValue<FV> feature : localFeatures) {
      if(feature.name.equals(NGramLanguageModelFeaturizer.DEFAULT_FEATURE_NAME)){ // LM
        lmScore = feature.value;
      }
    }
    
    // replace lmScore by rnnlmScore
    score += (nnlmScore-lmScore)*lmWeight;
  }
}
