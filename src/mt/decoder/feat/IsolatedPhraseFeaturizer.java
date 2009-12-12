package mt.decoder.feat;

import java.util.List;

import mt.base.FeatureValue;
import mt.base.Featurizable;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public interface IsolatedPhraseFeaturizer<TK,FV> {
	/**
	 * 
	 * @param f
	 */
	List<FeatureValue<FV>> phraseListFeaturize(Featurizable<TK,FV> f);
	
	/**
	 * 
	 * @param f
	 */
	FeatureValue<FV> phraseFeaturize(Featurizable<TK,FV> f); 
}
