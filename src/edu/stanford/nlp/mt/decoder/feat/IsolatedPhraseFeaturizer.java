package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public interface IsolatedPhraseFeaturizer<TK, FV> {
  /**
	 * 
	 */
  List<FeatureValue<FV>> phraseListFeaturize(Featurizable<TK, FV> f);

  /**
	 * 
	 */
  FeatureValue<FV> phraseFeaturize(Featurizable<TK, FV> f);
}
