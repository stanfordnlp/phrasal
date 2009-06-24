package mt.decoder.util;
import java.io.*;
import java.util.*;

import mt.base.FeatureValue;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.OAIndex;

/**
 * @author danielcer
 *
 */
public class StaticScorer implements Scorer<String> {
	final OAIndex<String> featureIndex;
	final double[] weights;
	     	
	/**
	 * @param filename
	 * @throws IOException
	 */
	public StaticScorer(String filename) throws IOException {
		
		featureIndex = new OAIndex<String>();
		
		
		
		Map<Integer,Double> wts = new HashMap<Integer,Double>();
		
				
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		for (String line; (line = reader.readLine()) != null;) {
			String[] fields = line.split("\\s+");
			wts.put(featureIndex.indexOf(fields[0], true), Double.valueOf(fields[1]));
		}
		
		weights = new double[featureIndex.boundOnMaxIndex()];
		for (Map.Entry<Integer,Double> e: wts.entrySet()) {
			weights[e.getKey()] = e.getValue();
		}
		reader.close();
	}
	
	/**
	 * 
	 * @param featureWts
	 */
	public StaticScorer(Map<String,Double> featureWts) {
		featureIndex = new OAIndex<String>();
		for (String key : featureWts.keySet()) {
			featureIndex.indexOf(key, true);
		//	System.err.printf("---inserting: '%s' index: %d\n", key, featureIndex.indexOf(key));
		}
		
		weights = new double[featureIndex.boundOnMaxIndex()];
		
		for (String key : featureWts.keySet()) {
			weights[featureIndex.indexOf(key)] = featureWts.get(key).doubleValue();
		}
	}
	
	public StaticScorer(Counter<String> featureWts) {
		featureIndex = new OAIndex<String>();
		for (String key : featureWts.keySet()) {
			featureIndex.indexOf(key, true);
			//System.err.printf("---inserting: '%s' index: %d\n", key, featureIndex.indexOf(key));
		}
		
		weights = new double[featureIndex.boundOnMaxIndex()];
		
		for (String key : featureWts.keySet()) {
			weights[featureIndex.indexOf(key)] = featureWts.getCount(key);
		}
	}
	
	@Override
	public double getIncrementalScore(List<FeatureValue<String>> features) {
		double score = 0;
		
		for (FeatureValue<String> feature : features) {
			int index = featureIndex.indexOf(feature.name);
			if (index >= 0) score += weights[index]*feature.value;			
		}
		
		return score;
	}
	
	@SuppressWarnings("unchecked")
	public double getIncrementalScoreNoisy(List features) {
		double score = 0;
		
		for (Object o : features) {
			FeatureValue<String> feature = (FeatureValue<String>)o;
			int index = featureIndex.indexOf(feature.name);
			System.out.printf("feature: %s index: %d\n", feature.name, index);
			if (index >= 0) {
				score += weights[index]*feature.value;
				System.out.printf("\tvalue: %f contrib: %f\n", feature.value, weights[index]*feature.value);
			}
		}
		
		return score;
	}


	public void saveWeights(String filename) {
		throw new UnsupportedOperationException();
	}


	public boolean hasNonZeroWeight(String featureName) {
		int idx = featureIndex.indexOf(featureName);
		if (idx < 0) {
			return false;
		}
		return weights[idx] == weights[idx] && weights[idx] != 0;
	}


	public boolean randomizeTag() {
		// TODO Auto-generated method stub
		return false;
	}


	public void setRandomizeTag(boolean randomizeTag) {
		// TODO Auto-generated method stub
		
	}


	public void setWeightMultipliers(double manualWeightMul,
			double classifierWeightMul) {
		// TODO Auto-generated method stub
		
	}

	public void displayWeights() {
		// TODO Auto-generated method stub
		
	}

}
