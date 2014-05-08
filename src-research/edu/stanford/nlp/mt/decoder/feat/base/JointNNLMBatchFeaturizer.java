package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.IOException;

import edu.stanford.nlp.mt.lm.JointNNLM;

/**
 * Featurizer for JointNNLM, but neural score computation is delayed until later stages.
 * Based on the NGramLanguageModelFeaturizer code.
 * 
 * @author Thang Luong
 */
public class JointNNLMBatchFeaturizer extends TargetNNLMBatchFeaturizer {
  public static final String DEFAULT_FEATURE_NAME = "JointNNLMBatch";

  /**
   * Constructor called by Phrasal when JointNNLMBatchFeaturizer appears in
   * [additional-featurizers].
   */
  public JointNNLMBatchFeaturizer(String...args) throws IOException {
    super(args); 
    
    // load JointNNLM
    nnlm = new JointNNLM(nnlmFile, cacheSize, 1); // miniBatchSize=1
    this.tgtOrder = nnlm.getTgtOrder(); // to store state
  }
}







