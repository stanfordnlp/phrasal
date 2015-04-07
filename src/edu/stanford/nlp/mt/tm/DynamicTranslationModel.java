package edu.stanford.nlp.mt.tm;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TranslationModelIndex;

/**
 * 
 * See here for lex re-ordering: https://github.com/moses-smt/mosesdecoder/commit/51824355f9f469c186d5376218e3396b92652617
 * 
 * @author Spence Green
 *
 */
public class DynamicTranslationModel<TK,FV> implements TranslationModel<TK,FV>,Serializable {

  /**
   * TODO(spenceg) Implement kryo
   */
  private static final long serialVersionUID = 5876435802959430120L;
  
  public ParallelSuffixArray sa;
  public TranslationModelIndex idx;
  
  public DynamicTranslationModel(ParallelSuffixArray suffixArray, TranslationModelIndex idx) {
    this.sa = suffixArray;
    this.idx = idx;
  }

  @Override
  public List<ConcreteRule<TK, FV>> getRules(Sequence<TK> source,
      InputProperties sourceInputProperties, List<Sequence<TK>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public RuleGrid<TK, FV> getRuleGrid(Sequence<TK> source,
      InputProperties sourceInputProperties, List<Sequence<TK>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int longestSourcePhrase() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int longestTargetPhrase() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public List<String> getFeatureNames() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return null;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
  
  // TODO(spenceg) query functions for dense features and lex re-ordering
  // Queries should prune higher-order n-grams
  
  // TODO(spenceg) Setup caches
  // Top-k rules
  // LRU cache
}
