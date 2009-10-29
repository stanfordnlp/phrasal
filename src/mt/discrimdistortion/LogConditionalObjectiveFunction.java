package mt.discrimdistortion;

import java.util.Arrays;
import java.util.Map;

import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;

public class LogConditionalObjectiveFunction extends AbstractCachingDiffFunction {

	private final LogPrior prior;
	private final TrainingSet trainingSet;
	private final int numClasses;
	private final int numFeatures;
	private final Map<DistortionModel.Feature,Integer> featureOffsets;

	protected double[] derivativeNumerator = null;
	private final double[] sums;
	private final double[] probs;

	public LogConditionalObjectiveFunction(TrainingSet ts) {
		trainingSet = ts;
		prior = new LogPrior(LogPrior.LogPriorType.QUADRATIC.ordinal(), 1.0, 0.0);

		numClasses = ts.getNumClasses();
		numFeatures = ts.getNumFeatures();
		
		featureOffsets = ts.getFeatureOffsets();
		
		sums = new double[numClasses];
		probs = new double[numClasses];
	}

	//Replace to locate the appropriate weight for the feature and class
	//So each class has a weight for each feature
	protected int indexOf(DistortionModel.Feature feat, float value, int c) {
		int offset = featureOffsets.get(feat);
		if(offset == -1)
			throw new RuntimeException("Bad feature value\n");
		
		DistortionModel.FeatureType type = trainingSet.getFeatureType(feat);
		
		int featIndex = (type == DistortionModel.FeatureType.Binary) ? offset + (int) value : offset;
			
		return featIndex * numClasses + c;
	}
	
	@Override
	public int domainDimension() { return numFeatures * numClasses; }
	
	/**
	 * Calculate the conditional likelihood of this data by multiplying
	 * conditional estimates.
	 *
	 * @param x
	 */
	@Override
	public void calculate(double[] x) {
		
		value = 0.0;

		//Cache the "counts" term
		if (derivativeNumerator == null) {
			derivativeNumerator = new double[x.length];
			
			for (Datum datum : trainingSet) {
				DistortionModel.Class c = DistortionModel.discretizeDistortion((int)datum.getTarget());
				
				for (int i = 0; i < datum.numFeatures(); i++) {
					DistortionModel.Feature feat = trainingSet.featureIndex.get(i);
					DistortionModel.FeatureType type = trainingSet.getFeatureType(feat);
					
					int j = indexOf(feat, datum.get(i), c.ordinal());
					derivativeNumerator[j] -= (type == DistortionModel.FeatureType.Binary) ? 1 : datum.get(i);
				}
			}
		}
		
		System.arraycopy(derivativeNumerator, 0, derivative, 0, derivativeNumerator.length);
		
		for(Datum datum : trainingSet) {

			Arrays.fill(sums, 0.0);

			double total = 0;
			for (int c = 0; c < numClasses; c++) {
				for (int i = 0; i < datum.numFeatures(); i++) {
					DistortionModel.Feature feat = trainingSet.featureIndex.get(i);
					DistortionModel.FeatureType type = trainingSet.getFeatureType(feat);
					
					int j = indexOf(feat, datum.get(i), c);
					sums[c] += (type == DistortionModel.FeatureType.Binary) ? x[j] : x[j] * datum.get(i);
				}
			}

			total = ArrayMath.logSum(sums);
			
			for (int c = 0; c < numClasses; c++) {
				probs[c] = Math.exp(sums[c] - total);
				for (int i = 0; i < datum.numFeatures(); i++) {
					DistortionModel.Feature feat = trainingSet.featureIndex.get(i);
					DistortionModel.FeatureType type = trainingSet.getFeatureType(feat);
					
					int j = indexOf(feat, datum.get(i), c);
					derivative[j] += (type == DistortionModel.FeatureType.Binary) ? probs[c] : probs[c] * datum.get(i);
				}
			}

			DistortionModel.Class c = DistortionModel.discretizeDistortion((int)datum.getTarget());
			double dV = sums[c.ordinal()] - total;
			value -= dV;
		}
		value += prior.compute(x, derivative);
	}

}
