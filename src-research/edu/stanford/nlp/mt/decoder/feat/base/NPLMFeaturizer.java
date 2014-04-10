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
public class NPLMFeaturizer extends DerivationFeaturizer<IString, String> implements
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
  private final int order; // lmOrder = srcOrder + tgtOrder
  private final int srcOrder;
  private final int tgtOrder;

  public String helpMessage(){
  	return "NPLMFeaturizer(nplm=<string>,cache=<int>,kenlm=<string>,id=<string>). kenlm is optional, for back-off LM.";
  }
  /**
   * Constructor called by Phrasal when NPLMFeaturizer appears in
   * [additional-featurizers].
   */
  public NPLMFeaturizer(String...args) throws IOException {
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
    this.order = srcNPLM.order();
    this.srcOrder = srcNPLM.getSrcOrder();
    this.tgtOrder = srcNPLM.getTgtOrder();
    
    this.startToken = srcNPLM.getStartToken();
    this.endToken = srcNPLM.getEndToken();
  }
  
  /**
   * @param f 
   * @param features 
     * @param isRuleFeaturize -- true if we score rule in isolation, i.e. no access to the source sentence
     */
  private double getScore(int tgtStartPos, int tgtEndPos, Sequence<IString> tgtSent, Featurizable<IString, String> f){
    assert(f!=null);
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    if(DEBUG){ printDebugQuery(tgtStartPos, tgtEndPos, tgtSent, f); }
    
    int srcStartPos = f.sourcePosition;
    
//    SrcNPLMState state = getScore(tgtStartPos, tgtEndPos, tgtSent, srcStartPos, srcSent, alignment);
    SrcNPLMState state = getScoreMulti(tgtStartPos, tgtEndPos, tgtSent, srcStartPos, srcSent, alignment);
    double score;
    
    
    // The featurizer state is the result of the last n-gram query 
    if (state == null) { // Target-deletion rule
      state = (SrcNPLMState) f.prior.getState(this);
      score = 0.0;
    } else {
      score = state.getScore();
    }
    f.setState(this, state);
    
    return score;
  }
  
  /**
   * Extract multiple ngrams and score them all at once.
   * 
   * @param tgtStartPos
   * @param tgtEndPos
   * @param tgtSent
   * @param srcStartPos
   * @param srcSent
   * @param alignment
   * @return
   */
  public SrcNPLMState getScoreMulti(int tgtStartPos, int tgtEndPos, Sequence<IString> tgtSent,
      int srcStartPos, Sequence<IString> srcSent, PhraseAlignment alignment){
    
    LinkedList<int[]> ngramList = srcNPLM.extractNgrams(srcSent, tgtSent, alignment, srcStartPos, tgtStartPos);
    double score = 0.0;
    SrcNPLMState state = null;
    if(ngramList.size()>0){
      double[] ngramScores = srcNPLM.scoreMultiNgrams(ngramList);
      for (double ngramScore : ngramScores) {
        score += ngramScore;
      }
      
      // use the last ngramIds to create state (inside SrcNPLMState, we only care about the last tgtOrder-1 indices)
      int[] ngramIds = ngramList.getLast();
      state = new SrcNPLMState(score, ngramIds, tgtOrder);
    }
    
    return state;
  }
    
  private void printDebugQuery(int tgtStartPos, int tgtEndPos, Sequence<IString> translation, Featurizable<IString, String> f){
  	System.err.println("# NPLMFeaturizer: srcStartPos=" + f.sourcePosition 
  			+ " tgtStartPos=" + tgtStartPos + ", tgtEndPos=" + tgtEndPos + ", f=" + f);
    System.err.println("  translation=" + translation);
    System.err.println("  targetPrefix=" + f.targetPrefix);
    System.err.println("  targetPhrase=" + f.targetPhrase);
    System.err.println("  alignment=" + f.rule.abstractRule.alignment);
    System.err.println("  lmOrder=" + order);
  }
  
  private void printDebugNPLM(int startPos, int pos, int srcAvgPos, int[] ngramIds, double ngramScore){
  	System.err.println("  tgtPos=" + (pos-startPos) + ", srcAvgPos=" + srcAvgPos + 
  	    ", ngram =" + Arrays.toString(ngramIds));
    System.err.print("  src words=");
    for (int j = 0; j<srcOrder; j++) {
      System.err.print(" " + srcNPLM.getSrcWord(ngramIds[j]-srcNPLM.getTgtVocabSize()).toString());
    }
    System.err.println();
    
    System.err.print("  tgt words=");

    for (int j = srcOrder; j < order; j++) {
      System.err.print(" " + srcNPLM.getTgtWord(ngramIds[j]).toString());
    }
    System.err.println("\n  score=" + ngramScore);
  
  }
  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    if (DEBUG) {
      System.err.printf("Sequence: %s, novel phrase: %s, num untranslated tokens: %d\n", f.targetPrefix, f.targetPhrase, f.numUntranslatedSourceTokens);
    }

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
    int tgtStartPos = f.targetPosition + 1;
    int tgtEndPos = tgtSent.size()-1;
    double lmScore = getScore(tgtStartPos, tgtEndPos, tgtSent, f);
    
    features.add(new FeatureValue<String>(featureName, lmScore));
    
    if (DEBUG) {
      System.err.printf("Final score: %f%n", lmScore);
      System.err.println("===================");
    }

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

//this.srcWindow = (srcOrder-1)/2;
//this.srcVocabMap = srcNPLM.getSrcVocabMap();
//this.tgtVocabMap = srcNPLM.getTgtVocabMap();
//
//// special tokens
//this.srcUnkVocabId = srcNPLM.getSrcUnkNPLMId();
//this.tgtUnkVocabId = srcNPLM.getTgtUnkNPLMId();
//this.srcStartVocabId = srcNPLM.getSrcStartVocabId();
//this.tgtStartVocabId = srcNPLM.getTgtStartVocabId();
//this.srcEndVocabId = srcNPLM.getSrcEndVocabId();

//private final int srcWindow; // = (srcOrder-1)/2
//
//// map IString id to NPLM id
//private final int[] srcVocabMap;
//private final int[] tgtVocabMap;
//
//// special tokens
//private final int srcUnkVocabId; 
//private final int tgtUnkVocabId;
//private final int srcStartVocabId;
//private final int tgtStartVocabId;
//private final int srcEndVocabId;

//public SrcNPLMState getScore(int tgtStartPos, int tgtEndPos, Sequence<IString> tgtSent, 
//    int srcStartPos, Sequence<IString> srcSent, PhraseAlignment alignment){ 
//  double lmSumScore = 0;
//  
//  int i, id;
//  int[] ngramIds = new int[order]; // will be stored in normal order
//  for (int pos = tgtStartPos; pos <=tgtEndPos; pos++) {
//    // get source avg alignment pos within rule
//    int srcAvgPos = SrcNPLMUtil.findSrcAvgPos(pos-tgtStartPos, alignment); // pos-startPos: position within the local target phrase
//    if(srcAvgPos==-1) { // no source alignment, identity translation I-I
//      continue;
// //     for (i=0; i < order; i++) { ngramIds[i] = srcUnkVocabId; }
//    } else {
//      // convert this local srcAvgPos within the current srcPhrase, to the global position within the source sent
//      srcAvgPos += srcStartPos;
//      
//      // extract src subsequence
//      Sequence<IString> sourceSeq = srcSent;
//      int srcLen = sourceSeq.size();
//      int srcSeqStart = srcAvgPos-srcWindow;
//      int srcSeqEnd = srcAvgPos+srcWindow;
//      
//      i=0;
//      for (int srcPos = srcSeqStart; srcPos <= srcSeqEnd; srcPos++) {
//        if(srcPos<0) { id = srcStartVocabId; }
//        else if (srcPos>=srcLen) { id = srcEndVocabId; }
//        else  { // within range
//          IString srcTok = sourceSeq.get(srcPos);
//          if(srcTok.id<srcVocabMap.length) id = srcVocabMap[srcTok.id];
//          else { id = srcUnkVocabId; }
//        }
//        ngramIds[i++] = id; // lmOrder-i-1
//      }
//    }
//    assert(i==srcOrder);
//    
//    // extract tgt subsequence
//    int tgtSeqStart = pos - tgtOrder + 1;
//    for (int tgtPos = tgtSeqStart; tgtPos <= pos; tgtPos++) {        
//      if(tgtPos<0) { id = tgtStartVocabId; }
//      else { 
//        IString tgtTok = tgtSent.get(tgtPos);
//        if(tgtTok.id<tgtVocabMap.length) id = tgtVocabMap[tgtTok.id];
//        else id = tgtUnkVocabId;
//      }
//      ngramIds[i++] = id; // lmOrder-i-1
//    }
//    assert(i==order);
//    
//    double ngramScore = srcNPLM.score(ngramIds);
//    
//    if (ngramScore == Double.NEGATIVE_INFINITY || ngramScore != ngramScore) {
//      // lmSumScore += MOSES_LM_UNKNOWN_WORD_SCORE;
//      printDebugNPLM(tgtStartPos, pos, srcAvgPos, ngramIds, ngramScore);
//      throw new RuntimeException("! Infinity or Nan NPLM score");
//    }
//    lmSumScore += ngramScore;
//  }
//  
//  // use the last ngramIds to create state (inside SrcNPLMState, we only care about the last tgtOrder-1 indices) 
//  SrcNPLMState state = (tgtEndPos>=tgtStartPos) ? new SrcNPLMState(lmSumScore, ngramIds, tgtOrder) : null;
//  return state;
// }

