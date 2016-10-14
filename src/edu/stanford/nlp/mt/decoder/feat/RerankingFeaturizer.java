package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Extract features for entries in an n-best list. 
 * initialize() is called once on a new sentence. 
 * 
 * @author Joern Wuebker
 * 
 * @param <TK>
 * @param <FV>
 */
public interface RerankingFeaturizer<TK, FV> extends Featurizer<TK,FV> {

  /**
   * This call is made *before* decoding a new input begins.
   * 
   * @param sourceInputId
   * @param source
   * @param targetPrefix
   */
  public abstract void initialize(int sourceInputId,
      Sequence<TK> source, Sequence<TK> targetPrefix);


  /**
   * Extract and return a list of features for each featurizable (in the same order). If features overlap in the list, 
   * their values will be added.
   * 
   * @return a list of features or null.
   */
  public abstract List<List<FeatureValue<FV>>> rerankingFeaturize(List<Featurizable<TK, FV>> featurizables);
 
}
