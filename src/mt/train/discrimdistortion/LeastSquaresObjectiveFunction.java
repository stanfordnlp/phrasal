package mt.train.discrimdistortion;

import java.util.List;

import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.util.Pair;

//WSGDEBUG - This needs to be fixed according to the new training set to work
public class LeastSquaresObjectiveFunction implements DiffFunction {

//	private final TrainingSet trainingSet;
//	private final boolean useIntercept;
//	private final int wtDimension;
//	private final List<Pair<DistortionModel.Feature,Integer>> featList;
	
	public LeastSquaresObjectiveFunction(TrainingSet ts, boolean useIntercept) {
//		trainingSet = ts;
//		this.useIntercept = useIntercept;
//		wtDimension = ts.getNumFeatures() + ((useIntercept) ? 1 : 0);
//		featList = ts.getFeatureOffsets();
	}
	
//	private double hypothesis(Datum d, double[] weights) {
//		double result = 0.0;
//		int wtBaseline = 0;
//		if(useIntercept)
//			result += 1.0 * weights[wtBaseline++];
//		
//		int datumPtr = 0;
//		for(Pair<DistortionModel.Feature,Integer> feat : featList) {
//			float datumVal = d.get(datumPtr++);
//			if(feat.first() == DistortionModel.Feature.Word || feat.first() == DistortionModel.Feature.CurrentTag)
//				result += 1.0 * weights[wtBaseline + (int) datumVal];
//			else if(feat.first() == DistortionModel.Feature.RelPosition)
//				result += datumVal * weights[wtBaseline];
//			wtBaseline += feat.second();
// 		}
//		
//		return result;
//	}
	
	@Override
	public double[] derivativeAt(double[] x) {
//		if(x.length != wtDimension)
//			throw new RuntimeException(String.format("%s: Parameter set (d=%d) and training set (d=%d) have different dimensions!\n",this.getClass().getName(),x.length,wtDimension));
//		
//		double[] next_x = new double[x.length];
////		if(useIntercept) next_x[0] = 1.0 * x[0];
//		
//		//Calculate the gradient
//		for(Datum datum : trainingSet) {
//			double hypothesis = hypothesis(datum,x) - datum.getTarget();
//			int dPtr = 0;
//			int lastThreshold = 0;
//			for(Pair<DistortionModel.Feature,Integer> feat : featList) {
//				//Set value
//				double value = (feat.first() == DistortionModel.Feature.RelPosition) ? datum.get(dPtr) : 1.0;
//				
//				//Set index
//				int thetaIdx = 0;
//				if(feat.first() == DistortionModel.Feature.RelPosition)
//					thetaIdx = lastThreshold;
//				else
//					thetaIdx = lastThreshold + (int) datum.get(dPtr);
//				
//				next_x[thetaIdx] += hypothesis * value;
//				
//				lastThreshold += feat.second();
//				dPtr++;
//			}
//		}
//		
//		return next_x;
		return null;
	}

	@Override
	public int domainDimension() { return 0; }

	@Override
	public double valueAt(double[] x) {
//		if(x.length != wtDimension)
//			throw new RuntimeException(String.format("%s: Parameter set (d=%d) and training set (d=%d) have different dimensions!\n",this.getClass().getName(),x.length,wtDimension));
//		
//		double result = 0.0;
//		for(Datum datum : trainingSet)
//			result += Math.pow(hypothesis(datum,x) - datum.getTarget(), 2);
//
//		result *= 0.5;
//		
//		return result;
		return 0.0;
	}

}
