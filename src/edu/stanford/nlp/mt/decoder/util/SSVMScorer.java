package edu.stanford.nlp.mt.decoder.util;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.FeatureValue;

import edu.stanford.nlp.util.OAIndex;
import edu.stanford.nlp.classify.km.*;
import edu.stanford.nlp.classify.km.kernels.*;
import edu.stanford.nlp.classify.km.sparselinearalgebra.SparseVector;


public class SSVMScorer implements Scorer<String> {
	public static final double DEFAULT_C = 100.0;
	public static final double DEFAULT_G_C = 1.0;
	public static final double DEFAULT_BIAS_C = Double.POSITIVE_INFINITY;
	public static final double DEFAULT_BIAS_MARGIN = Double.MIN_NORMAL;

	public static final String GENERATIVE_FEATURES_LIST_RESOURCE = "edu/stanford/nlp/mt/resources/generative.features";
	public static final Set<String> generativeFeatures = readGenerativeFeatureList(GENERATIVE_FEATURES_LIST_RESOURCE);
	
	public static Set<String> readGenerativeFeatureList(String resourceName) {
		return readGenerativeFeatureList(resourceName, false);
	}
	
	public static Set<String> readGenerativeFeatureList(String resourceName, boolean verbose) {
		Set<String> gF = new HashSet<String>();
		try {
			LineNumberReader reader = new LineNumberReader(new InputStreamReader(ClassLoader.getSystemClassLoader().getResource(resourceName).openStream()));
			if (verbose) System.err.printf("known generative features:\n");
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String featureName = line.replaceAll("\\s*#.*$", "").replaceAll("\\s+$", "").replaceAll("^\\s+", "");
				if (featureName.equals("")) continue;
				gF.add(featureName);
				if (verbose) System.err.printf("\t'%s'\n", featureName);
			}
		} catch(IOException e) {
			System.err.printf("Unable to load resouce: %s\n", resourceName);
			System.exit(-1);
		}
		return gF;
	}
	
	final OAIndex<String> featureIndex;
	final double[] manualWeights;
	private boolean[] generativeWts = new boolean[0];
	StructuredSVM ssvm;
	final StructuredSVM gssvm;
	Set<String> nonZeroFeatures = null;
	Set<String> zeroFeatures = null;
	

		
	public void allFeatures() {
		this.zeroFeatures = null;
		this.nonZeroFeatures = null;
	}
	
	private   double manualWeightMul = 0.0;
	private  double classifierWeightMul = 1.0;
	private final int maxDataPts;
	
	private boolean useRBF = false;
	
	public void setUseRBF(boolean useRBF) {
		this.useRBF = useRBF; 
	}
	
	static {
		System.err.printf("SSVMScore default c: %f", DEFAULT_C);
		System.err.printf("SSVMScore default bias c: %f", DEFAULT_BIAS_C);
	}
	
	public void setWeightMultipliers(double manualWeightMul, double classifierWeightMul) {
		this.manualWeightMul = manualWeightMul;
		this.classifierWeightMul = classifierWeightMul;
	}
	
	public void flipWeights() {
		this.classifierWeightMul = - this.classifierWeightMul;
		System.err.printf("Classifier Weight Mul flipped to: %f\n", classifierWeightMul);
	}
	
	final boolean constrainManualWeights;
	
	public SSVMScorer(Map<String,Double> manualFeatureWts, int dataPts, double wtMultiplier, boolean constrainManualWeights) {
		featureIndex = new OAIndex<String>();
		dataPts = 100000; // XXX
		for (String key : manualFeatureWts.keySet()) {
			featureIndex.indexOf(key, true);
			System.err.printf("---inserting: '%s' index: %d\n", key, featureIndex.indexOf(key));
		}
	
		manualWeights = new double[featureIndex.boundOnMaxIndex()];
		
		for (String key : manualFeatureWts.keySet()) {
			manualWeights[featureIndex.indexOf(key)] = wtMultiplier*manualFeatureWts.get(key).doubleValue();
		}
		
		this.maxDataPts = dataPts;
		double[] C = new double[maxDataPts+100];
		double[] gC = new double[maxDataPts];
		for (int i = 0; i < maxDataPts; i++) {
			C[i] = DEFAULT_C;
			gC[i] = DEFAULT_G_C;
		}
		for (int i = maxDataPts; i < maxDataPts+100; i++) {
			C[i] = DEFAULT_BIAS_C;
		}
		
		this.constrainManualWeights = constrainManualWeights;
		ssvm = StructuredSVM.trainableMCSVM(Kernel.factory("linear"), C, StructuredSVM.StructLoss.MarginRescale, maxDataPts+100);
		gssvm = StructuredSVM.trainableMCSVM(Kernel.factory("grbf:1"), C, StructuredSVM.StructLoss.MarginRescale, maxDataPts);
		addBiasPt();
		System.err.printf("Initial Weights\n");
		displayWeights();
	}
	
	public void resetSSVM() {
		ssvm = StructuredSVM.trainableMCSVM(Kernel.factory("linear"), ssvm.getC(), StructuredSVM.StructLoss.MarginRescale, maxDataPts+100);
		addBiasPt();
	}
	
	public SSVMScorer(Map<String,Double> featureWts) {
		constrainManualWeights = false;
		gssvm = null;
		featureIndex = new OAIndex<String>();
		for (String key : featureWts.keySet()) {
			featureIndex.indexOf(key, true);
			System.err.printf("---inserting: '%s' index: %d\n", key, featureIndex.indexOf(key));
		}
		
		manualWeights = new double[featureIndex.boundOnMaxIndex()];
		maxDataPts = 1;
		for (String key : featureWts.keySet()) {
			manualWeights[featureIndex.indexOf(key)] = featureWts.get(key).doubleValue();
			
		}
		
		ssvm = StructuredSVM.trainableMCSVM(Kernel.factory("linear"), new double[]{DEFAULT_C, DEFAULT_BIAS_C}, StructuredSVM.StructLoss.MarginRescale, 2);
		addBiasPt();
	}
	
	SparseVector biasVector = null;
	private void addBiasPt() {
		
		int cPt = 1;
		SparseVector emptyVector = new SparseVector(new HashMap<Integer,Double>());
		if (constrainManualWeights) {
  		for (int i = 0; i < manualWeights.length; i++) {
  			Map<Integer,Double> vMap = new HashMap<Integer,Double>();
  			vMap.put(i, 1.0);
  			SparseVector lockVector = new SparseVector(vMap);
  		  ssvm.getC()[maxDataPts+cPt] = Double.NEGATIVE_INFINITY;
  		  System.err.printf("Contraining %d >= %e\n", i, manualWeights[i]);
  			ssvm.expandSubProblem(0, maxDataPts+cPt++, lockVector, emptyVector, manualWeights[i], true);
  			ssvm.getC()[maxDataPts+cPt] = Double.NEGATIVE_INFINITY;
  			displayWeights();
  			System.err.printf("Contraining %d <= %e\n", i, manualWeights[i]);
  			ssvm.expandSubProblem(0, maxDataPts+cPt++, emptyVector, lockVector, -manualWeights[i], true);
  			displayWeights();
  		}
		}
	  
		biasVector = new SparseVector(new HashMap<Integer,Double>());
		//System.err.printf("bias vector l2: %e\n", biasVector.l2norm());
		//ssvm.expandSubProblem(0, maxDataPts, biasVector, emptyVector, DEFAULT_BIAS_MARGIN, true);
		
		int maxIndex = 0;
		if (!constrainManualWeights) {
			System.err.printf("Adding bias pt\n");
			Map<Integer,Double> vMap = new HashMap<Integer,Double>();
			for (int i = 0; i < manualWeights.length; i++) {	
  			if (manualWeights[i] != 0) vMap.put(i, manualWeights[i]);
  		}
			
		  ssvm.getC()[maxDataPts+cPt] = Double.NEGATIVE_INFINITY;			
			//ssvm.expandSubProblem(0, maxDataPts+cPt++, new SparseVector(vMap), emptyVector, 0.1, true);
			displayWeights();
			
  		vMap = new HashMap<Integer,Double>();
  		
  		for (String generativeFeature : generativeFeatures) {
  			vMap.clear();
  			System.err.printf("Constratining Generative Feature: %s(%d) >= 0 alpha idx: %d\n", generativeFeature, featureIndex.indexOf(generativeFeature, true), maxDataPts+cPt);
  			
  			vMap.put(featureIndex.indexOf(generativeFeature, true), 1.0);
  			if (maxIndex < featureIndex.indexOf(generativeFeature, true)) maxIndex =  featureIndex.indexOf(generativeFeature, true); 
  			SparseVector cVector = new SparseVector(vMap);
  		  ssvm.getC()[maxDataPts+cPt] = Double.NEGATIVE_INFINITY;
  			ssvm.expandSubProblem(0, maxDataPts+cPt++, cVector, emptyVector, 0.0, true);  
  		} 
  		generativeWts = new boolean[maxIndex+1];
  		/* XXXX
  		for (String generativeFeature : generativeFeatures) {
  			generativeWts[featureIndex.indexOf(generativeFeature)] = true;
  		} */
		}
	}
	
	static private double l2normDense(double[] vec) {
		double l2norm = 0;
		for (int i = 0; i < vec.length; i++) {
			l2norm += vec[i]*vec[i];
		}
		return Math.sqrt(l2norm);
	}
	
	@Override
	public double getIncrementalScore(Collection<FeatureValue<String>> features) {
		if (!useRBF) {
			double[] ssvmWeights = ssvm.getWeights();
			
			double score = 0;
			for (FeatureValue<String> feature : features) {
				if (zeroFeatures != null && zeroFeatures.contains(feature.name)) continue;
				if (nonZeroFeatures != null && !nonZeroFeatures.contains(feature.name)) continue;
				int idx = featureIndex.indexOf(feature.name, false);
				if (idx < 0) continue;
				
				if  (idx < manualWeights.length) {
					double wt = manualWeights[idx];
					score += wt*manualWeightMul*feature.value;
				}
				
				if (idx < ssvmWeights.length) {
					double wt = ssvmWeights[idx];
					if (idx < generativeWts.length && generativeWts[idx] && wt < 0) continue;
					score += wt*classifierWeightMul*feature.value;
				}
			}
			return score;
		} else {
			SparseVector featureVector = createVector(features, false);
			return gssvm.score(featureVector);
		}		
	}
	
	public SparseVector createVector(Collection<FeatureValue<String>> features, boolean addFeatureValues) {
		
		Map<Integer,Double> vMap = new HashMap<Integer,Double>();
		for (FeatureValue<String> feature : features) {
			int id = featureIndex.indexOf(feature.name, addFeatureValues);
			if (id == -1) continue;
			Double v = vMap.get(id);
			if (v == null) {
				vMap.put(id, feature.value);
			} else {
				vMap.put(id, feature.value+v);
			}
		}
		return new SparseVector(vMap);
	}

	public void saveWeights(String filename) throws IOException {
		System.err.printf("Saving weights to: %s\n", filename);
		BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
		double[] weights = ssvm.getWeights();
		PriorityQueue<ComparableWtPair> q = new PriorityQueue<ComparableWtPair>();
		
		for (String featureName : featureIndex.keySet()) {
			int idx = featureIndex.indexOf(featureName);
			double value;
			if (idx < 0 || idx >= weights.length) {
				value = 0;
			} else {
				value = weights[idx];
			}
			if (value == 0) continue;
			q.add(new ComparableWtPair(featureName, value));
		}
		for (ComparableWtPair cwp = q.poll(); cwp != null; cwp = q.poll()) {
			writer.append(cwp.featureName).append(" ").append("" + String.format("%e",cwp.value)).append("\n");
		}
		writer.close();
	}
	
	private class ComparableWtPair implements Comparable<ComparableWtPair> {
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
	
	public double weightL2Norm() {
		return l2normDense(ssvm.getWeights());
	}
	
	public double angleBias() {
		return biasVector.dotProduct(ssvm.getWeights())/(l2normDense(ssvm.getWeights())*biasVector.l2norm());
	}
	
	public double noCostBiasCosine() {
		return (DEFAULT_BIAS_MARGIN)/weightL2Norm();
	}
	
	public void displayWeights() {
		System.err.printf("Weights\n");
		double[] weights = ssvm.getWeights();
		for (String featureName : featureIndex.keySet()) {
			int idx = featureIndex.indexOf(featureName);
			double value;
			if (idx < 0 || idx >= weights.length) {
				value = 0;
			} else {
				value = weights[idx];
			}
			if (value == 0) continue;
			System.err.printf("%s %e\n", featureName, value);
		}
	}


	public boolean hasNonZeroWeight(String featureName) {
		int idx = featureIndex.indexOf(featureName);
		double[] weights = ssvm.getWeights();
		if (idx < 0 || idx >= weights.length) {
			return false;
		}
		return weights[idx] == weights[idx] && weights[idx] != 0;
	}


	boolean randomizeTag = false;
	
	public boolean randomizeTag() {
		// TODO Auto-generated method stub
		return randomizeTag;
	}


	
	public void setRandomizeTag(boolean randomizeTag) {
		this.randomizeTag = randomizeTag;
	}
}
