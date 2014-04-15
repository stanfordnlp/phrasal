package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.IOException;
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
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.lm.KenLanguageModel;
import edu.stanford.nlp.mt.lm.JointNNLM;
import edu.stanford.nlp.mt.lm.NNLMState;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Featurizer for source-conditioned Neural Probabilistic Language Models (NPLMs).
 * Based on the NGramLanguageModelFeaturizer code.
 * 
 * @author Thang Luong
 */
public class JointNNLMFeaturizer extends DerivationFeaturizer<IString, String> implements
RuleFeaturizer<IString, String> {
  private static final boolean DEBUG = false;
  public static final String DEFAULT_FEATURE_NAME = "JointNNLM";

  // in srilm -99 is -infinity
  //  private static final double MOSES_LM_UNKNOWN_WORD_SCORE = -100;

  private final IString startToken;
  private final IString endToken;

  private final String featureName;
  private final JointNNLM jointNNLM;
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
  public JointNNLMFeaturizer(String...args) throws IOException {
    Properties options = FeatureUtils.argsToProperties(args);
    String nnlmFile = PropertiesUtils.getString(options, "nnlm", null);
    int cacheSize = PropertiesUtils.getInt(options, "cache", 0);
    String kenlmFile = PropertiesUtils.getString(options, "kenlm", null); // backoff language model
    featureName = PropertiesUtils.getString(options, "id", null);

    if(nnlmFile==null || featureName==null) {
      throw new RuntimeException(
          "At least 2 arguments are needed: nplm and id. " + helpMessage());
    }

    // load back-off KenLM (if any)
    if (kenlmFile!=null){
      System.err.println("# NPLMFeaturizer back-off KenLM: " + kenlmFile);
      kenlm = new KenLanguageModel(kenlmFile);
    } else { kenlm = null; }


    // load NPLM
    jointNNLM = new JointNNLM(nnlmFile, cacheSize);
    this.tgtOrder = jointNNLM.getTgtOrder(); // to store state

    this.startToken = TokenUtils.START_TOKEN;
    this.endToken = TokenUtils.END_TOKEN;
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
  public NNLMState getScore(int tgtStartPos, Sequence<IString> tgtSent, 
      int srcStartPos, Sequence<IString> srcSent, PhraseAlignment alignment){ 
    double lmSumScore = 0;
    int[] ngramIds = null;
    
    for (int pos = tgtStartPos; pos < tgtSent.size(); pos++) {
      ngramIds = jointNNLM.extractNgram(pos, srcSent, tgtSent, alignment, srcStartPos, tgtStartPos);
      double ngramScore = jointNNLM.scoreNgram(ngramIds);
      if(DEBUG) { System.err.println("  ngram " + jointNNLM.toIString(ngramIds) + "\t" + ngramScore); }
      
      if (ngramScore == Double.NEGATIVE_INFINITY || ngramScore != ngramScore) {
        throw new RuntimeException("! Infinity or Nan NPLM score: " + 
            jointNNLM.toIString(ngramIds) + "\t" + ngramScore);
      }
      lmSumScore += ngramScore;
    }

    // use the last ngramIds to create state (inside NNLMState, we only care about the last tgtOrder-1 indices) 
    NNLMState state = (tgtSent.size()>tgtStartPos) ? new NNLMState(lmSumScore, ngramIds, tgtOrder) : null;
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
  public NNLMState getScoreMulti(int tgtStartPos, Sequence<IString> tgtSent,
      int srcStartPos, Sequence<IString> srcSent, PhraseAlignment alignment){

    LinkedList<int[]> ngramList = (LinkedList<int[]>) jointNNLM.extractNgrams(srcSent, tgtSent, alignment, srcStartPos, tgtStartPos);
    double score = 0.0;
    NNLMState state = null;
    int numNgrams = ngramList.size(); 
    if(numNgrams>0){
      double[] ngramScores = jointNNLM.scoreNgrams(ngramList);
      for (int i = 0; i < numNgrams; i++) {
        if(DEBUG) { System.err.println("  ngram " + jointNNLM.toIString(ngramList.get(i)) + "\t" + ngramScores[i]); }
        score += ngramScores[i];
      }

      // use the last ngramIds to create state (inside SrcNPLMState, we only care about the last tgtOrder-1 indices)
      int[] ngramIds = ngramList.getLast();
      state = new NNLMState(score, ngramIds, tgtOrder);
    }

    return state;
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    Sequence<IString> tgtSent = null;
    List<FeatureValue<String>> features = Generics.newLinkedList();

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
    
    NNLMState state = getScore(tgtStartPos, tgtSent, srcStartPos, srcSent, f.rule.abstractRule.alignment);
    //SrcNPLMState state = getScoreMulti(tgtStartPos, tgtSent, srcStartPos, srcSent, f.rule.abstractRule.alignment);
    
    double score = 0.0;
    if (state == null) { // Target-deletion rule
      state = (NNLMState) f.prior.getState(this);
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







