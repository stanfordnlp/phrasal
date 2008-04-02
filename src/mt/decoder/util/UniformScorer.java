package mt.decoder.util;

import java.io.IOException;
import java.util.List;

import mt.base.FeatureValue;

import static java.lang.System.*;

/**
 * 
 * @author Daniel Cer
 *
 * @param <T>
 */
public class UniformScorer<T> implements Scorer<T> {
	
	public UniformScorer() {
		err.println("--------------------------------------------------------");
		err.println("Warning: Creating instance of UniformScorer.");
		err.println();
		err.println("This class primarily exists for diagnostic purposes");
		err.println("and to allow for generative translation models.");
		err.println();
		err.println("Otherwise, you'll probably want to use something like");
		err.println("StaticScorer.");
		err.println("--------------------------------------------------------");
	}

	public double getIncrementalScore(List<FeatureValue<T>> features) {
		double score = 0;
		
		for (FeatureValue<T> feature : features) {
			if (feature == null) continue;
			score += feature.value;			
		}
		
		return score;
		
	}

	@Override
	public void saveWeights(String filename) throws IOException {
		throw new UnsupportedOperationException();		
	}

	@Override
	public boolean hasNonZeroWeight(String featureName) {
		return true;
	}

	@Override
	public boolean randomizeTag() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setRandomizeTag(boolean randomizeTag) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setWeightMultipliers(double manualWeightMul,
			double classifierWeightMul) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayWeights() {
		// TODO Auto-generated method stub
		
	}


}
