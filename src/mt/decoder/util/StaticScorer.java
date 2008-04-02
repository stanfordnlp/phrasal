package mt.decoder.util;
import java.io.*;
import java.util.*;

import mt.base.FeatureValue;

import edu.stanford.nlp.stats.ClassicCounter;
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
		
		Properties properties = new Properties();
		
		FileReader reader = new FileReader(filename);
		properties.load(reader);
		reader.close();
		
		featureIndex = new OAIndex<String>();
		
		for (Object propObj : properties.keySet()) {
			String prop = (String)propObj;
			featureIndex.indexOf(prop, true);
		}
		
		weights = new double[featureIndex.boundOnMaxIndex()];
		
		for (Object propObj : properties.keySet()) {
			String prop = (String)propObj;
			weights[featureIndex.indexOf(prop)] = Double.parseDouble(properties.getProperty(prop));
		}
				
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
	
	public StaticScorer(ClassicCounter<String> featureWts) {
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

	@Override
	public void saveWeights(String filename) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasNonZeroWeight(String featureName) {
		int idx = featureIndex.indexOf(featureName);
		if (idx < 0) {
			return false;
		}
		return weights[idx] == weights[idx] && weights[idx] != 0;
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
