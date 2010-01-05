package mt.decoder.util;

import java.util.*;

import mt.base.FeatureValue;

/**
 * @author danielcer
 */
public interface Scorer<FV> {
	/**
	 */
	double getIncrementalScore(List<FeatureValue<FV>> features);
}
