package mt.decoder.feat;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public interface IncrementalFeaturizer<TK,FV> {
	void initialize(List<ConcreteTranslationOption<TK>> options, Sequence<TK> foreign);
	
	void reset();
	
	/**
	 * 
	 * @param f
	 */
	List<FeatureValue<FV>> listFeaturize(Featurizable<TK,FV> f);
	
	/**
	 * 
	 * @param f
	 */
	FeatureValue<FV> featurize(Featurizable<TK,FV> f); 
}
