package mt;

import java.util.*;
import java.io.*;

/**
 * @author danielcer
 *
 * @param <T>
 */
public interface Scorer<FV> {
	/**
	 * @param features
	 * @return
	 */
	double getIncrementalScore(List<FeatureValue<FV>> features);
	
	/**
	 * 
	 * @param filename
	 */
	void saveWeights(String filename) throws IOException;
	
	/**
	 * 
	 * @param featureName
	 * @return
	 */
	boolean hasNonZeroWeight(String featureName);

	public void setWeightMultipliers(double manualWeightMul, double classifierWeightMul);
	
	boolean randomizeTag();
	void setRandomizeTag(boolean randomizeTag);
	void displayWeights();
}
