package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.lm.NNLMState;
import edu.stanford.nlp.mt.lm.TargetNNLM;
import edu.stanford.nlp.util.Generics;

/**
 * Featurizer for TargetNNLM, but neural score computation is delayed until later stages.
 * Based on the NGramLanguageModelFeaturizer code.
 * 
 * @author Thang Luong
 */
public class TargetNNLMBatchFeaturizer extends NNLMFeaturizer {
  public static final String DEFAULT_FEATURE_NAME = "TargetNNLMBatch";

  /**
   * Constructor called by Phrasal when JointNNLMBatchFeaturizer appears in
   * [additional-featurizers].
   */
  public TargetNNLMBatchFeaturizer(String...args) throws IOException {
    super(args); 
    
    // load TargetNNLM
    nnlm = new TargetNNLM(nnlmFile, cacheSize, 1); // miniBatchSize=1
    this.tgtOrder = nnlm.getTgtOrder(); // to store state
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    Sequence<IString> tgtSent = null;
    List<FeatureValue<String>> features = Generics.newLinkedList();

    // f.targetPrefix includes priorState + targetPhrase
    // f.targetPosition: position in targetPrefix where the targetPhrase starts.
    if (f.done) { tgtSent = Sequences.wrapStartEnd(f.targetPrefix, startToken, endToken); } 
    else { tgtSent = Sequences.wrapStart(f.targetPrefix, startToken); }
    
    int srcStartPos = f.sourcePosition;
    int tgtStartPos = f.targetPosition + 1;

    NNLMState state;
    double score = 0.0; // we delay the score computation to later stage
    int tgtLen = tgtSent.size(); 
    if(tgtLen>tgtStartPos){
      // extract the last ngram
      int[] ngramIds = nnlm.extractNgram(tgtLen, srcSent, tgtSent, f.rule.abstractRule.alignment, srcStartPos, tgtStartPos);
      state = new NNLMState(0.0, ngramIds, tgtOrder);
    } else { // target deletion
      state = (NNLMState) f.prior.getState(this);
    }
    
    f.setState(this, state);
    features.add(new FeatureValue<String>(featureName, score));

    return features;
  }
}







