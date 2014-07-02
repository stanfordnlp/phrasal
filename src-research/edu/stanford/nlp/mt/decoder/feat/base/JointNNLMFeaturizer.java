package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.mt.lm.JointNNLM;
import edu.stanford.nlp.mt.lm.NNLMState;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.util.Generics;

/**
 * Featurizer for JointNNLM.
 * 
 * @author Thang Luong
 */
public class JointNNLMFeaturizer extends NNLMFeaturizer {
  private static final boolean DEBUG = false;
  
  /**
   * Constructor called by Phrasal when JointNNLMFeaturizer appears in
   * [additional-featurizers].
   */
  public JointNNLMFeaturizer(String...args) throws IOException {
    super(args);
    
    // load JointNNLM
    nnlm = new JointNNLM(nnlmFile, cacheSize, 1); // miniBatchSize=1
    this.tgtOrder = nnlm.getTgtOrder(); // to store state
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
      ngramIds = nnlm.extractNgram(pos, srcSent, tgtSent, alignment, srcStartPos, tgtStartPos);
      double ngramScore = nnlm.scoreNgram(ngramIds);
      if(DEBUG) { System.err.println("  ngram " + nnlm.toIString(ngramIds) + "\t" + ngramScore); }
      
      if (ngramScore == Double.NEGATIVE_INFINITY || ngramScore != ngramScore) {
        throw new RuntimeException("! Infinity or Nan NPLM score: " + 
            nnlm.toIString(ngramIds) + "\t" + ngramScore);
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

    int[][] ngrams = nnlm.extractNgrams(srcSent, tgtSent, alignment, srcStartPos, tgtStartPos);
    double score = 0.0;
    NNLMState state = null;
    int numNgrams = ngrams.length; 
    if(numNgrams>0){
      double[] ngramScores = nnlm.scoreNgrams(ngrams);
      for (int i = 0; i < numNgrams; i++) {
        if(DEBUG) { System.err.println("  ngram " + nnlm.toIString(ngrams[i]) + "\t" + ngramScores[i]); }
        score += ngramScores[i];
      }

      // use the last ngramIds to create state (inside SrcNPLMState, we only care about the last tgtOrder-1 indices)
      int[] ngramIds = ngrams[numNgrams-1];
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


}

//private final KenLanguageModel kenlm;
//String kenlmFile = PropertiesUtils.getString(options, "kenlm", null); // backoff language model
//// load back-off KenLM (if any)
//if (kenlmFile!=null){
//  System.err.println("# NPLMFeaturizer back-off KenLM: " + kenlmFile);
//  kenlm = new KenLanguageModel(kenlmFile);
//} else { kenlm = null; }


//    if (kenlm!=null) { // score if back-off LM is specified
//      lmScore = kenlm.score(f.targetPhrase, 0, null).getScore();
//    }

