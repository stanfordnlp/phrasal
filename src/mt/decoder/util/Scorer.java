package mt.decoder.util;

import java.util.*;

import mt.base.FeatureValue;

/**
 * @author danielcer
 *
 * @param <T>
 */
public interface Scorer<FV> {
	/**
	 * @param features
	 */
	double getIncrementalScore(List<FeatureValue<FV>> features);
}
