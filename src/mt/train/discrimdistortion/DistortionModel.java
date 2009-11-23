package mt.train.discrimdistortion;

import java.io.Serializable;
import java.util.Map;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

public class DistortionModel implements Serializable {
	private static final long serialVersionUID = 8119388926319744131L;

	public static enum Feature { Word, RelPosition, CurrentTag, SourceLen, LeftTag, RightTag }
	public static enum FeatureType { Binary, Real };
	public static enum Class { C1, C2, C3, C4, C5, C6, C7, C8, C9, NULL }

	public Index<DistortionModel.Feature> featureIndex = null;
	public Index<DistortionModel.Class> classIndex = null;
	public Index<String> wordIndex = null;
	public Index<String> tagIndex = null;
	public double[] weights = null;
	public Map<DistortionModel.Feature,Integer> featureOffsets = null;
	public Map<DistortionModel.Feature,Integer> featureDimensions = null;
	public Map<DistortionModel.Feature,DistortionModel.FeatureType> featureTypes = null;

	//WSGDEBUG - Should make this configurable, eventually
	//NOTE the stylistic justification: you probably wouldn't use the same word twice
	//within a 20% span of a sentence (even for sentences with 100 words)
	public static final int NUM_SLEN_BINS = 4;
	public static final int NUM_SLOC_BINS = 5;
	
	/**
	 * Returns the logprob from the model for this datum and class
	 * @param datum
	 * @param thisC
	 * @param isOOV
	 * @return
	 */
	public double prob(Datum datum, DistortionModel.Class thisC, boolean isOOV) {
		double[] logScores = new double[DistortionModel.Class.values().length];
		for(DistortionModel.Class c : DistortionModel.Class.values())
			logScores[c.ordinal()] = modelScore(datum, c, isOOV);
		
		double denom = ArrayMath.logSum(logScores);
		
		double scoreFromModel = modelScore(datum, thisC, isOOV);

		return scoreFromModel - denom; //Division in real space
	}
	
	public Pair<Double,DistortionModel.Class> argmax(Datum datum, boolean isOOV) {
		DistortionModel.Class maxClass = null;
		double maxScore = -1.0;
		for(DistortionModel.Class c : DistortionModel.Class.values()) {
			double modelScore = modelScore(datum, c, isOOV);
			if(modelScore > maxScore) {
				maxScore = modelScore;
				maxClass = c;
			}
		}
			
		return new Pair<Double,DistortionModel.Class>(maxScore, maxClass);
	}
	
	public double modelScore(Datum datum, DistortionModel.Class c, boolean isOOV) {
	  if(featureIndex.contains(Feature.Word)) {
	    return (isOOV) ? Math.exp(modelScore(datum, c, 1)) : Math.exp(modelScore(datum, c, 0));
	  }  else {
	    return Math.exp(modelScore(datum, c, 0));
	  }
	}
	
	private double modelScore(Datum datum, DistortionModel.Class c, int startPos) {
		double score = 0.0;
		for (int i = startPos; i < datum.numFeatures(); i++) {
			DistortionModel.Feature feat = featureIndex.get(i);
			DistortionModel.FeatureType type = featureTypes.get(feat);
			
			int j = indexOf(feat, datum.get(i), c.ordinal());
				
			score += (type == DistortionModel.FeatureType.Binary) ? weights[j] : weights[j] * datum.get(i);
		}
		
		return score;
	}	
	
	public int indexOf(DistortionModel.Feature feat, float value, int c) {
		Integer offset = featureOffsets.get(feat);
		if(offset == null)
			throw new RuntimeException("Bad feature value\n");
		
		DistortionModel.FeatureType type = featureTypes.get(feat);
		
		int featIndex = (type == DistortionModel.FeatureType.Binary) ? offset + (int) value : offset;
			
		return featIndex * classIndex.size() + c;
	}
	
	
	public int getFeatureDimension() { return (featureIndex == null) ? 0 : featureIndex.size(); }
	
	
	public static final float[] classRightBounds = {-50.0f,-22.09f,-11.84f,-6.69f,-3.38f,
	  -1.0f, 0.64f, 3.45f, 8.68f, 32.0f, 65.0f, 100.0f };
	public static final Class FIRST_CLASS = Class.C1;
	public static final Class LAST_CLASS = Class.C9;
	public static final Class MONOTONE = Class.C5;
	public static final int NULL_VALUE = -5000;
	
	//Expects relative movement as a percentage (e.g., 100%)
	public static Class discretizeDistortion(int relMovement) {
	
	  if(relMovement == NULL_VALUE)
	    return Class.NULL;
	  
		//10 class implementation (28 Oct 2009)
	  if(relMovement <= -7)    //By construction
	    return Class.C1;
	  else if(relMovement <= -4)
	    return Class.C2;
	  else if(relMovement <= -2)
	    return Class.C3;
	  else if(relMovement == -1)
	    return Class.C4;
	  else if(relMovement == 0)
	    return Class.C5;
	  else if(relMovement == 1)
	    return Class.C6;
	  else if(relMovement <= 3)
	    return Class.C7;
	  else if(relMovement <= 6)
	    return Class.C8;
	  
	  return Class.C9;
		
//    if (relMovement == 0.0)
//      return Class.Zero;
//    
////    10 class implementation (28 Oct 2009)
//    if(relMovement < -19.82f)    //By construction
//      return Class.C1;
//    if(relMovement < -11.80)
//      return Class.C2;
//    else if(relMovement < -7.31f)
//      return Class.C3;
//    else if(relMovement < -4.27f)
//      return Class.C4;
//    else if(relMovement < -1.94f)
//      return Class.C5;
//    else if(relMovement < 0.0f)
//      return Class.C6;
//    else if(relMovement < 1.94f)
//      return Class.C7;
//    else if(relMovement < 4.27f)
//      return Class.C8;
//    else if(relMovement < 7.31f)
//      return Class.C9;
//    else if(relMovement < 11.81f)
//      return Class.C10;
//    else if(relMovement < 19.82f)  //By construction
//      return Class.C11;
//    return Class.C12;
//		
				
       //9 class (this works well except for right movement
//		if(relMovement < -50.0f) //By construction
//			return Class.C1;
//		else if(relMovement < -21.1f)
//			return Class.C2;
//		else if(relMovement < -11.0f)
//			return Class.C3;
//		else if(relMovement < -5.98f)
//			return Class.C4;
//		else if(relMovement < -2.70f)
//			return Class.C5;
//		else if(relMovement < -0.22f)
//			return Class.C6;
//		else if(relMovement < 1.57f)
//			return Class.C7;
//		else if(relMovement < 5.29f)
//			return Class.C8;
//		else if(relMovement < 14.9f)
//			return Class.C9;
//		else if(relMovement < 50.0f) //By construction
//			return Class.C10;
//		return Class.C11;
		
		
		//17 class justification:
		// 1) 15 bins using the bin selection procedure
		// 2) 2 construction bins to collect crap. Bounds set empirically
//		if(relMovement < -50.0f) //By construction
//			return Class.C1;
//		else if(relMovement < -29.04f)
//			return Class.C2;
//		else if(relMovement < -18.26f)
//			return Class.C3;
//		else if(relMovement < -12.42f)
//			return Class.C4;
//		else if(relMovement < -8.64f)
//			return Class.C5;
//		else if(relMovement < -5.95f)
//			return Class.C6;
//		else if(relMovement < -3.86f)
//			return Class.C7;
//		else if(relMovement < -2.13f)
//			return Class.C8;
//		else if(relMovement < -0.61f)
//			return Class.C9;
//		else if(relMovement < 0.19f)
//			return Class.C10;
//		else if(relMovement < 1.60f)
//			return Class.C11;
//		else if(relMovement < 3.57f)
//			return Class.C12;
//		else if(relMovement < 6.45f)
//			return Class.C13;
//		else if(relMovement < 11.86f)
//			return Class.C14;
//		else if(relMovement < 29.30f)
//			return Class.C15;
//		else if(relMovement < 65.0f) //By construction
//			return Class.C16;
//		return Class.C17;

	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Distortion Model:\n");
		if(featureIndex != null)
			sb.append(String.format(" Features: %d\n",featureIndex.size()));
		if(classIndex != null) {
			sb.append(String.format(" Classes : %d\n", classIndex.size()));
			sb.append(String.format(" > %s\n",classIndex.toString()));
		}
		if(tagIndex != null) {
			sb.append(String.format(" Tags    : %d\n", tagIndex.size()));
			sb.append(String.format(" > %s\n",tagIndex.toString()));
		}
		if(wordIndex != null)
			sb.append(String.format(" Words   : %d\n", wordIndex.size()));
		
		return sb.toString();
	}
	
	public static int getSlenBin(final int slen) {
		if(slen <= 21)
			return 0;
		else if(slen <= 30)
			return 1;
		else if(slen <= 39)
			return 2;
		return 3;
	}

	public static int getSlocBin(float normalizedLoc) {
		if(normalizedLoc < 0.2f)
			return 0;
		else if(normalizedLoc < 0.4f)
			return 1;
		else if(normalizedLoc < 0.6f)
			return 2;
		else if(normalizedLoc < 0.8f)
			return 3;
		else
			return 4;
	}

}
