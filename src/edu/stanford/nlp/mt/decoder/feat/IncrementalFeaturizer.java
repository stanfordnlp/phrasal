package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public interface IncrementalFeaturizer<TK, FV> {
  void initialize(List<ConcreteTranslationOption<TK>> options,
      Sequence<TK> foreign);

  void reset();

  /**
	 * 
	 */
  List<FeatureValue<FV>> listFeaturize(Featurizable<TK, FV> f);

  /**
	 * 
	 */
  FeatureValue<FV> featurize(Featurizable<TK, FV> f);
}
