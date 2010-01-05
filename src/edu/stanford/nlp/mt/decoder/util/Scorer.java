package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.base.FeatureValue;

/**
 * @author danielcer
 */
public interface Scorer<FV> {
	/**
	 */
	double getIncrementalScore(List<FeatureValue<FV>> features);
}
