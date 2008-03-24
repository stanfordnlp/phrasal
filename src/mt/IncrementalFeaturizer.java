package mt;

import java.util.*;

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
	 * @return
	 */
	List<FeatureValue<FV>> listFeaturize(Featurizable<TK,FV> f);
	
	/**
	 * 
	 * @param f
	 * @return
	 */
	FeatureValue<FV> featurize(Featurizable<TK,FV> f); 
}
