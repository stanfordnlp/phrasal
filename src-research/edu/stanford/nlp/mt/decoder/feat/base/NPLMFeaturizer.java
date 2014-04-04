package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.IOException;
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
import edu.stanford.nlp.mt.lm.NPLMLanguageModel;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.SrcNPLMUtil;
import edu.stanford.nlp.mt.util.Util;
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
  private static final boolean DEBUG = true;
  public static final String DEFAULT_FEATURE_NAME = "NPLM";
  
  // in srilm -99 is -infinity
//  private static final double MOSES_LM_UNKNOWN_WORD_SCORE = -100;

  private final IString startToken;
  private final IString endToken;

  private final String featureName;
  private final NPLMLanguageModel nplm;
  private final KenLanguageModel kenlm;
  
  private Sequence<IString> sourceSent;
  
  // orders
  private final int lmOrder; // lmOrder = srcOrder + tgtOrder
  private final int srcOrder;
  private final int tgtOrder;
  private final int srcWindow; // = (srcOrder-1)/2
  
  // map IString id to NPLM id
  private final int[] srcVocabMap;
  private final int[] tgtVocabMap;
  
  // special tokens
  private final int srcUnkVocabId; 
	private final int tgtUnkVocabId;
	private final int srcStartVocabId;
  private final int tgtStartVocabId;
  private final int srcEndVocabId;

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
    nplm = new NPLMLanguageModel(nplmFile, cacheSize);
    this.lmOrder = nplm.order();
    this.srcOrder = nplm.getSrcOrder();
    this.tgtOrder = nplm.getTgtOrder();
    this.srcWindow = (srcOrder-1)/2;
    this.srcVocabMap = nplm.getSrcVocabMap();
    this.tgtVocabMap = nplm.getTgtVocabMap();
    
    this.startToken = nplm.getStartToken();
    this.endToken = nplm.getEndToken();

    // special tokens
    this.srcUnkVocabId = nplm.getSrcUnkNPLMId();
    this.tgtUnkVocabId = nplm.getTgtUnkNPLMId();
    this.srcStartVocabId = nplm.getSrcStartVocabId();
    this.tgtStartVocabId = nplm.getTgtStartVocabId();
    this.srcEndVocabId = nplm.getSrcEndVocabId();
  }
  
  /**
   * @param f 
   * @param features 
	 * @param isRuleFeaturize -- true if we score rule in isolation, i.e. no access to the source sentence
	 */
  private double getScore(int startPos, int limit, Sequence<IString> translation, Featurizable<IString, String> f, List<FeatureValue<String>> features){ 
    double lmSumScore = 0;
    LMState state = null;
    assert(f!=null);
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    if(DEBUG){ printDebugQuery(startPos, limit, translation, f); }
    
    int i, id;
    for (int pos = startPos; pos < limit; pos++) {;
      int[] ngramIds = new int[lmOrder]; // will be stored in normal order
      
      // get source avg alignment pos within rule
      int srcAvgPos = SrcNPLMUtil.findSrcAvgPos(pos-startPos, alignment); // pos-startPos: position within the local target phrase
      if(srcAvgPos==-1) { // no source alignment, identity translation I-I
        // Thang TODO: the below code should be replaced by a continue statement, but then we have to explicitly compute the state
      	for (i=0; i < srcOrder; i++) { ngramIds[i] = srcUnkVocabId; }
      } else {
        // convert this local srcAvgPos within the current srcPhrase, to the global position within the source sent
        //if(!isRuleFeaturize) 
        srcAvgPos += f.sourcePosition;
        
        // extract src subsequence
        Sequence<IString> sourceSeq = sourceSent; // (isRuleFeaturize) ? f.rule.abstractRule.source : sourceSent;
        int srcLen = sourceSeq.size();
        int srcSeqStart = srcAvgPos-srcWindow;
        int srcSeqEnd = srcAvgPos+srcWindow;
        
        i=0;
        for (int srcPos = srcSeqStart; srcPos <= srcSeqEnd; srcPos++) {
          if(srcPos<0) { id = srcStartVocabId; }
          else if (srcPos>=srcLen) { id = srcEndVocabId; }
          else  { // within range
          	IString srcTok = sourceSeq.get(srcPos);
            if(srcTok.id<srcVocabMap.length) id = srcVocabMap[srcTok.id];
            else { id = srcUnkVocabId; }
          }
          ngramIds[i++] = id; // lmOrder-i-1
        }
      }
      assert(i==srcOrder);
      
      // extract tgt subsequence
      int tgtSeqStart = pos - tgtOrder + 1;
      for (int tgtPos = tgtSeqStart; tgtPos <= pos; tgtPos++) {        
        if(tgtPos<0) { id = tgtStartVocabId; }
        else { 
        	IString tgtTok = translation.get(tgtPos);
          if(tgtTok.id<tgtVocabMap.length) id = tgtVocabMap[tgtTok.id];
          else id = tgtUnkVocabId;
        }
        ngramIds[i++] = id; // lmOrder-i-1
      }
      assert(i==lmOrder);
      
      state = nplm.score(ngramIds);
      double ngramScore = state.getScore();
      
      if (ngramScore == Double.NEGATIVE_INFINITY || ngramScore != ngramScore) {
        // lmSumScore += MOSES_LM_UNKNOWN_WORD_SCORE;
      	System.err.println("! Infinity or Nan NPLM score");
      	printDebugNPLM(startPos, pos, srcAvgPos, ngramIds, ngramScore);
        System.exit(1);
      }
      lmSumScore += ngramScore;
    }
    
    // The featurizer state is the result of the last n-gram query 
    if (state == null) { // Target-deletion rule
      state = (LMState) f.prior.getState(this);
    }
    f.setState(this, state);
    return lmSumScore;
  }

  private void printDebugQuery(int startPos, int limit, Sequence<IString> translation, Featurizable<IString, String> f){
  	System.err.println("# NPLMFeaturizer: srcPos=" + f.sourcePosition 
  			+ " tgtPos=" + startPos + ", limit=" + limit + ", f=" + f);
    System.err.println("  translation=" + translation);
    System.err.println("  targetPrefix=" + f.targetPrefix);
    System.err.println("  targetPhrase=" + f.targetPhrase);
    System.err.println("  alignment=" + f.rule.abstractRule.alignment);
    System.err.println("  lmOrder=" + lmOrder);
  }
  
  private void printDebugNPLM(int startPos, int pos, int srcAvgPos, int[] ngramIds, double ngramScore){
  	System.err.println("  tgtPos=" + (pos-startPos) + ", srcAvgPos=" + srcAvgPos + ", ngram =" + Util.intArrayToString(ngramIds));
    System.err.print("  src words=");
    for (int j = 0; j<srcOrder; j++) {
      System.err.print(" " + nplm.getSrcWord(ngramIds[j]-nplm.getTgtVocabSize()).toString());
    }
    System.err.println();
    
    System.err.print("  tgt words=");

    for (int j = srcOrder; j < lmOrder; j++) {
      System.err.print(" " + nplm.getTgtWord(ngramIds[j]).toString());
    }
    System.err.println("\n  score=" + ngramScore);
  
  }
  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    if (DEBUG) {
      System.err.printf("Sequence: %s, novel phrase: %s, num untranslated tokens: %d\n", f.targetPrefix, f.targetPhrase, f.numUntranslatedSourceTokens);
    }

    Sequence<IString> partialTranslation = null;
    List<FeatureValue<String>> features = Generics.newLinkedList();

    /*
    LMState priorState = f.prior == null ? null : (LMState) f.prior.getState(this);
    int startIndex = 0;
    if (f.prior == null && f.done) {
      partialTranslation = Sequences.wrapStartEnd(
          f.targetPhrase, startToken, endToken);
      startIndex = 1;
    } else if (f.prior == null) {
      partialTranslation = Sequences.wrapStart(f.targetPhrase, startToken);
      startIndex = 1;
    } else if (f.done) {
      partialTranslation = Sequences.wrapEnd(f.targetPhrase, endToken);
    } else {
      partialTranslation = f.targetPhrase;
    }
    LMState state = kenlm.score(partialTranslation, startIndex, priorState);
    f.setState(this, state);
    double lmScore = state.getScore();
    */
    
    // f.targetPrefix includes priorState + targetPhrase
    // f.targetPosition: position in targetPrefix where the targetPhrase starts.
    if (f.done) {
      partialTranslation = Sequences.wrapStartEnd(f.targetPrefix, startToken, endToken);
    } else {
      partialTranslation = Sequences.wrapStart(f.targetPrefix, startToken);
    }
    int startPos = f.targetPosition + 1;
    int limit = partialTranslation.size();
    double lmScore = getScore(startPos, limit, partialTranslation, f, features);
    
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
    this.sourceSent = foreign;
    if (DEBUG) { System.err.println("# Source sent: " + sourceSent); }
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return true;
  }
}


//System.err.println("\n## NPLMFeaturizer initialize: " + this.sourceSent);
//for (ConcreteRule<IString,String> option : options) {
//System.err.println(option);
//}
