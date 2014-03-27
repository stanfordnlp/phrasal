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
import edu.stanford.nlp.mt.neural.Util;
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
  public static final String DEFAULT_FEATURE_NAME = "NPLM";
  
  // in srilm -99 is -infinity
  private static final double MOSES_LM_UNKNOWN_WORD_SCORE = -100;
 
  private final String featureName;
  private final NPLMLanguageModel nplm;
  private final KenLanguageModel kenlm;
  private final boolean addContextFeatures;
  private String[] contextFeatureNames;
  
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
  private final int tgtStartVocabId;

  public String helpMessage(){
  	return "NPLMFeaturizer(nplm=<string>,kenlm=<string>,id=<string>). kenlm is optional.";
  }
  /**
   * Constructor called by Phrasal when NPLMFeaturizer appears in
   * [additional-featurizers].
   */
  public NPLMFeaturizer(String...args) throws IOException {
    Properties options = FeatureUtils.argsToProperties(args);
    String nplmFile = PropertiesUtils.getString(options, "nplm", null);
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
    nplm = new NPLMLanguageModel(nplmFile);
    this.lmOrder = nplm.order();
    this.srcOrder = nplm.getSrcOrder();
    this.tgtOrder = nplm.getTgtOrder();
    this.srcWindow = (srcOrder-1)/2;
    this.srcVocabMap = nplm.getSrcVocabMap();
    this.tgtVocabMap = nplm.getTgtVocabMap();
    
    // special tokens
    this.srcUnkVocabId = nplm.getSrcUnkVocabId();
    this.tgtUnkVocabId = nplm.getTgtUnkVocabId();
    this.tgtStartVocabId = nplm.getTgtStartVocabId();
    
    // context features
    this.addContextFeatures = args.length > 2 ? true : false;
    contextFeatureNames = addContextFeatures ? new String[lmOrder] : null;
  }
  
  /**
   * @param f 
   * @param features 
	 * @param isRuleFeaturize -- true if we score rule in isolation, i.e. no access to the source sentence
	 */
  private double getScore(int startPos, int limit, Sequence<IString> translation, 
      Featurizable<IString, String> f, List<FeatureValue<String>> features){ //, boolean isRuleFeaturize) {
    double lmSumScore = 0;
    LMState state = null;
    
    assert(f!=null);
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    
//    int tgtLength = f.targetPhrase.size();
    if(DEBUG){// && tgtLength>2) {
      System.err.println("# NPLMFeaturizer:"  //getScore, isRuleFeaturize=" + isRuleFeaturize
          + " startPos=" + startPos + ", srcPos=" + f.sourcePosition + ", limit=" + limit + ", f=" + f);
      System.err.println("  translation=" + translation);
      System.err.println("  targetPrefix=" + f.targetPrefix);
      System.err.println("  targetPhrase=" + f.targetPhrase);
      System.err.println("  lmOrder=" + lmOrder);
    }
    
    for (int pos = startPos; pos < limit; pos++) {
      // create id array
      int i;
      int[] ngramIds = new int[lmOrder]; // will be stored in reverse order
      
      // get source avg alignment pos within rule
      int srcAvgPos = Util.findSrcAvgPos(pos-startPos, alignment); // pos-startPos: position within the local target phrase
      if(srcAvgPos==-1) { // no source alignment, add <unk>
        for(i=0; i<srcOrder; i++) ngramIds[lmOrder-i-1] = srcUnkVocabId;
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
          int id = srcUnkVocabId;
          if(srcPos>=0 && srcPos<srcLen) { // within range
          	IString srcTok = sourceSeq.get(srcPos);
            if(srcTok.id<srcVocabMap.length) id = srcVocabMap[srcTok.id]; 
          }
          ngramIds[lmOrder-i-1] = id;
          i++;
        }
      }
      assert(i==srcOrder);
      
      // extract tgt subsequence
      int tgtSeqStart = pos - tgtOrder + 1;
      for (int tgtPos = tgtSeqStart; tgtPos <= pos; tgtPos++) {        
        int id = tgtStartVocabId;
        if(tgtPos>=0) { 
        	IString tgtTok = translation.get(tgtPos);
          if(tgtTok.id<tgtVocabMap.length) id = tgtVocabMap[tgtTok.id];
          else id = tgtUnkVocabId;
        }
        ngramIds[lmOrder-i-1] = id;
        i++;
      }
      assert(i==lmOrder);
      
      state = nplm.score(ngramIds);
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
      
      if(DEBUG) { // && tgtLength>2){
        System.err.println(" # tgtPos=" + (pos-startPos));
        System.err.println("  srcAvgPos=" + srcAvgPos);
        System.err.println("  ngram reverse=" + Util.intArrayToString(ngramIds));
        System.err.print("  src words=");
        for (int j = lmOrder-1; j >= (lmOrder-srcOrder); j--) {
          System.err.print(" " + nplm.getSrcWord(ngramIds[j]).toString());
        }
        System.err.println();
        
        System.err.print("  tgt words=");
        for (int j = tgtOrder-1; j >= 0; j--) {
          System.err.print(" " + nplm.getTgtWord(ngramIds[j]).toString());
        }
        System.err.println("  score=" + ngramScore);
      }
    }
    
    // The featurizer state is the result of the last n-gram query 
    // Don't set state for rule queries
    if (state == null) {
      // Target-deletion rule
      state = (LMState) f.prior.getState(this);
    }
    f.setState(this, state);
    return lmSumScore;
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    IString startToken = nplm.getStartToken();
    IString endToken = nplm.getEndToken();

    // TODO(spenceg): If we remove targetPrefix---which we should---then we'd need to retrieve the
    // prior state to perform this calculation.
    Sequence<IString> partialTranslation;
    int startPos = f.targetPosition + 1;
    if (f.done) {
      partialTranslation = Sequences.wrapStartEnd(
          f.targetPrefix, startToken, endToken);
    } else {
      partialTranslation = Sequences.wrapStart(
          f.targetPrefix, startToken);
    }
    int limit = partialTranslation.size();

    List<FeatureValue<String>> features = Generics.newLinkedList();
    double lmScore = getScore(startPos, limit, partialTranslation, f, features); //, false);
    features.add(new FeatureValue<String>(featureName, lmScore));
    
    return features;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    assert (f.targetPhrase != null);
    double lmScore = 0.0;
    
    if (kenlm!=null) { // score if back-off LM is specified
    	lmScore = kenlm.score(f.targetPhrase, 0, null).getScore();
    }
    
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, lmScore));
    return features;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
    this.sourceSent = foreign;
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