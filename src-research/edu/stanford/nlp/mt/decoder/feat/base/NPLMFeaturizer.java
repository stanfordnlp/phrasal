package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.InsertedStartEndToken;
import edu.stanford.nlp.mt.base.InsertedStartToken;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.lm.KenLanguageModel;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.neural.Util;
import edu.stanford.nlp.util.Generics;

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
  private final KenLanguageModel lm; // explicitly use KenLanguageModel which supports loading of NPLMs
  private final boolean addContextFeatures;
  private String[] contextFeatureNames;

  private final String INPUT_VOCAB_SIZE = "input_vocab_size";
  private final String OUTPUT_VOCAB_SIZE = "output_vocab_size";
  private final String SRC_ORDER = "src_ngram_size";
  private final IString UNK = new IString("<unk>");
  private final IString START = new IString("<s>");
  
  private Sequence<IString> sourceSent;
  private final int lmOrder; // lmOrder = srcOrder + tgtOrder
  private final int srcOrder;
  private final int tgtOrder;
  private final int srcWindow; // = (srcOrder-1)/2
  private final Map<IString, Integer> srcVocabMap;
  private final Map<IString, Integer> tgtVocabMap;
  private final Map<Integer, IString> srcReverseVocabMap;
  private final Map<Integer, IString> tgtReverseVocabMap;
  
  /**
   *
   */
  public LanguageModel<IString> getLM() {
    return lm;
  }

  /**
   * Constructor called by Phrasal when NPLMFeaturizer appears in
   * [additional-featurizers].
   */
  public NPLMFeaturizer(String...args) throws IOException {
    if (args.length < 2)
      throw new RuntimeException(
          "At least 2 arguments are needed: LM file name, src and LM ID");
    featureName = args[1];
    
    // load KenLM
    String modelFile = args[0];
    lm = new KenLanguageModel(modelFile);
    this.lmOrder = lm.order();
    
    // context features
    this.addContextFeatures = args.length > 2 ? true : false;
    contextFeatureNames = addContextFeatures ? new String[lmOrder] : null;
    
    // load src-conditioned info
    System.err.println("# Loading NPLMFeaturizer");
    BufferedReader br = new BufferedReader(new FileReader(modelFile));
    String line;
    int vocabSize=0; // = tgtVocabSize + srcVocabSize
    int tgtVocabSize=0;
    int srcOrder=0;
    while((line=br.readLine())!=null){
      if (line.startsWith(INPUT_VOCAB_SIZE)) {
        vocabSize = Integer.parseInt(line.substring(INPUT_VOCAB_SIZE.length()+1));
      } else if (line.startsWith(OUTPUT_VOCAB_SIZE)) {
        tgtVocabSize = Integer.parseInt(line.substring(OUTPUT_VOCAB_SIZE.length()+1));
      } else if (line.startsWith(SRC_ORDER)) {
        srcOrder = Integer.parseInt(line.substring(SRC_ORDER.length()+1));
      } else if (line.startsWith("\\input_vocab")) { // stop reading
        break;
      }
    }
    int srcVocabSize=vocabSize-tgtVocabSize;
    
    // load tgtWords first
    tgtVocabMap = new HashMap<IString, Integer>();
    tgtReverseVocabMap = new HashMap<Integer, IString>();
    for (int i = 0; i < tgtVocabSize; i++) {
      line = br.readLine();
      tgtVocabMap.put(new IString(line), i);
      tgtReverseVocabMap.put(i, new IString(line));
      
      if(i==(tgtVocabSize-1)) System.err.println("  last tgt word=" + tgtReverseVocabMap.get(i) 
          + ", id=" + tgtVocabMap.get(new IString(line)));
    }
    
    // load srcWords
    srcVocabMap = new HashMap<IString, Integer>();
    srcReverseVocabMap = new HashMap<Integer, IString>();
    for (int i = 0; i < srcVocabSize; i++) {
      line = br.readLine();
      srcVocabMap.put(new IString(line), i+tgtVocabSize);
      srcReverseVocabMap.put(i+tgtVocabSize, new IString(line));
      if(i==0) System.err.println("  first src word=" + srcReverseVocabMap.get(i+tgtVocabSize) 
          + ", id=" + srcVocabMap.get(new IString(line)));
    }
    br.readLine(); // empty line
    
    line = br.readLine(); // should be equal to "\output_vocab"
    if (!line.startsWith("\\output_vocab")) {
      System.err.println("! Expect \\output_vocab in NPLM model");
      System.exit(1);
    }
    br.close();
    
    this.srcOrder = srcOrder;
    this.tgtOrder = this.lmOrder - this.srcOrder;
    this.srcWindow = (srcOrder-1)/2;
    System.err.println("  srcOrder=" + this.srcOrder + ", tgtOrder=" + this.tgtOrder + 
        ", srcVocabSize=" + srcVocabSize + ", tgtVocabSize=" + tgtVocabSize + 
        ", srcUnk=" + srcVocabSize + ", tgtUnk=" + tgtVocabSize);
  }
  
  /**
   * @param f 
   * @param features 
	 * @param isRuleFeaturize -- true if we score rule in isolation, i.e. no access to the source sentence
	 */
  private double getScore(int startPos, int limit, Sequence<IString> translation, 
      Featurizable<IString, String> f, List<FeatureValue<String>> features, boolean isRuleFeaturize) {
    double lmSumScore = 0;
    LMState state = null;
    
    assert(f!=null);
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    
//    int tgtLength = f.targetPhrase.size();
    if(DEBUG){// && tgtLength>2) {
      System.err.println("# NPLMFeaturizer getScore, isRuleFeaturize=" + isRuleFeaturize
          + ", startPos=" + startPos + ", srcPos=" + f.sourcePosition + ", limit=" + limit + ", f=" + f);
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
        for(i=0; i<srcOrder; i++) ngramIds[lmOrder-i-1] = srcVocabMap.get(UNK);
      } else {
        // convert this local srcAvgPos within the current srcPhrase, to the global position within the source sent
        if(!isRuleFeaturize) srcAvgPos += f.sourcePosition;
        
        // extract src subsequence
        Sequence<IString> sourceSeq = (isRuleFeaturize) ? f.rule.abstractRule.source : sourceSent;
        int srcLen = sourceSeq.size();
        int srcSeqStart = srcAvgPos-srcWindow;
        int srcSeqEnd = srcAvgPos+srcWindow;
        
        i=0;
        for (int srcPos = srcSeqStart; srcPos <= srcSeqEnd; srcPos++) {
          IString srcTok = UNK;
          if(srcPos>=0 && srcPos<srcLen) { // within range
            srcTok = sourceSeq.get(srcPos);
            if(!srcVocabMap.containsKey(srcTok)) srcTok = UNK;
          }
          ngramIds[lmOrder-i-1] = srcVocabMap.get(srcTok);
          i++;
        }
      }
      assert(i==srcOrder);
      
      // extract tgt subsequence
      int tgtSeqStart = pos - tgtOrder + 1;
      for (int tgtPos = tgtSeqStart; tgtPos <= pos; tgtPos++) {        
        IString tgtTok = START;
        if(tgtPos>=0) { 
          tgtTok = translation.get(tgtPos);
          if(!tgtVocabMap.containsKey(tgtTok)) tgtTok = UNK;
        }
        ngramIds[lmOrder-i-1] = tgtVocabMap.get(tgtTok);
        i++;
      }
      assert(i==lmOrder);
      // TODO(spenceg): Don't expose part of the underlying LM. Instead, implement NPLMLanguageModel or something.
//      state = lm.score(ngramIds);
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
          System.err.print(" " + srcReverseVocabMap.get(ngramIds[j]).toString());
        }
        System.err.println();
        
        System.err.print("  tgt words=");
        for (int j = tgtOrder-1; j >= 0; j--) {
          System.err.print(" " + tgtReverseVocabMap.get(ngramIds[j]).toString());
        }
        System.err.println("  score=" + ngramScore);
      }
    }
    // The featurizer state is the result of the last n-gram query
    if (!isRuleFeaturize) {
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
    double lmScore = getScore(startPos, limit, partialTranslation, f, features, false);
    features.add(new FeatureValue<String>(featureName, lmScore));
    
    return features;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    assert (f.targetPhrase != null);
    double lmScore = getScore(0, f.targetPhrase.size(), f.targetPhrase, f, null, true); // Thang Mar14: pass f instead of null
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, lmScore));
    return features;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
    this.sourceSent = foreign;
//    System.err.println("\n## NPLMFeaturizer initialize: " + this.sourceSent);
//    for (ConcreteRule<IString,String> option : options) {
//      System.err.println(option);
//    }
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return true;
  }
}
