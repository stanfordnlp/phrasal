package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.lm.KenLanguageModel;
import edu.stanford.nlp.mt.lm.SrcNPLM;
import edu.stanford.nlp.mt.lm.SrcNPLMState;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Featurizer for source-conditioned Neural Probabilistic Language Models (NPLMs).
 * Based on the NGramLanguageModelFeaturizer code.
 * 
 * @author Thang Luong
 */
public class SrcNPLMFeaturizer extends DerivationFeaturizer<IString, String> implements
RuleFeaturizer<IString, String> {
  private static final boolean DEBUG = false;
  public static final String DEFAULT_FEATURE_NAME = "SrcNPLM";

  // in srilm -99 is -infinity
  //  private static final double MOSES_LM_UNKNOWN_WORD_SCORE = -100;

  private final IString startToken;
  private final IString endToken;

  private final String featureName;
  private final SrcNPLM srcNPLM;
  private final KenLanguageModel kenlm;
  private Sequence<IString> srcSent;

  // orders
  private final int tgtOrder;

  public String helpMessage(){
    return "NPLMFeaturizer(nplm=<string>,cache=<int>,kenlm=<string>,id=<string>). kenlm is optional, for back-off LM.";
  }
  /**
   * Constructor called by Phrasal when NPLMFeaturizer appears in
   * [additional-featurizers].
   */
  public SrcNPLMFeaturizer(String...args) throws IOException {
    Properties options = FeatureUtils.argsToProperties(args);
    String nplmFile = PropertiesUtils.getString(options, "nplm", null);
    int cacheSize = PropertiesUtils.getInt(options, "cache", 0);
    String kenlmFile = PropertiesUtils.getString(options, "kenlm", null); // backoff language model
    featureName = PropertiesUtils.getString(options, "id", null);

    if(nplmFile==null || featureName==null) {
      throw new RuntimeException(
          "At least 2 arguments are needed: nplm and id. " + helpMessage());
    }

    // load back-off KenLM (if any)
    if (kenlmFile!=null){
      System.err.println("# NPLMFeaturizer back-off KenLM: " + kenlmFile);
      kenlm = new KenLanguageModel(kenlmFile);
    } else { kenlm = null; }


    // load NPLM
    srcNPLM = new SrcNPLM(nplmFile, cacheSize);
    this.tgtOrder = srcNPLM.getTgtOrder(); // to store state

    this.startToken = srcNPLM.getStartToken();
    this.endToken = srcNPLM.getEndToken();
  }

  /**
   * Compute score and state for a new phrase pair added.
   *
   * @param tgtSent
   * @param srcSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return
   */
  public SrcNPLMState getScore(int tgtStartPos, Sequence<IString> tgtSent, 
      int srcStartPos, Sequence<IString> srcSent, PhraseAlignment alignment){ 
    double lmSumScore = 0;
    int[] ngramIds = null;
    
    for (int pos = tgtStartPos; pos < tgtSent.size(); pos++) {
      ngramIds = srcNPLM.extractNgram(pos, srcSent, tgtSent, alignment, srcStartPos, tgtStartPos);
      double ngramScore = srcNPLM.score(ngramIds);
      if(DEBUG) { System.err.println("  ngram " + srcNPLM.toIString(ngramIds) + "\t" + ngramScore); }
      
      if (ngramScore == Double.NEGATIVE_INFINITY || ngramScore != ngramScore) {
        throw new RuntimeException("! Infinity or Nan NPLM score: " + 
            srcNPLM.toIString(ngramIds) + "\t" + ngramScore);
      }
      lmSumScore += ngramScore;
    }

    // use the last ngramIds to create state (inside SrcNPLMState, we only care about the last tgtOrder-1 indices) 
    SrcNPLMState state = (tgtSent.size()>tgtStartPos) ? new SrcNPLMState(lmSumScore, ngramIds, tgtOrder) : null;
    return state;
  }
  
  /**
   * Extract multiple ngrams and score them all at once. 
   * Should return the same score as getScore.
   * This method is slower than getScore and is used only to test srcNPLM.extractNgrams/scoreMultiNgrams.
   * 
   * @param tgtStartPos
   * @param tgtEndPos
   * @param tgtSent
   * @param srcStartPos
   * @param srcSent
   * @param alignment
   * @return
   */
  public SrcNPLMState getScoreMulti(int tgtStartPos, Sequence<IString> tgtSent,
      int srcStartPos, Sequence<IString> srcSent, PhraseAlignment alignment){

    LinkedList<int[]> ngramList = srcNPLM.extractNgrams(srcSent, tgtSent, alignment, srcStartPos, tgtStartPos);
    double score = 0.0;
    SrcNPLMState state = null;
    int numNgrams = ngramList.size(); 
    if(numNgrams>0){
      double[] ngramScores = srcNPLM.scoreMultiNgrams(ngramList);
      for (int i = 0; i < numNgrams; i++) {
        if(DEBUG) { System.err.println("  ngram " + srcNPLM.toIString(ngramList.get(i)) + "\t" + ngramScores[i]); }
        score += ngramScores[i];
      }

      // use the last ngramIds to create state (inside SrcNPLMState, we only care about the last tgtOrder-1 indices)
      int[] ngramIds = ngramList.getLast();
      state = new SrcNPLMState(score, ngramIds, tgtOrder);
    }

    return state;
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    Sequence<IString> tgtSent = null;
    List<FeatureValue<String>> features = Generics.newLinkedList();

    /*
    LMState priorState = f.prior == null ? null : (LMState) f.prior.getState(this);
    int startIndex = 0;
    if (f.prior == null && f.done) {
      tgtSent = Sequences.wrapStartEnd(
          f.targetPhrase, startToken, endToken);
      startIndex = 1;
    } else if (f.prior == null) {
      tgtSent = Sequences.wrapStart(f.targetPhrase, startToken);
      startIndex = 1;
    } else if (f.done) {
      tgtSent = Sequences.wrapEnd(f.targetPhrase, endToken);
    } else {
      tgtSent = f.targetPhrase;
    }
    LMState state = kenlm.score(tgtSent, startIndex, priorState);
    f.setState(this, state);
    double lmScore = state.getScore();
     */

    // f.targetPrefix includes priorState + targetPhrase
    // f.targetPosition: position in targetPrefix where the targetPhrase starts.
    if (f.done) {
      tgtSent = Sequences.wrapStartEnd(f.targetPrefix, startToken, endToken);
    } else {
      tgtSent = Sequences.wrapStart(f.targetPrefix, startToken);
    }
    
    int srcStartPos = f.sourcePosition;
    int tgtStartPos = f.targetPosition + 1;
    if(DEBUG){ 
      System.err.println("# NPLMFeaturizer: srcStartPos=" + srcStartPos + " tgtStartPos=" + tgtStartPos 
          + ", srcLen=" + srcSent.size() + ", tgtLen=" + tgtSent.size() + ", f=" + f);
      System.err.println("  srcSent=" + tgtSent);
      System.err.println("  tgtSent=" + tgtSent);
      System.err.println("  sequence=" + f.targetPrefix);
      System.err.println("  num untranslated tokens=" + f.numUntranslatedSourceTokens);
    }
    
    SrcNPLMState state = getScore(tgtStartPos, tgtSent, srcStartPos, srcSent, f.rule.abstractRule.alignment);
    //SrcNPLMState state = getScoreMulti(tgtStartPos, tgtSent, srcStartPos, srcSent, f.rule.abstractRule.alignment);
    
    double score = 0.0;
    if (state == null) { // Target-deletion rule
      state = (SrcNPLMState) f.prior.getState(this);
    } else {
      score = state.getScore();
    }
    f.setState(this, state);
    features.add(new FeatureValue<String>(featureName, score));

    if (DEBUG) { System.err.println("Final score: " + score + "\n==================="); }

    return features;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    assert (f.targetPhrase != null);
    double lmScore = 0.0;

    //    if (kenlm!=null) { // score if back-off LM is specified
    //    	lmScore = kenlm.score(f.targetPhrase, 0, null).getScore();
    //    }

    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, lmScore));
    return features;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
    this.srcSent = foreign;
    if (DEBUG) { System.err.println("# Source sent: " + srcSent); }
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return true;
  }
}







