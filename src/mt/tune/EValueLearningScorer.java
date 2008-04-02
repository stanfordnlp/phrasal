package mt.tune;

import java.io.*;
import java.util.*;

import mt.base.FeatureValue;
import mt.decoder.util.SSVMScorer;
import mt.decoder.util.Scorer;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.OAIndex;

public class EValueLearningScorer implements Scorer<String> {
	public static final String GENERATIVE_FEATURES_LIST_RESOURCE = "mt/resources/generative.features";
	public static final Set<String> generativeFeatures = SSVMScorer.readGenerativeFeatureList(GENERATIVE_FEATURES_LIST_RESOURCE);
	
	final double[] manualWeights;
	private double[] wts;
	private double[] deltaWts;
	OAIndex<String> featureIndex = new OAIndex<String>();
	private   double manualWeightMul = 0.0;
	private  double classifierWeightMul = 1.0;
	final boolean DEBUG = false;
	final double alpha = 1e-4;
	
	public void setWeightMultipliers(double manualWeightMul, double classifierWeightMul) {
		this.manualWeightMul = manualWeightMul;
		this.classifierWeightMul = classifierWeightMul;
	}
	
	
	final double lrate;
	final double momentumTerm;
	final double acclTerm;
	
	public EValueLearningScorer(Map<String,Double> manualFeatureWts) {
		this(manualFeatureWts, 0.1, 0.0);
	}
	
	public EValueLearningScorer(Map<String,Double> manualFeatureWts, double lrate, double momentumTerm) {
		this.lrate = lrate;
		this.momentumTerm = momentumTerm;
		acclTerm = 0.5*(1.0-momentumTerm);
		System.err.printf("Learning rate: %e MomentumTerm: %e\n", lrate, momentumTerm);
		System.err.printf("Alpha: %e\n", alpha);
		featureIndex = new OAIndex<String>();
		
		//deltaWts = new double[0];
		for (String key : manualFeatureWts.keySet()) {
			featureIndex.indexOf(key, true);
			System.err.printf("---inserting: '%s' index: %d\n", key, featureIndex.indexOf(key));
		}
	
		manualWeights = new double[featureIndex.boundOnMaxIndex()];
		
		for (String key : manualFeatureWts.keySet()) {
			manualWeights[featureIndex.indexOf(key)] = manualFeatureWts.get(key).doubleValue()/100.0;
		}
		wts = manualWeights.clone(); // new double[manualWeights.length]; 
		deltaWts = new double[wts.length];
	}
	
	
	public double objectiveValue(List<List<FeatureValue<String>>> featureVectors, double[] l) {
		double Z = 0;
		double[] n = new double[l.length];
		
		for (List<FeatureValue<String>> featureVector : featureVectors) {
			Z += Math.exp(getIncrementalScore(featureVector));
		}
		
		double o = 0;
		System.out.printf("Z:%e\n", Z);
		{ int nIdx = -1; 
		for (List<FeatureValue<String>> featureVector : featureVectors) { nIdx++;
			n[nIdx] = Math.exp(getIncrementalScore(featureVector));
			double p = n[nIdx]/Z;
			o += p*l[nIdx];
		}
		}
		return o;
	}
	
	
	static public ClassicCounter<String> dEl(Scorer<String> scorer, List<List<FeatureValue<String>>> featureVectors, double[] l) {
		double Z = Double.MIN_NORMAL;
		double[] n = new double[l.length];
		ClassicCounter<String> dEl = new ClassicCounter<String>();
		for (List<FeatureValue<String>> featureVector : featureVectors) {
			Z += Math.exp(scorer.getIncrementalScore(featureVector));
		}
		
		if (Z == Double.POSITIVE_INFINITY) Z = Double.MAX_VALUE;
		
		ClassicCounter<String> eF = new ClassicCounter<String>();
		{ int nIdx = -1; 
		for (List<FeatureValue<String>> featureVector : featureVectors) { nIdx++;
			n[nIdx] = Math.exp(scorer.getIncrementalScore(featureVector));
			double p = n[nIdx]/Z;
			for (FeatureValue<String> feature : featureVector) {
				eF.incrementCount(feature.name, p*feature.value);
			}
		}
		}
		
		// System.out.printf("Z:%e (vectors: %d)\n", Z, featureVectors.size());
		{ int lIdx = -1;
		for (List<FeatureValue<String>> featureVector : featureVectors) { lIdx++;
			if (l[lIdx] != l[lIdx]) continue;
			double p = n[lIdx]/Z;
			/*System.err.printf("%d:%e (%e/%e)\n", lIdx, p, n[lIdx], Z);
			System.err.printf("%s\n", featureVector); */
			for (FeatureValue<String> feature : summarizedFeatureVector(featureVector)) {
				double delta;
				delta = p*(feature.value - eF.getCount(feature.name))*l[lIdx];
		    	//System.err.printf("%s: delta: %e <= %e*(%e-%e)*%e, args) \n", feature.name, delta, p,  feature.value,  eF.getCount(feature.name),l[lIdx]); 			
				dEl.incrementCount(feature.name, delta);
			}
		}
		}
		
		return dEl;
	}
	
	public void wtUpdate(List<List<FeatureValue<String>>> featureVectors, double[] l, int iter) {
		double Z = 0.0;
		ClassicCounter<String> eF = new ClassicCounter<String>();
		double[] n = new double[l.length];
		
		for (List<FeatureValue<String>> featureVector : featureVectors) {
			Z += Math.exp(getIncrementalScore(featureVector));
		}
		
		System.out.printf("Z:%e (vectors: %d)\n", Z, featureVectors.size());
		{ int nIdx = -1; 
		for (List<FeatureValue<String>> featureVector : featureVectors) { nIdx++;
			n[nIdx] = Math.exp(getIncrementalScore(featureVector));
			double p = n[nIdx]/Z;
			for (FeatureValue<String> feature : featureVector) {
				featureIndex.indexOf(feature.name, true);
				eF.incrementCount(feature.name, p*feature.value);
			}
		}
		}
		
		if (Z == Double.POSITIVE_INFINITY) {
			for (int i = 0; i < n.length; i++) {
				System.err.printf("%d:%e - l: %e\n", i, n[i], l[i]);
				System.err.printf("\t%s\n", featureVectors.get(i));
			}
			System.exit(-1);
		}
		int maxIndex = featureIndex.maxIndex();
		if (wts.length < maxIndex) {
			wts = Arrays.copyOf(wts, maxIndex);
			deltaWts = Arrays.copyOf(deltaWts, maxIndex);
		}
		
		double[] d = new double[wts.length];
		for (int i = 0; i < wts.length; i++) {
			d[i] = -wts[i]*(alpha); // l2 - reg, @ 1e-4 w/1000 dt pts this is like a Gaussian prior with sigma = 10   
		}
		
		{ int lIdx = -1;
		for (List<FeatureValue<String>> featureVector : featureVectors) { lIdx++;
			if (l[lIdx] != l[lIdx]) continue;
			double p = n[lIdx]/Z;
			/*System.err.printf("%d:%e (%e/%e)\n", lIdx, p, n[lIdx], Z);
			System.err.printf("%s\n", featureVector); */
			for (FeatureValue<String> feature : summarizedFeatureVector(featureVector)) {
				int wtIdx = featureIndex.indexOf(feature.name, true);
				double delta;
				delta = p*(feature.value - eF.getCount(feature.name))*l[lIdx];
		    	//System.err.printf("%s: delta: %e <= %e*(%e-%e)*%e, args) \n", feature.name, delta, p,  feature.value,  eF.getCount(feature.name),l[lIdx]); 			
				d[wtIdx] += delta; 
			}
		}
		}
		
		if (DEBUG) {
			System.err.printf("Weight Update Summary\n");
		}
		for (int i = 0; i < wts.length; i++) {
			deltaWts[i] = (momentumTerm+acclTerm)*deltaWts[i] + (1.0-momentumTerm)*d[i];
			if (DEBUG) {
				if (wts[i] != 0) { 
					System.err.printf("w: %e (md %e, d: %e) %s\n", wts[i], deltaWts[i], d[i], featureIndex.get(i));
				}
			}
		}
		
		for (int i = 0; i < wts.length; i++) {
			wts[i] += lrate*deltaWts[i];
		}
		
		for (String gf : generativeFeatures) {
			int wtIdx = featureIndex.indexOf(gf);
			if (wtIdx < 0) continue;
			if (wts[wtIdx] < 0) {
				wts[wtIdx] = 0;
				if (deltaWts[wtIdx] < 0) deltaWts[wtIdx] = 0;
			}
		}
	}
	
	@Override
	public double getIncrementalScore(List<FeatureValue<String>> features) {
		
		double score = 0;
		for (FeatureValue<String> feature : features) {
			int idx = featureIndex.indexOf(feature.name, false);
			if (idx < 0) continue;
			
			if  (idx < manualWeights.length) {
				double wt = manualWeights[idx];
				score += wt*manualWeightMul*feature.value;
			}
			
			if (idx < wts.length) {
				double wt = wts[idx];
				score += wt*classifierWeightMul*feature.value;
			}
		}
		return score;
	}

	@Override
	public boolean hasNonZeroWeight(String featureName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean randomizeTag() {
		// TODO Auto-generated method stub
		return false;
	}

	public static List<FeatureValue<String>> summarizedFeatureVector(List<FeatureValue<String>> featureValues) {
		ClassicCounter<String> sumValues = new ClassicCounter<String>();
		List<FeatureValue<String>> fVector = new ArrayList<FeatureValue<String>>(featureValues.size());
		for (FeatureValue<String> fValue : featureValues) {
			sumValues.incrementCount(fValue.name, fValue.value);
		}
		for (String featureName : sumValues.keySet()) {
			fVector.add(new FeatureValue<String>(featureName, sumValues.getCount(featureName)));
		}
		return fVector;
	}
	
	@Override
	public void saveWeights(String filename) throws IOException {
		System.err.printf("Saving weights to: %s\n", filename);
		BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
		
		double[] weights = wts;
		PriorityQueue<ComparableWtPair> q = new PriorityQueue<ComparableWtPair>();
		
		for (String featureName : featureIndex.keySet()) {
			int idx = featureIndex.indexOf(featureName);
			double value;
			if (idx < 0 || idx >= weights.length) {
				value = 0;
			} else {
				value = weights[idx];
			}
			//System.out.printf("%s:%f\n", featureName, value);
			q.add(new ComparableWtPair(featureName, value));
		}
		for (ComparableWtPair cwp = q.poll(); cwp != null; cwp = q.poll()) {
			writer.append(cwp.featureName).append(" ").append("" + String.format("%e",cwp.value)).append("\n");
		}
		writer.close();
	}
	
	public void displayWts() throws IOException {
		double[] weights = wts;
		PriorityQueue<ComparableWtPair> q = new PriorityQueue<ComparableWtPair>();
		
		for (String featureName : featureIndex.keySet()) {
			int idx = featureIndex.indexOf(featureName);
			double value;
			if (idx < 0 || idx >= weights.length) {
				value = 0;
			} else {
				value = weights[idx];
			}
			//System.out.printf("%s:%f\n", featureName, value);
			q.add(new ComparableWtPair(featureName, value));
		}
		for (ComparableWtPair cwp = q.poll(); cwp != null; cwp = q.poll()) {
			System.err.printf("%s %e\n", cwp.featureName, cwp.value);
		}
	}
	

	@Override
	public void setRandomizeTag(boolean randomizeTag) {
		// TODO Auto-generated method stub
		
	}

	class ComparableWtPair implements Comparable<ComparableWtPair> {
		String featureName;
		double value;
		public ComparableWtPair(String featureName, double value) {
			this.featureName = featureName;
			this.value = value;
		}
		
		@Override
		public int compareTo(ComparableWtPair o) {
			int signum = (int)Math.signum(Math.abs(o.value) - Math.abs(this.value));
			if (signum != 0) return signum;
			return this.featureName.compareTo(o.featureName);
		}
	}

	@Override
	public void displayWeights() {
		try { displayWts(); } catch (Exception e) {;} 
	}
	

}
