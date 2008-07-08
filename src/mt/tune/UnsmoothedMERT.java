package mt.tune;

import java.io.*;
import java.util.*;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.sparse.CompRowMatrix;

import mt.base.*;
import mt.decoder.util.*;
import mt.metrics.*;
import mt.reranker.ter.*;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.svd.ReducedSVD;
import edu.stanford.nlp.util.MutableDouble;
import edu.stanford.nlp.util.Ptr;

/**
 * Minimum Error Rate Training (MERT).
 *
 * Optimization for non smooth error surfaces.
 *
 * @author danielcer
 */
public class UnsmoothedMERT {

  private UnsmoothedMERT() {}

  public static final String GENERATIVE_FEATURES_LIST_RESOURCE = "mt/resources/generative.features";
	public static final Set<String> generativeFeatures = SSVMScorer
			.readGenerativeFeatureList(GENERATIVE_FEATURES_LIST_RESOURCE);

	static public final boolean DEBUG = false;
  public final static double DEFAULT_C = 100;
  public static double C = DEFAULT_C;
  public static final double DEFAULT_T = 1;
  public static double T = DEFAULT_T;
	public static final double DEFAULT_UNSCALED_L_RATE = 0.1;
	public static double lrate = DEFAULT_UNSCALED_L_RATE;
	static final double MIN_OBJECTIVE_CHANGE_SGD = 1e-5;
	static final int DEFAULT_MAX_ITER_SGD = 1000;
	static final int NO_PROGRESS_LIMIT = 20;
	static final double NO_PROGRESS_SSD = 1e-6;
	static final double NO_PROGRESS_MCMC_TIGHT_DIFF = 1e-6;
	static final double NO_PROGRESS_MCMC_COSINE = 0.95;
  static final int MCMC_BATCH_SAMPLES = 10;
  static final int MCMC_MIN_BATCHES = 0; 
  static final int MCMC_MAX_BATCHES = 20;
  static final int MCMC_MAX_BATCHES_TIGHT = 50; 
  
  static final double MAX_LOCAL_ALL_GAP_WTS_REUSE = 0.035;

	static public final double MIN_PLATEAU_DIFF = 0.0;
	static public final double MIN_OBJECTIVE_DIFF = 1e-5;


  static class ObjELossDiffFunction implements DiffFunction, HasInitial {

  	final MosesNBestList nbest;
  	final ClassicCounter<String> initialWts;
  	final EvaluationMetric<IString,String> emetric;
  	final int domainDimension;

  	final List<String> featureIdsToString;
  	final double[] initial;
  	final double[] derivative;

  	public ObjELossDiffFunction(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric) {
  		this.nbest = nbest;
  		this.initialWts = initialWts;
  		this.emetric = emetric;
  		Set<String> featureSet = new HashSet<String>();
  		featureIdsToString = new ArrayList<String>();
  		List<Double> initialFeaturesVector = new ArrayList<Double>();

  		for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) {
  			for (ScoredFeaturizedTranslation<IString,String> trans : nbestlist) {
  				for (FeatureValue<String> featureValue : trans.features) {
  					if (!featureSet.contains(featureValue.name)) {
  						featureSet.add(featureValue.name);
  						featureIdsToString.add(featureValue.name);
  						initialFeaturesVector.add(initialWts.getCount(featureValue.name));
  					}
  				}
  			}
  		}

  		initial = new double[initialFeaturesVector.size()];
  		derivative = new double[initialFeaturesVector.size()];
  		for (int i = 0; i < initial.length; i++) {
  			initial[i] = initialFeaturesVector.get(i);
  		}
  		domainDimension = featureSet.size();
  	}

  	@Override
    public int domainDimension() { return domainDimension; }

  	@Override
    public double[] initial() {
  		return initial;
  	}

  	@SuppressWarnings("deprecation")
		@Override
		public double[] derivativeAt(double[] wtsDense) {

			ClassicCounter<String> wtsCounter = new ClassicCounter<String>();
  		for (int i = 0; i < wtsDense.length; i++) {
  			wtsCounter.incrementCount(featureIdsToString.get(i), wtsDense[i]);
  		}

  		MutableDouble expectedEval = new MutableDouble();
  		ClassicCounter<String> dE = mcmcDerivative(nbest, wtsCounter,
        emetric, expectedEval);

      for (int i = 0; i < derivative.length; i++) {
      	derivative[i] = dE.getCount(featureIdsToString.get(i));
      }
			return derivative;
		}

    public ClassicCounter<String> getBestWts() {
			ClassicCounter<String> wtsCounter = new ClassicCounter<String>();
  		for (int i = 0; i < bestWts.length; i++) {
  			wtsCounter.incrementCount(featureIdsToString.get(i), bestWts[i]);
  		}
      return wtsCounter;
    }

		@Override
		public double valueAt(double[] wtsDense) {
			ClassicCounter<String> wtsCounter = new ClassicCounter<String>();

  		for (int i = 0; i < wtsDense.length; i++) {
  			if (wtsDense[i] != wtsDense[i]) throw new RuntimeException("Weights contain NaN");
  			wtsCounter.incrementCount(featureIdsToString.get(i), wtsDense[i]);
  		}
			double eval =	mcmcTightExpectedEval(nbest, wtsCounter, emetric);
      if (eval < bestEval) {
        bestWts = wtsDense.clone();
      }
			return mcmcTightExpectedEval(nbest, wtsCounter, emetric);
		}
    double bestEval = Double.POSITIVE_INFINITY;
    double[] bestWts;
  }

  static public ClassicCounter<String> reducedWeightsToWeights(
     ClassicCounter<String> reducedWts,
     Matrix reducedRepU, Map<String,Integer> featureIdMap) {

  	int col = reducedRepU.numColumns();
  	Vector vecReducedWts = new DenseVector(col);
  	for (int i = 0; i < col; i++) {
  		vecReducedWts.set(i, reducedWts.getCount((new Integer(i)).toString()));
  	}

  	Vector vecWts = new DenseVector(reducedRepU.numRows());
  	reducedRepU.mult(vecReducedWts, vecWts);

  	ClassicCounter<String> wts = new ClassicCounter<String>();
  	for (Map.Entry<String,Integer> entry : featureIdMap.entrySet()) {
  		wts.setCount(entry.getKey(), vecWts.get(entry.getValue()));
  	}

  	return wts;
  }

  static public ClassicCounter<String> weightsToReducedWeights(
    ClassicCounter<String> wts, Matrix reducedRepU,
    Map<String,Integer> featureIdMap) {

  	Matrix vecWtsOrig = new DenseMatrix(featureIdMap.size(), 1);
  	for (Map.Entry<String,Integer> entry : featureIdMap.entrySet()) {
  		vecWtsOrig.set(entry.getValue(), 0, wts.getCount(entry.getKey()));
  	}

  	Matrix vecWtsReduced = new DenseMatrix(reducedRepU.numColumns(), 1);
  	reducedRepU.transAmult(vecWtsOrig, vecWtsReduced);

  	ClassicCounter<String> reducedWts = new ClassicCounter<String>();
    for (int i = 0; i < vecWtsReduced.numRows(); i++) {
  		reducedWts.setCount((new Integer(i)).toString(), vecWtsReduced.get(i,0));
  	}

  	return reducedWts;
  }

  static public MosesNBestList nbestListToDimReducedNbestList(
    MosesNBestList nbest, Matrix reducedRepV) {

  	List<List<ScoredFeaturizedTranslation<IString,String>>> oldNbestLists = nbest.nbestLists();
  	List<List<ScoredFeaturizedTranslation<IString,String>>> newNbestLists = new ArrayList<List<ScoredFeaturizedTranslation<IString, String>>>(oldNbestLists.size());

  	int nbestId = -1;
  	int numNewFeat = reducedRepV.numColumns();
    //System.err.printf("V rows: %d cols: %d\n", reducedRepV.numRows(),
    //    reducedRepV.numColumns());
  	for (int listId = 0; listId < oldNbestLists.size(); listId++) {
  		 List<ScoredFeaturizedTranslation<IString, String>> oldNbestlist =
         oldNbestLists.get(listId);
  		 List<ScoredFeaturizedTranslation<IString, String>> newNbestlist =
         new ArrayList<ScoredFeaturizedTranslation<IString, String>>
         (oldNbestlist.size());
  		 newNbestLists.add(newNbestlist);
  		 for (int transId = 0; transId < oldNbestlist.size(); transId++) {
  		   nbestId++;
  			 ScoredFeaturizedTranslation<IString,String> oldTrans =
           oldNbestlist.get(transId);
  		   List<FeatureValue<String>> reducedFeatures =
           new ArrayList<FeatureValue<String>>(numNewFeat);
  		   for (int featId = 0; featId < numNewFeat; featId++) {
     //      System.err.printf("%d:%d\n", featId, nbestId);
  		  	 reducedFeatures.add(new FeatureValue<String>(
                  (new Integer(featId)).toString(),
                  reducedRepV.get(nbestId, featId)));
  		   }
  		   ScoredFeaturizedTranslation<IString,String> newTrans =
           new ScoredFeaturizedTranslation<IString, String>
           (oldTrans.translation, reducedFeatures, 0);
  		   newNbestlist.add(newTrans);
  		 }
  	}

  	return new MosesNBestList(newNbestLists);
  }

  static public void nbestListToFeatureDocumentMatrix(MosesNBestList nbest, Ptr<Matrix> pFeatDocMat, Ptr<Map<String,Integer>> pFeatureIdMap) {

  	// build map from feature names to consecutive unique integer ids
  	Map<String,Integer> featureIdMap = new HashMap<String,Integer>();
  	for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) {
  		for (ScoredFeaturizedTranslation<IString,String> trans : nbestlist) {
  			for (FeatureValue<String> fv : trans.features) {
  				if (!featureIdMap.containsKey(fv.name)) {
  					featureIdMap.put(fv.name, featureIdMap.size()); } } } }

  	// build list representation of feature document matrix
  	List<List<Integer>> listFeatDocMapId = new ArrayList<List<Integer>>(featureIdMap.size());
  	List<List<Double>> listFeatDocMapVal = new ArrayList<List<Double>>(featureIdMap.size());
  	for (int i = 0; i < featureIdMap.size(); i++) {
  		listFeatDocMapId.add(new ArrayList<Integer>());
  		listFeatDocMapVal.add(new ArrayList<Double>());
  	}

  	int nbestId = -1;
  	for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) {
  		for (ScoredFeaturizedTranslation<IString,String> trans : nbestlist) {
  			nbestId++;
  			for (FeatureValue<String> fv : trans.features) {
  				int featureId = featureIdMap.get(fv.name);
  				listFeatDocMapId.get(featureId).add(nbestId);
  				listFeatDocMapVal.get(featureId).add(fv.value);
  			}
  		}
  	}

  	// prepare to create compressed row matrix
  	int[][] nonZeros = new int[listFeatDocMapId.size()][];
  	for (int i = 0; i < nonZeros.length; i++) {
  		int[] row = new int[listFeatDocMapId.get(i).size()];
  		for (int j = 0; j < row.length; j++) {
  			row[j] = listFeatDocMapId.get(i).get(j);
  		}
  		nonZeros[i] = row;
  	}

  	Matrix featDocMat = new CompRowMatrix(nonZeros.length, nbestId+1, nonZeros);
  	for (int i = 0; i < nonZeros.length; i++) {
  		for (int j = 0; j < nonZeros[i].length; j++) {
  			featDocMat.set(i, nonZeros[i][j], listFeatDocMapVal.get(i).get(j));
  		}
  	}

  	pFeatDocMat.set(featDocMat);
  	pFeatureIdMap.set(featureIdMap);
  }

  static public double mcmcTightExpectedEval(MosesNBestList nbest, ClassicCounter<String> wts, EvaluationMetric<IString,String> emetric) {
     return mcmcTightExpectedEval(nbest, wts, emetric, true);
  }

  static public double mcmcTightExpectedEval(MosesNBestList nbest, ClassicCounter<String> wts, EvaluationMetric<IString,String> emetric, boolean regularize) {
  	System.err.printf("TMCMC weights:\n%s\n\n", wts.toString(35));
  	
  	// for quick mixing, get current classifier argmax
		List<ScoredFeaturizedTranslation<IString, String>> argmax = transArgmax(nbest, wts), current = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(argmax);
				
		// recover which candidates were selected
		int argmaxCandIds[] = new int[current.size()]; Arrays.fill(argmaxCandIds, -1);
		for (int i = 0; i < nbest.nbestLists().size(); i++) {
			for (int j = 0; j < nbest.nbestLists().get(i).size(); j++) { 
				if (current.get(i) == nbest.nbestLists().get(i).get(j)) argmaxCandIds[i] = j;
			}			
		}
		
		Scorer<String> scorer = new StaticScorer(wts);
		
		int cnt = 0;
		double dEEval = Double.POSITIVE_INFINITY;
		
   	// expected value sum
		double sumExpL = 0.0;
		for (int batch = 0; 
         (Math.abs(dEEval) > NO_PROGRESS_MCMC_TIGHT_DIFF
         || batch < MCMC_MIN_BATCHES) &&
         batch < MCMC_MAX_BATCHES_TIGHT; 
         batch++) {
						
		  double oldExpL = sumExpL/cnt;
	
			for (int bi = 0; bi < MCMC_BATCH_SAMPLES; bi++) {
				// gibbs mcmc sample
				if (cnt != 0)  // always sample once from argmax
        for (int sentId = 0; sentId < nbest.nbestLists().size(); sentId++) {
					double Z = 0;
					double[] num = new double[nbest.nbestLists().get(sentId).size()];
					int pos = -1; for (ScoredFeaturizedTranslation<IString, String> trans : nbest.nbestLists().get(sentId)) { pos++;
					  Z += num[pos] = Math.exp(scorer.getIncrementalScore(trans.features));
					}
					
					int selection = -1;
					if (Z != 0) {
  					double rv = r.nextDouble()*Z;
  					for (int i = 0; i < num.length; i++) {
  						if ((rv -= num[i]) <= 0) { selection = i; break; }
  					}
					} else {
						selection = r.nextInt(num.length);
					}
					
					if (Z == 0) {
						Z = 1.0;
						num[selection] = 1.0/num.length;
					}
					
					// adjust current
					current.set(sentId, nbest.nbestLists().get(sentId).get(selection));
				}
				
				// collect derivative relevant statistics using sample
				cnt++;
				
				// adjust currentF & eval
				double  eval = emetric.score(current);

				sumExpL += eval;					
			}
			
		  dEEval = (oldExpL != oldExpL ? Double.POSITIVE_INFINITY : 
        oldExpL - sumExpL/cnt);
	
			System.err.printf("TBatch: %d dEEval: %e cnt: %d\n", batch, dEEval, cnt);
			System.err.printf("E(loss) = %e (sum: %e)\n", sumExpL/cnt, sumExpL);
		}
		
		// objective 0.5*||w||_2^2 - C * E(Eval), e.g. 0.5*||w||_2^2 - C * E(BLEU)
		double l2wts = Counters.L2Norm(wts);
		double obj = (C != 0 && regularize ? 0.5*l2wts*l2wts -C*sumExpL/cnt : -sumExpL/cnt);
		System.err.printf("Regularized objective 0.5*||w||_2^2 - C * E(Eval): %e\n", obj);
		System.err.printf("C: %e\n", C);
		System.err.printf("||w||_2^2: %e\n", l2wts*l2wts);
		System.err.printf("E(loss) = %e\n", sumExpL/cnt);
		return obj;
  }

  static public ClassicCounter<String> mcmcDerivative(
    MosesNBestList nbest, ClassicCounter<String> wts,
    EvaluationMetric<IString,String> emetric) {

  	return mcmcDerivative(nbest, wts, emetric, null);
  }

	static public ClassicCounter<String> mcmcDerivative(
    MosesNBestList nbest, ClassicCounter<String> wts,
    EvaluationMetric<IString,String> emetric, MutableDouble expectedEval) {

  	return mcmcDerivative(nbest, wts, emetric, expectedEval, null);
  }

	@SuppressWarnings({ "deprecation" })
	static public ClassicCounter<String> mcmcDerivative(MosesNBestList nbest, 
    ClassicCounter<String> wts, EvaluationMetric<IString,String> emetric, 
    MutableDouble expectedEval, MutableDouble objValue) {

		System.err.printf("MCMC weights:\n%s\n\n", wts.toString(35));
		
		// for quick mixing, get current classifier argmax
    System.err.println("finding argmax");
		List<ScoredFeaturizedTranslation<IString, String>> argmax = 
      transArgmax(nbest, wts), current = 
        new ArrayList<ScoredFeaturizedTranslation<IString, String>>(argmax);
		

		
		// recover which candidates were selected
    System.err.println("recovering cands");
		int argmaxCandIds[] = new int[current.size()]; Arrays.fill(argmaxCandIds, -1);
		for (int i = 0; i < nbest.nbestLists().size(); i++) {
			for (int j = 0; j < nbest.nbestLists().get(i).size(); j++) { 
				if (current.get(i) == nbest.nbestLists().get(i).get(j)) 
          argmaxCandIds[i] = j;
			}			
		}
		
		ClassicCounter<String> dE = new ClassicCounter<String>();
		Scorer<String> scorer = new StaticScorer(wts);

    double hardEval = emetric.score(argmax);
    System.err.printf("Hard eval: %.5f\n", hardEval);
		
		// expected value sums
		OpenAddressCounter<String> sumExpLF = new OpenAddressCounter<String>(0.50f);
		double sumExpL = 0.0;
		OpenAddressCounter<String> sumExpF = new OpenAddressCounter<String>(0.50f);
		int cnt = 0;
		double dEDiff = Double.POSITIVE_INFINITY;
    double dECosine = 0.0;
		for (int batch = 0; 
         (dECosine < NO_PROGRESS_MCMC_COSINE
         || batch < MCMC_MIN_BATCHES) &&
         batch < MCMC_MAX_BATCHES; batch++) {
			ClassicCounter<String> oldDe = new ClassicCounter<String>(dE);

			// reset current to argmax
			current = new ArrayList<ScoredFeaturizedTranslation<IString, String>>
        (argmax);

			int[] candIds = argmaxCandIds.clone();
			
			// reset incremental evaluation object for the argmax candidates
			IncrementalEvaluationMetric<IString, String> incEval = 
        emetric.getIncrementalMetric();			

			for (ScoredFeaturizedTranslation<IString, String> tran : current) 
        incEval.add(tran);
			
			OpenAddressCounter<String> currentF = 
        new OpenAddressCounter<String>(summarizedAllFeaturesVector(current), 0.50f);
			
      System.err.println("Sampling");
  
      long time = -System.currentTimeMillis();
			for (int bi = 0; bi < MCMC_BATCH_SAMPLES; bi++) {
				// gibbs mcmc sample
				if (cnt != 0)  // always sample once from argmax
        for (int sentId = 0; sentId < nbest.nbestLists().size(); sentId++) {
					double Z = 0;
					double[] num = new double[nbest.nbestLists().get(sentId).size()];
					int pos = -1; for (ScoredFeaturizedTranslation<IString, String> trans : nbest.nbestLists().get(sentId)) { pos++;
					  Z += num[pos] = Math.exp(scorer.getIncrementalScore(trans.features));
					  // System.err.printf("%d: featureOnly score: %g\n", pos, Math.exp(scorer.getIncrementalScore(trans.features)));
					}
					//System.out.printf("%d:%d - Z: %e\n", bi, sentId, Z);
					// System.out.printf("num[]: %s\n", Arrays.toString(num));
					
					
					int selection = -1;
					if (Z != 0) {
  					double rv = r.nextDouble()*Z;
  					for (int i = 0; i < num.length; i++) {
  						if ((rv -= num[i]) <= 0) { selection = i; break; }
  					}
					} else {
						selection = r.nextInt(num.length);
					}
					
					if (Z == 0) {
						Z = 1.0;
						num[selection] = 1.0/num.length;
					}
					//System.out.printf("%d:%d - selection: %d p(%g|f) %g/%g\n", bi, sentId, selection, num[selection]/Z, num[selection], Z);
					
					// adjust current
					current.set(sentId, nbest.nbestLists().get(sentId).get(selection));
				}
				
				// collect derivative relevant statistics using sample
				cnt++;
				
				// adjust currentF & eval
				currentF = 
	        new OpenAddressCounter<String>(summarizedAllFeaturesVector(current), 0.50f);				
				double eval = emetric.score(current);
				sumExpL += eval;					

        System.out.printf("Sample: %d(%d) Eval: %g E(Loss): %g\n", cnt, bi, eval, sumExpL/cnt);
				
        for (Object2DoubleMap.Entry<String> entry : 
          currentF.object2DoubleEntrySet()) {
          String k = entry.getKey();
          double val = entry.getDoubleValue();
          sumExpF.incrementCount(k, val);
          sumExpLF.incrementCount(k, val*eval);
        }
			}
      time += System.currentTimeMillis();
			
			dE = new ClassicCounter<String>(sumExpF);
			Counters.multiplyInPlace(dE, sumExpL/cnt);
			Counters.subtractInPlace(dE, sumExpLF);
			Counters.divideInPlace(dE, -1*cnt);
			dEDiff = wtSsd(oldDe, dE);
      dECosine = Counters.dotProduct(oldDe, dE)/
                 (Counters.L2Norm(dE)*Counters.L2Norm(oldDe));
      if (dECosine != dECosine) dECosine = 0;
			
			System.err.printf("Batch: %d dEDiff: %e dECosine: %g)\n", 
        batch, dEDiff, dECosine);
      System.err.printf("Sampling time: %.3f s\n", time/1000.0);
			System.err.printf("E(loss) = %e\n", sumExpL/cnt);
			System.err.printf("E(loss*f):\n%s\n\n", ((ClassicCounter<String>)Counters.divideInPlace(new ClassicCounter<String>(sumExpLF),(cnt))).toString(35));
			System.err.printf("E(f):\n%s\n\n", ((ClassicCounter<String>)Counters.divideInPlace(new ClassicCounter<String>(sumExpF), cnt)).toString(35));
			System.err.printf("dE:\n%s\n\n", dE.toString(35));
		}
    System.err.printf("Hard eval: %.5f E(Eval): %.5f diff: %e\n", 
        hardEval, sumExpL/cnt,
        hardEval-sumExpL/cnt);

		double l2wts = Counters.L2Norm(wts);
		double obj = (C != 0 ? 0.5*l2wts*l2wts-C*sumExpL/cnt : -sumExpL/cnt);
		System.err.printf("DRegularized objective 0.5*||w||_2^2 - C * E(Eval): %e\n", obj);
		System.err.printf("C: %e\n", C);
		System.err.printf("||w||_2^2: %e\n", l2wts*l2wts);
		System.err.printf("E(loss) = %e\n", sumExpL/cnt);
		
		if (expectedEval != null) expectedEval.set(sumExpL/cnt);
    if (objValue != null) objValue.set(obj);
		
		// obtain dObj by adding in regularization terms to dE
    ClassicCounter<String> dObj = new ClassicCounter<String>(dE);
    

    if (C != 0) {
    	Counters.multiplyInPlace(dObj, -C);
    	for (String key : wts.keySet()) {
    		dObj.incrementCount(key, wts.getCount(key));
    	}
    } else {
    	Counters.multiplyInPlace(dObj, -1.0);
    }
		return dObj;
	}

	static class InterceptIDs {
		final int list;
		final int trans;

		InterceptIDs(int list, int trans) {
			this.list = list;
			this.trans = trans;
		}
	}

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> lineSearch(MosesNBestList nbest,
			ClassicCounter<String> initialWts, ClassicCounter<String> direction,
			EvaluationMetric<IString, String> emetric) {
		Scorer<String> currentScorer = new StaticScorer(initialWts);
		Scorer<String> slopScorer = new StaticScorer(direction);
		ArrayList<Double> intercepts = new ArrayList<Double>();
		Map<Double, Set<InterceptIDs>> interceptToIDs = new HashMap<Double, Set<InterceptIDs>>();

		{
			int lI = -1;
			for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
					.nbestLists()) {
				lI++;
				// calculate slops/intercepts
				double[] m = new double[nbestlist.size()];
				double[] b = new double[nbestlist.size()];
				{
					int tI = -1;
					for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
						tI++;
						m[tI] = slopScorer.getIncrementalScore(trans.features);
						b[tI] = currentScorer.getIncrementalScore(trans.features);
					}
				}

				// find -inf*dir candidate
				int firstBest = 0;
				for (int i = 1; i < m.length; i++) {
					if (m[i] < m[firstBest]
							|| (m[i] == m[firstBest] && b[i] > b[firstBest])) {
						firstBest = i;
					}
				}

				Set<InterceptIDs> niS = interceptToIDs.get(Double.NEGATIVE_INFINITY);
				if (niS == null) {
					niS = new HashSet<InterceptIDs>();
					interceptToIDs.put(Double.NEGATIVE_INFINITY, niS);
				}

				niS.add(new InterceptIDs(lI, firstBest));

				// find & save all intercepts
				double interceptLimit = Double.NEGATIVE_INFINITY;
				for (int currentBest = firstBest; currentBest != -1;) {
					// find next intersection
					double nearestIntercept = Double.POSITIVE_INFINITY;
					int nextBest = -1;
					for (int i = 0; i < m.length; i++) {
						double intercept = (b[currentBest] - b[i])
								/ (m[i] - m[currentBest]); // wow just like middle school
						if (intercept <= interceptLimit + MIN_PLATEAU_DIFF)
							continue;
						if (intercept < nearestIntercept) {
							nextBest = i;
							nearestIntercept = intercept;
						}
					}
					if (nearestIntercept == Double.POSITIVE_INFINITY)
						break;
					if (DEBUG) {
						System.out.printf("Nearest intercept: %e Limit: %e\n",
								nearestIntercept, interceptLimit);
					}
					intercepts.add(nearestIntercept);
					interceptLimit = nearestIntercept;
					Set<InterceptIDs> s = interceptToIDs.get(nearestIntercept);
					if (s == null) {
						s = new HashSet<InterceptIDs>();
						interceptToIDs.put(nearestIntercept, s);
					}
					s.add(new InterceptIDs(lI, nextBest));
					currentBest = nextBest;
				}
			}
		}

		// check eval score at each intercept;
		double bestEval = Double.NEGATIVE_INFINITY;
		ClassicCounter<String> bestWts = initialWts;
		if (intercepts.size() == 0)
			return initialWts;
		intercepts.add(Double.NEGATIVE_INFINITY);
		Collections.sort(intercepts);
		resetQuickEval(emetric, nbest);
		System.out.printf("Checking %d points\n", intercepts.size() - 1);

		double[] evals = new double[intercepts.size()];
		double[] chkpts = new double[intercepts.size()];

		for (int i = 0; i < intercepts.size(); i++) {
			double chkpt;
			if (i == 0) {
				chkpt = intercepts.get(i + 1) - 1.0;
			} else if (i + 1 == intercepts.size()) {
				chkpt = intercepts.get(i) + 1.0;
			} else {
				if (intercepts.get(i) < 0 && intercepts.get(i + 1) > 0) {
					chkpt = 0;
				} else {
					chkpt = (intercepts.get(i) + intercepts.get(i + 1)) / 2.0;
				}
			}
			if (DEBUG)
				System.out.printf("intercept: %f, chkpt: %f\n", intercepts.get(i),
						chkpt);
			double eval = quickEvalAtPoint(nbest, interceptToIDs.get(intercepts
					.get(i)));

			chkpts[i] = chkpt;
			evals[i] = eval;

			if (DEBUG) {
				System.out.printf("pt(%d): %e eval: %e best: %e\n", i, chkpt, eval,
						bestEval);
			}
		}

		int bestPt = -1;
		for (int i = 0; i < evals.length; i++) {
			double eval = windowSmooth(evals, i, SEARCH_WINDOW);
			if (bestEval < eval) {
				bestPt = i;
				bestEval = eval;
			}
		}

		ClassicCounter<String> newWts = new ClassicCounter<String>(initialWts);
    Counters.addInPlace(newWts, direction, chkpts[bestPt]);
    bestWts = newWts;

		return normalize(bestWts);
	}

	enum SmoothingType {
		avg, min
	}

	static final int SEARCH_WINDOW = Integer.parseInt(System.getProperty(
			"SEARCH_WINDOW", "1"));
	static public int MIN_NBEST_OCCURANCES = Integer.parseInt(System.getProperty(
			"MIN_NBEST_OCCURENCES", "5"));
	static final int STARTING_POINTS = Integer.parseInt(System.getProperty(
			"STARTING_POINTS", "5")); //XXX
	static final SmoothingType smoothingType = SmoothingType.valueOf(System
			.getProperty("SMOOTHING_TYPE", "min"));
	static final boolean filterUnreachable = Boolean.parseBoolean(System
			.getProperty("FILTER_UNREACHABLE", "false"));

	static {
		System.err.println();
		System.err.printf("Search Window Size: %d\n", SEARCH_WINDOW);
		System.err.printf("Min nbest occurences: %d\n", MIN_NBEST_OCCURANCES);
		System.err.printf("Starting points: %d\n", STARTING_POINTS);
		System.err.printf("Smoothing Type: %s\n", smoothingType);
		System.err.printf("Min plateau diff: %f\n", MIN_PLATEAU_DIFF);
		System.err.printf("Min objective diff: %f\n", MIN_OBJECTIVE_DIFF);
		System.err.printf("FilterUnreachable?: %b\n", filterUnreachable);
	}

	static double windowSmooth(double[] a, int pos, int window) {
		int strt = Math.max(0, pos - window);
		int nd = Math.min(a.length, pos + window + 1);

		if (smoothingType == SmoothingType.min) {
			int minLoc = strt;
			for (int i = strt + 1; i < nd; i++)
				if (a[i] < a[minLoc])
					minLoc = i;
			return a[minLoc];
		} else if (smoothingType == SmoothingType.avg) {
			double avgSum = 0;
			for (int i = strt; i < nd; i++)
				avgSum += a[i];

			return avgSum / (nd - strt);
		} else {
			throw new RuntimeException();
		}
	}

	/**
	 * Powell's method, but without heuristics for replacement of search
	 * directions. See Press et al Numerical Recipes (1992) pg 415
	 *
	 * Unlike the heuristic version, see powell() below, this variant has
	 * quadratic convergence guarantees. However, note that the heuristic version
	 * should do better in long and narrow valleys.
	 *
	 * @param nbest
	 * @param initialWts
	 * @param emetric
	 * @return
	 */
	@SuppressWarnings( { "unchecked", "deprecation" })
	static public ClassicCounter<String> basicPowell(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric) {
		ClassicCounter<String> wts = initialWts;

		// initialize search directions
		List<ClassicCounter<String>> axisDirs = new ArrayList<ClassicCounter<String>>(
				initialWts.size());
		List<String> featureNames = new ArrayList<String>(wts.keySet());
		Collections.sort(featureNames);
		for (String featureName : featureNames) {
			ClassicCounter<String> dir = new ClassicCounter<String>();
			dir.incrementCount(featureName);
			axisDirs.add(dir);
		}

		// main optimization loop
		ClassicCounter[] p = new ClassicCounter[axisDirs.size()];
		double objValue = evalAtPoint(nbest, wts, emetric); // obj value w/o
		// smoothing
		List<ClassicCounter<String>> dirs = null;
		for (int iter = 0;; iter++) {
			if (iter % p.length == 0) {
				// reset after N iterations to avoid linearly dependent search
				// directions
				System.err.printf("%d: Search direction reset\n", iter);
				dirs = new ArrayList<ClassicCounter<String>>(axisDirs);
			}
			// search along each direction
			p[0] = lineSearch(nbest, wts, dirs.get(0), emetric);
			for (int i = 1; i < p.length; i++) {
				p[i] = lineSearch(nbest, (ClassicCounter<String>) p[i - 1],
						dirs.get(i), emetric);
				dirs.set(i - 1, dirs.get(i)); // shift search directions
			}

			double totalWin = evalAtPoint(nbest, p[p.length-1], emetric) - objValue;
			System.err.printf("%d: totalWin: %e Objective: %e\n", iter, totalWin,
					objValue);
			if (Math.abs(totalWin) < MIN_OBJECTIVE_DIFF)
				break;

			// construct combined direction
			ClassicCounter<String> combinedDir = new ClassicCounter<String>(wts);
      Counters.multiplyInPlace(combinedDir, -1.0);
      combinedDir.addAll(p[p.length - 1]);

			dirs.set(p.length - 1, combinedDir);

			// search along combined direction
			wts = lineSearch(nbest, (ClassicCounter<String>) p[p.length - 1], dirs
					.get(p.length - 1), emetric);
			objValue = evalAtPoint(nbest, wts, emetric);
			System.err.printf("%d: Objective after combined search %e\n", iter,
					objValue);
		}

		return wts;
	}

	@SuppressWarnings( { "deprecation", "unchecked" })
	static public ClassicCounter<String> betterWorseCentroids(
			MosesNBestList nbest, ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric, boolean useCurrentAsWorse,
			boolean useOnlyBetter) {
		List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
				.nbestLists();
		ClassicCounter<String> wts = initialWts;

		for (int iter = 0;; iter++) {
			List<ScoredFeaturizedTranslation<IString, String>> current = transArgmax(
					nbest, wts);
			IncrementalEvaluationMetric<IString, String> incEval = emetric
					.getIncrementalMetric();
			for (ScoredFeaturizedTranslation<IString, String> tran : current) {
				incEval.add(tran);
			}
			ClassicCounter<String> betterVec = new ClassicCounter<String>();
			int betterCnt = 0;
			ClassicCounter<String> worseVec = new ClassicCounter<String>();
			int worseCnt = 0;
			double baseScore = incEval.score();
			System.err.printf("baseScore: %f\n", baseScore);
			int lI = -1;
			for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
				lI++;
				for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
					incEval.replace(lI, tran);
					if (incEval.score() >= baseScore) {
						betterCnt++;
						betterVec.addAll(normalize(summarizedAllFeaturesVector(Arrays
								.asList(tran))));
					} else {
						worseCnt++;
						worseVec.addAll(normalize(summarizedAllFeaturesVector(Arrays
								.asList(tran))));
					}
				}
				incEval.replace(lI, current.get(lI));
			}
			normalize(betterVec);
			if (useCurrentAsWorse)
				worseVec = summarizedAllFeaturesVector(current);
			normalize(worseVec);
			ClassicCounter<String> dir = new ClassicCounter<String>(betterVec);
			if (!useOnlyBetter)
        Counters.subtractInPlace(dir, worseVec);
			normalize(dir);
			System.err.printf("iter: %d\n", iter);
			System.err.printf("Better cnt: %d\n", betterCnt);
			System.err.printf("Worse cnt: %d\n", worseCnt);
			System.err.printf("Better Vec:\n%s\n\n", betterVec);
			System.err.printf("Worse Vec:\n%s\n\n", worseVec);
			System.err.printf("Dir:\n%s\n\n", dir);
			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			System.err.printf("new wts:\n%s\n\n", wts);
			double ssd = wtSsd(wts, newWts);
			wts = newWts;
			System.err.printf("ssd: %f\n", ssd);
			if (ssd < NO_PROGRESS_SSD)
				break;
		}

		return wts;
	}

	static MosesNBestList lastNbest;
	static List<ClassicCounter<String>> lastKMeans;
	static ClassicCounter<String> lastWts;

	@SuppressWarnings( { "unchecked", "deprecation" })
	static public ClassicCounter<String> fullKmeans(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric, int K, boolean clusterToCluster) {
		List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
				.nbestLists();

		List<ClassicCounter<String>> kMeans = new ArrayList<ClassicCounter<String>>(
				K);
		int[] clusterCnts = new int[K];

		if (nbest == lastNbest) {
			kMeans = lastKMeans;
			if (clusterToCluster)
				return lastWts;
		} else {
			int vecCnt = 0;
			for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists)
				for (@SuppressWarnings("unused")
				ScoredFeaturizedTranslation<IString, String> tran : nbestlist)
					vecCnt++;

			List<ClassicCounter<String>> allVecs = new ArrayList<ClassicCounter<String>>(
					vecCnt);
			int[] clusterIds = new int[vecCnt];

			for (int i = 0; i < K; i++)
				kMeans.add(new ClassicCounter<String>());

			// Extract all feature vectors & use them to seed the clusters;
			for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
				for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
					ClassicCounter<String> feats = Counters.L2Normalize(summarizedAllFeaturesVector(Arrays
							.asList(tran)));
					int clusterId = r.nextInt(K);
					clusterIds[kMeans.size()] = clusterId;
					allVecs.add(feats);
					kMeans.get(clusterId).addAll(feats);
					clusterCnts[clusterId]++;
				}
			}

			// normalize cluster vectors
			for (int i = 0; i < K; i++)
        Counters.divideInPlace(kMeans.get(i), clusterCnts[i]);

			// K-means main loop
			for (int changes = vecCnt; changes != 0;) {
				changes = 0;
				int[] newClusterCnts = new int[K];
				List<ClassicCounter<String>> newKMeans = new ArrayList<ClassicCounter<String>>(
						K);
				for (int i = 0; i < K; i++)
					newKMeans.add(new ClassicCounter<String>());

				for (int i = 0; i < vecCnt; i++) {
					ClassicCounter<String> feats = allVecs.get(i);
					double minDist = Double.POSITIVE_INFINITY;
					int bestCluster = -1;
					for (int j = 0; j < K; j++) {
						double dist = 0;
						Set<String> keys = new HashSet<String>(feats.keySet());
						keys.addAll(kMeans.get(j).keySet());
						for (String key : keys) {
							double d = feats.getCount(key) - kMeans.get(j).getCount(key);
							dist += d * d;
						}
						if (dist < minDist) {
							bestCluster = j;
							minDist = dist;
						}
					}
					newKMeans.get(bestCluster).addAll(feats);
					newClusterCnts[bestCluster]++;
					if (bestCluster != clusterIds[i])
						changes++;
					clusterIds[i] = bestCluster;
				}

				// normalize new cluster vectors
				for (int i = 0; i < K; i++)
          Counters.divideInPlace(newKMeans.get(i), newClusterCnts[i]);

				// some output for the user
				System.err.printf("Cluster Vectors:\n");
				for (int i = 0; i < K; i++) {
					System.err.printf(
							"%d:\nCurrent (l2: %f):\n%s\nPrior(l2: %f):\n%s\n\n", i,
							Counters.L2Norm(newKMeans.get(i)), newKMeans.get(i),
							Counters.L2Norm(kMeans.get(i)), kMeans.get(i));
				}
				System.err.printf("\nCluster sizes:\n");
				for (int i = 0; i < K; i++) {
					System.err.printf("\t%d: %d (prior: %d)\n", i, newClusterCnts[i],
							clusterCnts[i]);
				}

				System.err.printf("Changes: %d\n", changes);

				// swap in new clusters
				kMeans = newKMeans;
				clusterCnts = newClusterCnts;
			}
		}

		lastKMeans = kMeans;
		lastNbest = nbest;

		// main optimization loop
		System.err.printf("Begining optimization\n");
		ClassicCounter<String> wts = new ClassicCounter<String>(initialWts);
		if (clusterToCluster) {
			ClassicCounter<String> bestWts = null;
			double bestEval = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < K; i++) {
				if (clusterCnts[i] == 0)
					continue;
				for (int j = i + 1; j < K; j++) {
					if (clusterCnts[j] == 0)
						continue;
					System.err.printf("seach pair: %d->%d\n", j, i);
					ClassicCounter<String> dir = new ClassicCounter<String>(kMeans.get(i));
          Counters.subtractInPlace(dir, kMeans.get(j));
          ClassicCounter<String> eWts = lineSearch(nbest, kMeans.get(j), dir,
							emetric);
					double eval = evalAtPoint(nbest, eWts, emetric);
					if (eval > bestEval) {
						bestEval = eval;
						bestWts = eWts;
					}
					System.err.printf("new eval: %f best eval: %f\n", eval, bestEval);
				}
			}
			System.err.printf("new wts:\n%s\n\n", bestWts);
			wts = bestWts;
		} else {
			for (int iter = 0;; iter++) {
				ClassicCounter<String> newWts = new ClassicCounter<String>(wts);
				for (int i = 0; i < K; i++) {
					List<ScoredFeaturizedTranslation<IString, String>> current = transArgmax(
							nbest, newWts);
					ClassicCounter<String> c = Counters.L2Normalize(summarizedAllFeaturesVector(current));
					ClassicCounter<String> dir = new ClassicCounter<String>(kMeans.get(i));
          Counters.subtractInPlace(dir, c);

          System.err.printf("seach perceptron to cluster: %d\n", i);
					newWts = lineSearch(nbest, newWts, dir, emetric);
					System.err.printf("new eval: %f\n", evalAtPoint(nbest, newWts,
							emetric));
					for (int j = i; j < K; j++) {
						dir = new ClassicCounter<String>(kMeans.get(i));
						if (j != i) {
							System.err.printf("seach pair: %d<->%d\n", j, i);
              Counters.subtractInPlace(dir, kMeans.get(j));
            } else {
							System.err.printf("seach singleton: %d\n", i);
						}

						newWts = lineSearch(nbest, newWts, dir, emetric);
						System.err.printf("new eval: %f\n", evalAtPoint(nbest, newWts,
								emetric));
					}
				}
				System.err.printf("new wts:\n%s\n\n", newWts);
				double ssd = wtSsd(wts, newWts);
				wts = newWts;
				System.err.printf("ssd: %f\n", ssd);
				if (ssd < NO_PROGRESS_SSD)
					break;
			}
		}

		lastWts = wts;
		return wts;
	}

	static enum Cluster3 {
		better, worse, same
	}

	static enum Cluster3LearnType {
		betterWorse, betterSame, betterPerceptron, allDirs
	}

	@SuppressWarnings( { "deprecation", "unchecked" })
	static public ClassicCounter<String> betterWorse3KMeans(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric, Cluster3LearnType lType) {
		List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
				.nbestLists();
		ClassicCounter<String> wts = initialWts;

		for (int iter = 0;; iter++) {
			List<ScoredFeaturizedTranslation<IString, String>> current = transArgmax(
					nbest, wts);
			IncrementalEvaluationMetric<IString, String> incEval = emetric
					.getIncrementalMetric();
			for (ScoredFeaturizedTranslation<IString, String> tran : current) {
				incEval.add(tran);
			}
			ClassicCounter<String> betterVec = new ClassicCounter<String>();
			int betterClusterCnt = 0;
			ClassicCounter<String> worseVec = new ClassicCounter<String>();
			int worseClusterCnt = 0;
			ClassicCounter<String> sameVec = new ClassicCounter<String>(
					Counters.L2Normalize(summarizedAllFeaturesVector(current)));
			int sameClusterCnt = 0;

			double baseScore = incEval.score();
			System.err.printf("baseScore: %f\n", baseScore);
			int lI = -1;
			List<ClassicCounter<String>> allPoints = new ArrayList<ClassicCounter<String>>();
			List<Cluster3> inBetterCluster = new ArrayList<Cluster3>();
			for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
				lI++;
				for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
					incEval.replace(lI, tran);
					ClassicCounter<String> feats = Counters.L2Normalize(summarizedAllFeaturesVector(Arrays
							.asList(tran)));
					if (incEval.score() >= baseScore) {
						betterVec.addAll(feats);
						betterClusterCnt++;
						inBetterCluster.add(Cluster3.better);
					} else {
						worseVec.addAll(feats);
						worseClusterCnt++;
						inBetterCluster.add(Cluster3.worse);
					}
					allPoints.add(feats);
				}
				incEval.replace(lI, current.get(lI));
			}

			System.err.printf("Better cnt: %d\n", betterClusterCnt);
			System.err.printf("Worse cnt: %d\n", worseClusterCnt);

      Counters.multiplyInPlace(betterVec, 1.0 / betterClusterCnt);
      Counters.multiplyInPlace(worseVec, 1.0 / worseClusterCnt);

      System.err.printf("Initial Better Vec:\n%s\n", betterVec);
			System.err.printf("Initial Worse Vec:\n%s\n", worseVec);
			System.err.printf("Initial Same Vec:\n%s\n", sameVec);

			// k-means loop
			Set<String> keys = new HashSet<String>();
			keys.addAll(betterVec.keySet());
			keys.addAll(worseVec.keySet());
			int changes = keys.size();
			for (int clustIter = 0; changes != 0; clustIter++) {
				changes = 0;
				ClassicCounter<String> newBetterVec = new ClassicCounter<String>();
				ClassicCounter<String> newSameVec = new ClassicCounter<String>();
				ClassicCounter<String> newWorseVec = new ClassicCounter<String>();
				betterClusterCnt = 0;
				worseClusterCnt = 0;
				sameClusterCnt = 0;
				for (int i = 0; i < allPoints.size(); i++) {
					ClassicCounter<String> pt = allPoints.get(i);
					double pDist = 0;
					double nDist = 0;
					double sDist = 0;
					for (String k : keys) {
						double pd = betterVec.getCount(k) - pt.getCount(k);
						pDist += pd * pd;
						double nd = worseVec.getCount(k) - pt.getCount(k);
						nDist += nd * nd;
						double sd = sameVec.getCount(k) - pt.getCount(k);
						sDist += sd * sd;
					}

					if (pDist < nDist && pDist < sDist) {
						newBetterVec.addAll(pt);
						betterClusterCnt++;
						if (inBetterCluster.get(i) != Cluster3.better) {
							inBetterCluster.set(i, Cluster3.better);
							changes++;
						}
					} else if (sDist < nDist) {
						newSameVec.addAll(pt);
						sameClusterCnt++;
						if (inBetterCluster.get(i) != Cluster3.same) {
							inBetterCluster.set(i, Cluster3.same);
							changes++;
						}
					} else {
						newWorseVec.addAll(pt);
						worseClusterCnt++;
						if (inBetterCluster.get(i) != Cluster3.worse) {
							inBetterCluster.set(i, Cluster3.worse);
							changes++;
						}
					}
				}
				System.err
						.printf(
								"Cluster Iter: %d Changes: %d BetterClust: %d WorseClust: %d SameClust: %d\n",
								clustIter, changes, betterClusterCnt, worseClusterCnt,
								sameClusterCnt);
        Counters.multiplyInPlace(newBetterVec, 1.0 / betterClusterCnt);
        Counters.multiplyInPlace(newWorseVec, 1.0 / worseClusterCnt);
        Counters.multiplyInPlace(newSameVec, 1.0 / sameClusterCnt);
        betterVec = newBetterVec;
				worseVec = newWorseVec;
				sameVec = newSameVec;
				System.err.printf("Better Vec:\n%s\n", betterVec);
				System.err.printf("Worse Vec:\n%s\n", worseVec);
				System.err.printf("Same Vec:\n%s\n", sameVec);
			}

			ClassicCounter<String> dir = new ClassicCounter<String>();
			if (betterClusterCnt != 0)
				dir.addAll(betterVec);

			switch (lType) {
			case betterPerceptron:
				ClassicCounter<String> c = Counters.L2Normalize(summarizedAllFeaturesVector(current));
        Counters.multiplyInPlace(c, Counters.L2Norm(betterVec));
        Counters.subtractInPlace(dir, c);
        System.out.printf("betterPerceptron");
				System.out.printf("current:\n%s\n\n", c);
				break;
			case betterSame:
				System.out.printf("betterSame");
				System.out.printf("sameVec:\n%s\n\n", sameVec);
				if (sameClusterCnt != 0)
          Counters.subtractInPlace(dir, sameVec);
				break;

			case betterWorse:
				System.out.printf("betterWorse");
				System.out.printf("worseVec:\n%s\n\n", worseVec);
				if (worseClusterCnt != 0)
          Counters.subtractInPlace(dir, worseVec);
				break;
			}

			normalize(dir);
			System.err.printf("iter: %d\n", iter);
			System.err.printf("Better cnt: %d\n", betterClusterCnt);
			System.err.printf("SameClust: %d\n", sameClusterCnt);
			System.err.printf("Worse cnt: %d\n", worseClusterCnt);
			System.err.printf("Better Vec:\n%s\n\n", betterVec);
			System.err.printf("l2: %f\n", Counters.L2Norm(betterVec));
			System.err.printf("Worse Vec:\n%s\n\n", worseVec);
			System.err.printf("Same Vec:\n%s\n\n", sameVec);
			System.err.printf("Dir:\n%s\n\n", dir);

			ClassicCounter<String> newWts;
			if (lType != Cluster3LearnType.allDirs) {
				newWts = lineSearch(nbest, wts, dir, emetric);
			} else {
				ClassicCounter<String> c = Counters.L2Normalize(summarizedAllFeaturesVector(current));
        Counters.multiplyInPlace(c, Counters.L2Norm(betterVec));

        newWts = wts;

				// Better Same
				dir = new ClassicCounter<String>(betterVec);
        Counters.subtractInPlace(dir, sameVec);
        newWts = lineSearch(nbest, newWts, dir, emetric);

				// Better Perceptron
				dir = new ClassicCounter<String>(betterVec);
        Counters.subtractInPlace(dir, c);
        newWts = lineSearch(nbest, newWts, dir, emetric);

				// Better Worse
				dir = new ClassicCounter<String>(betterVec);
        Counters.subtractInPlace(dir, worseVec);
        newWts = lineSearch(nbest, newWts, dir, emetric);

				// Same Worse
				dir = new ClassicCounter<String>(sameVec);
        Counters.subtractInPlace(dir, worseVec);
        newWts = lineSearch(nbest, newWts, dir, emetric);

				// Same Perceptron
				dir = new ClassicCounter<String>(sameVec);
        Counters.subtractInPlace(dir, c);
        newWts = lineSearch(nbest, newWts, dir, emetric);

				// Perceptron Worse
				dir = new ClassicCounter<String>(c);
        Counters.subtractInPlace(dir, worseVec);
        newWts = lineSearch(nbest, newWts, dir, emetric);
			}
			System.err.printf("new wts:\n%s\n\n", newWts);
			double ssd = wtSsd(wts, newWts);
			wts = newWts;
			System.err.printf("ssd: %f\n", ssd);
			if (ssd < NO_PROGRESS_SSD)
				break;
		}

		return wts;
	}

	@SuppressWarnings( { "deprecation", "unchecked" })
	static public ClassicCounter<String> betterWorse2KMeans(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric, boolean perceptron,
			boolean useWts) {
		List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
				.nbestLists();
		ClassicCounter<String> wts = initialWts;

		for (int iter = 0;; iter++) {
			List<ScoredFeaturizedTranslation<IString, String>> current = transArgmax(
					nbest, wts);
			IncrementalEvaluationMetric<IString, String> incEval = emetric
					.getIncrementalMetric();
			for (ScoredFeaturizedTranslation<IString, String> tran : current) {
				incEval.add(tran);
			}
			ClassicCounter<String> betterVec = new ClassicCounter<String>();
			int betterClusterCnt = 0;
			ClassicCounter<String> worseVec = new ClassicCounter<String>();
			int worseClusterCnt = 0;
			double baseScore = incEval.score();
			System.err.printf("baseScore: %f\n", baseScore);
			int lI = -1;
			List<ClassicCounter<String>> allPoints = new ArrayList<ClassicCounter<String>>();
			List<Boolean> inBetterCluster = new ArrayList<Boolean>();
			for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
				lI++;
				for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
					incEval.replace(lI, tran);
					ClassicCounter<String> feats = Counters.L2Normalize(summarizedAllFeaturesVector(Arrays
							.asList(tran)));
					if (incEval.score() >= baseScore) {
						betterVec.addAll(feats);
						betterClusterCnt++;
						inBetterCluster.add(true);
					} else {
						worseVec.addAll(feats);
						worseClusterCnt++;
						inBetterCluster.add(false);
					}
					allPoints.add(feats);
				}
				incEval.replace(lI, current.get(lI));
			}

			System.err.printf("Better cnt: %d\n", betterClusterCnt);
			System.err.printf("Worse cnt: %d\n", worseClusterCnt);

      Counters.multiplyInPlace(betterVec, 1.0 / betterClusterCnt);
      Counters.multiplyInPlace(worseVec, 1.0 / worseClusterCnt);

      System.err.printf("Initial Better Vec:\n%s\n", betterVec);
			System.err.printf("Initial Worse Vec:\n%s\n", worseVec);

			// k-means loop
			Set<String> keys = new HashSet<String>();
			keys.addAll(betterVec.keySet());
			keys.addAll(worseVec.keySet());
			int changes = keys.size();
			for (int clustIter = 0; changes != 0; clustIter++) {
				changes = 0;
				ClassicCounter<String> newBetterVec = new ClassicCounter<String>();
				ClassicCounter<String> newWorseVec = new ClassicCounter<String>();
				betterClusterCnt = 0;
				worseClusterCnt = 0;
				for (int i = 0; i < allPoints.size(); i++) {
					ClassicCounter<String> pt = allPoints.get(i);
					double pDist = 0;
					double nDist = 0;
					for (String k : keys) {
						double pd = betterVec.getCount(k) - pt.getCount(k);
						pDist += pd * pd;
						double nd = worseVec.getCount(k) - pt.getCount(k);
						nDist += nd * nd;
					}
					if (pDist <= nDist) {
						newBetterVec.addAll(pt);
						betterClusterCnt++;
						if (!inBetterCluster.get(i)) {
							inBetterCluster.set(i, true);
							changes++;
						}
					} else {
						newWorseVec.addAll(pt);
						worseClusterCnt++;
						if (inBetterCluster.get(i)) {
							inBetterCluster.set(i, false);
							changes++;
						}
					}
				}
				System.err.printf(
						"Cluster Iter: %d Changes: %d BetterClust: %d WorseClust: %d\n",
						clustIter, changes, betterClusterCnt, worseClusterCnt);
        Counters.multiplyInPlace(newBetterVec, 1.0 / betterClusterCnt);
        Counters.multiplyInPlace(newWorseVec, 1.0 / worseClusterCnt);
        betterVec = newBetterVec;
				worseVec = newWorseVec;
				System.err.printf("Better Vec:\n%s\n", betterVec);
				System.err.printf("Worse Vec:\n%s\n", worseVec);
			}

			ClassicCounter<String> dir = new ClassicCounter<String>();
			if (betterClusterCnt != 0)
				dir.addAll(betterVec);
			if (perceptron) {
				if (useWts) {
					ClassicCounter<String> normWts = new ClassicCounter<String>(wts);
					Counters.L2Normalize(normWts);
          Counters.multiplyInPlace(normWts, Counters.L2Norm(betterVec));
          System.err.printf("Subing wts:\n%s\n", normWts);
          Counters.subtractInPlace(dir, normWts);
          System.err.printf("l2: %f\n", Counters.L2Norm(normWts));
				} else {
					ClassicCounter<String> c = Counters.L2Normalize(summarizedAllFeaturesVector(current));
          Counters.multiplyInPlace(c, Counters.L2Norm(betterVec));
          System.err.printf("Subing current:\n%s\n", c);
          Counters.subtractInPlace(dir, c);
          System.err.printf("l2: %f\n", Counters.L2Norm(c));
				}
			} else {
				if (worseClusterCnt != 0)
          Counters.subtractInPlace(dir, worseVec);
			}
			normalize(dir);
			System.err.printf("iter: %d\n", iter);
			System.err.printf("Better cnt: %d\n", betterClusterCnt);
			System.err.printf("Worse cnt: %d\n", worseClusterCnt);
			System.err.printf("Better Vec:\n%s\n\n", betterVec);
			System.err.printf("l2: %f\n", Counters.L2Norm(betterVec));
			System.err.printf("Worse Vec:\n%s\n\n", worseVec);
			System.err.printf("Dir:\n%s\n\n", dir);
			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			System.err.printf("new wts:\n%s\n\n", wts);
			double ssd = wtSsd(wts, newWts);
			wts = newWts;
			System.err.printf("ssd: %f\n", ssd);
			if (ssd < NO_PROGRESS_SSD)
				break;
		}

		return wts;
	}

	/**
	 * Powell's Method
	 *
	 * A typical implementation - with details originally based on David Chiang's
	 * CMERT 0.5 (as distributed with Moses 1.5.8)
	 *
	 * This implementation appears to be based on that given in Press et al's
	 * Numerical Recipes (1992) pg. 417.
	 *
	 */
	@SuppressWarnings( { "unchecked", "deprecation" })
	static public ClassicCounter<String> powell(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric) {
		ClassicCounter<String> wts = initialWts;

		// initialize search directions
		List<ClassicCounter<String>> dirs = new ArrayList<ClassicCounter<String>>(
				initialWts.size());
		List<String> featureNames = new ArrayList<String>(wts.keySet());
		Collections.sort(featureNames);
		for (String featureName : featureNames) {
			ClassicCounter<String> dir = new ClassicCounter<String>();
			dir.incrementCount(featureName);
			dirs.add(dir);
		}

		// main optimization loop
		ClassicCounter[] p = new ClassicCounter[dirs.size()];
		double objValue = evalAtPoint(nbest, wts, emetric); // obj value w/o
		// smoothing
		for (int iter = 0;; iter++) {
			// search along each direction
			p[0] = lineSearch(nbest, wts, dirs.get(0), emetric);
			double eval = evalAtPoint(nbest, p[0], emetric);
			double biggestWin = Math.max(0, eval - objValue);
			System.err.printf("initial totalWin: %e (%e-%e)\n", biggestWin,
					eval, objValue);
			System.err.printf("eval @ wts: %e\n", evalAtPoint(nbest, wts, emetric));
			System.err.printf("eval @ p[0]: %e\n", evalAtPoint(nbest, p[0], emetric));
			objValue = eval;
			int biggestWinId = 0;
			double totalWin = biggestWin;
			double initObjValue = objValue;
			for (int i = 1; i < p.length; i++) {
				p[i] = lineSearch(nbest, (ClassicCounter<String>) p[i - 1],
						dirs.get(i), emetric);
				eval = evalAtPoint(nbest, p[i], emetric);
				if (Math.max(0, eval - objValue) > biggestWin) {
					biggestWin = eval - objValue;
					biggestWinId = i;
				}
				totalWin += Math.max(0, eval - objValue);
				System.err.printf("\t%d totalWin: %e(%e-%e)\n", i, totalWin,
						eval, objValue);
				objValue = eval;
			}

			System.err.printf("%d: totalWin %e biggestWin: %e objValue: %e\n", iter,
					totalWin, biggestWin, objValue);

			// construct combined direction
			ClassicCounter<String> combinedDir = new ClassicCounter<String>(wts);
      Counters.multiplyInPlace(combinedDir, -1.0);
      combinedDir.addAll(p[p.length - 1]);

			// check to see if we should replace the dominant 'win' direction
			// during the last iteration of search with the combined search direction
			ClassicCounter<String> testPoint = new ClassicCounter<String>(
					p[p.length - 1]);
			testPoint.addAll(combinedDir);
			double testPointEval = evalAtPoint(nbest, testPoint, emetric);
			double extrapolatedWin = testPointEval - objValue;
			System.err.printf("Test Point Eval: %e, extrapolated win: %e\n",
					testPointEval, extrapolatedWin);
			if (extrapolatedWin > 0
					&& 2 * (2 * totalWin - extrapolatedWin)
							* Math.pow(totalWin - biggestWin, 2.0) < Math.pow(
							extrapolatedWin, 2.0)
							* biggestWin) {
				System.err.printf(
						"%d: updating direction %d with combined search dir\n", iter,
						biggestWinId);
				normalize(combinedDir);
				dirs.set(biggestWinId, combinedDir);
			}

			// Search along combined dir even if replacement didn't happen
			wts = lineSearch(nbest, p[p.length - 1], combinedDir, emetric);
			eval = evalAtPoint(nbest, wts, emetric);
			System.err.printf(
					"%d: Objective after combined search %e (gain: %e prior:%e)\n", iter,
					eval - objValue, objValue);

			objValue = eval;

			double finalObjValue = objValue;
			System.err.printf("Actual win: %e (%e-%e)\n", finalObjValue
					- initObjValue, finalObjValue, initObjValue);
			if (Math.abs(initObjValue - finalObjValue) < MIN_OBJECTIVE_DIFF)
				break; // changed to prevent infinite loops
		}

		return wts;
	}

	/**
	 * Optimization algorithm used by cmert included in Moses.
	 *
	 * @author danielcer
	 */
	static public ClassicCounter<String> koehnStyleOptimize(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric) {
		ClassicCounter<String> wts = initialWts;

		for (double oldEval = Double.NEGATIVE_INFINITY;;) {
			ClassicCounter<String> wtsFromBestDir = null;
			double fromBestDirScore = Double.NEGATIVE_INFINITY;
			String bestDirName = null;
			for (String feature : wts.keySet()) {
				if (DEBUG)
					System.out.printf("Searching %s\n", feature);
				ClassicCounter<String> dir = new ClassicCounter<String>();
				dir.incrementCount(feature, 1.0);
				ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
				double eval = evalAtPoint(nbest, newWts, emetric);
				if (DEBUG)
					System.out.printf("\t%e\n", eval);
				if (eval > fromBestDirScore) {
					fromBestDirScore = eval;
					wtsFromBestDir = newWts;
					bestDirName = feature;
				}
			}

			System.out.printf("Best dir: %s Global max along dir: %f\n", bestDirName,
					fromBestDirScore);
			wts = wtsFromBestDir;

			double eval = evalAtPoint(nbest, wts, emetric);
			if (Math.abs(eval - oldEval) < MIN_OBJECTIVE_DIFF)
				break;
			oldEval = eval;
		}

		return wts;
	}

	public static ClassicCounter<String> summarizedAllFeaturesVector(
			List<ScoredFeaturizedTranslation<IString, String>> trans) {
		ClassicCounter<String> sumValues = new ClassicCounter<String>();

		for (ScoredFeaturizedTranslation<IString, String> tran : trans) {
			for (FeatureValue<String> fValue : tran.features) {
				sumValues.incrementCount(fValue.name, fValue.value);
			}
		}

		return sumValues;
	}

	enum SVDOptChoices { exact, evalue }

  static Ptr<DenseMatrix> pU = null;
  static Ptr<DenseMatrix> pV = null;

	static public ClassicCounter<String> svdReducedObj(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric,
			int rank, SVDOptChoices opt) {


		Ptr<Matrix> pFeatDocMat = new Ptr<Matrix>();
		Ptr<Map<String,Integer>> pFeatureIdMap = new Ptr<Map<String,Integer>>();
		System.err.println("Creating feature document matrix");
		nbestListToFeatureDocumentMatrix(nbest, pFeatDocMat, pFeatureIdMap);

    if (pU == null) {
		  pU = new Ptr<DenseMatrix>();
		  pV = new Ptr<DenseMatrix>();
		  System.err.printf("Doing SVD rank: %d\n", rank);
		  ReducedSVD.svd(pFeatDocMat.deref(), pU, pV, rank, false);
		  System.err.println("SVD done.");
    }

		ClassicCounter<String> reducedInitialWts = weightsToReducedWeights(initialWts, pU.deref(), pFeatureIdMap.deref());

		System.err.println("Initial Wts:");
		System.err.println("====================");
		System.err.println(initialWts.toString(35));

		System.err.println("Reduced Initial Wts:");
		System.err.println("====================");
		System.err.println(reducedInitialWts.toString(35));


    System.err.println("Recovered Reduced Initial Wts");
    System.err.println("=============================");
		ClassicCounter<String> recoveredInitialWts =
       reducedWeightsToWeights(reducedInitialWts, pU.deref(), pFeatureIdMap.deref());
    System.err.println(recoveredInitialWts.toString(35));


		MosesNBestList reducedRepNbest = nbestListToDimReducedNbestList(nbest,
      pV.deref());
		ClassicCounter<String> reducedWts;
		switch (opt) {
		case exact:
			System.err.println("Using exact MERT");
			reducedWts = koehnStyleOptimize(reducedRepNbest, reducedInitialWts, emetric);
			break;
		case evalue:
			System.err.println("Using E(Eval) MERT");
			reducedWts = mcmcELossObjectiveCG(reducedRepNbest, reducedInitialWts, emetric);
			break;
		default:
			throw new UnsupportedOperationException();
		}
		System.err.println("Reduced Learned Wts:");
		System.err.println("====================");
		System.err.println(reducedWts.toString(35));


		ClassicCounter<String> recoveredWts = reducedWeightsToWeights(reducedWts, pU.deref(), pFeatureIdMap.deref());
		System.err.println("Recovered Learned Wts:");
		System.err.println("======================");
		System.err.println(recoveredWts.toString(35));

    double wtSsd = wtSsd(reducedInitialWts, reducedWts);
    System.out.printf("reduced wts ssd: %e\n", wtSsd);

    double twtSsd = wtSsd(initialWts, recoveredWts);
    System.out.printf("recovered wts ssd: %e\n", twtSsd);
		return recoveredWts;
	}

	static public ClassicCounter<String> mcmcELossObjectiveCG(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric) {
    ClassicCounter<String> sgdWts;
    System.err.println("Begin SGD optimization\n");
    sgdWts = mcmcELossObjectiveSGD(nbest, initialWts, emetric, 50);
		double eval = evalAtPoint(nbest, sgdWts, emetric);
		double regE = mcmcTightExpectedEval(nbest, sgdWts, emetric);
		double l2wtsSqred = Counters.L2Norm(sgdWts); l2wtsSqred *= l2wtsSqred;
		System.err.printf("SGD final reg objective 0.5||w||_2^2 - C*E(Eval): %e\n",
       -regE);
		System.err.printf("||w||_2^2: %e\n", l2wtsSqred);
		System.err.printf("E(Eval): %e\n", (regE + 0.5*l2wtsSqred)/C);
		System.err.printf("C: %e\n", C);
		System.err.printf("Last eval: %e\n", eval);

    System.err.println("Begin CG optimization\n");
		ObjELossDiffFunction obj = new ObjELossDiffFunction(nbest, sgdWts, emetric);
		//CGMinimizer minim = new CGMinimizer(obj);
		QNMinimizer minim = new QNMinimizer(obj, 10, true);

		/*double[] wtsDense = minim.minimize(obj, 1e-5, obj.initial);
		ClassicCounter<String> wts = new ClassicCounter<String>();
		for (int i = 0; i < wtsDense.length; i++) {
			wts.incrementCount(obj.featureIdsToString.get(i), wtsDense[i]);
		} */
    while (true) {
      try {
		    minim.minimize(obj, 1e-5, obj.initial);
        break;
      } catch (Exception e) {
        continue;
      }
    }
    ClassicCounter<String> wts = obj.getBestWts();

		eval = evalAtPoint(nbest, wts, emetric);
		regE = mcmcTightExpectedEval(nbest, wts, emetric);
		System.err.printf("CG final reg 0.5||w||_2^2 - C*E(Eval): %e\n", -regE);
		l2wtsSqred = Counters.L2Norm(wts); l2wtsSqred *= l2wtsSqred;
		System.err.printf("||w||_2^2: %e\n", l2wtsSqred);
		System.err.printf("E(Eval): %e\n", (regE + 0.5*l2wtsSqred)/C);
		System.err.printf("C: %e\n", C);
		System.err.printf("Last eval: %e\n", eval);
		return wts;
	}

	static public ClassicCounter<String> mcmcELossObjectiveSGD(
   MosesNBestList nbest, ClassicCounter<String> initialWts,
   EvaluationMetric<IString, String> emetric) {
    return mcmcELossObjectiveSGD(nbest, initialWts, emetric,
      DEFAULT_MAX_ITER_SGD);
  }

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> mcmcELossObjectiveSGD(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString, String> emetric, int max_iter) {
		ClassicCounter<String> wts = new ClassicCounter<String>(initialWts);
		double eval = 0;
		double lastExpectedEval = Double.NEGATIVE_INFINITY;
	  double lastObj = Double.NEGATIVE_INFINITY;
    double[] objDiffWin = new double[10];

		for (int iter = 0; iter < max_iter; iter++) {
			MutableDouble expectedEval = new MutableDouble();
			MutableDouble objValue = new MutableDouble();

			ClassicCounter<String> dE = mcmcDerivative(nbest, wts, emetric, expectedEval, objValue);
      Counters.multiplyInPlace(dE, -1.0*lrate);
      wts.addAll(dE);

			double ssd = Counters.L2Norm(dE);
			double expectedEvalDiff = expectedEval.doubleValue() - lastExpectedEval;
      double objDiff = objValue.doubleValue() - lastObj;
      lastObj = objValue.doubleValue();
      objDiffWin[iter % objDiffWin.length] = objDiff;
      double winObjDiff = Double.POSITIVE_INFINITY;
      if (iter > objDiffWin.length) {
         double sum = 0;
         for (int i = 0; i < objDiffWin.length; i++) {
           sum += objDiffWin[i];
         }
         winObjDiff = sum/objDiffWin.length;
      }
			lastExpectedEval = expectedEval.doubleValue();
			eval = evalAtPoint(nbest, wts, emetric);
			System.err.printf("sgd step %d: eval: %e wts ssd: %e E(Eval): %e delta E(Eval): %e obj: %e (delta: %e)\n", iter, eval, ssd, expectedEval.doubleValue(), expectedEvalDiff, objValue.doubleValue(), objDiff);
      if (iter > objDiffWin.length) {
         System.err.printf("objDiffWin: %e\n", winObjDiff);
      }
			if (MIN_OBJECTIVE_CHANGE_SGD > Math.abs(winObjDiff)) break;
		}
		System.err.printf("Last eval: %e\n", eval);
		return wts;
	}

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> mcmcELossDirOptimize(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString, String> emetric) {
		ClassicCounter<String> wts = initialWts;
		double eval;
		for (int iter = 0; ; iter++) {
      double[] tset = {1e-5, 1e-4, 0.001, 0.01, 0.1, 1, 10, 100, 1000, 1e4, 1e5};
			ClassicCounter<String> newWts = new ClassicCounter<String>(wts);
      for (int i = 0; i < tset.length; i++) {
        T = tset[i];
        MutableDouble md = new MutableDouble();
			  ClassicCounter<String> dE = mcmcDerivative(nbest, newWts, emetric,md);
			  newWts = lineSearch(nbest, newWts, dE, emetric);
			  eval = evalAtPoint(nbest, newWts, emetric);
        System.err.printf("T:%e Eval: %.5f E(Eval): %.5f\n", tset[i], eval, md.doubleValue());
      }
			double ssd = wtSsd(wts, newWts);


			eval = evalAtPoint(nbest, newWts, emetric);
			System.err.printf("line opt %d: eval: %e ssd: %e\n", iter, eval, ssd);
			if (ssd < NO_PROGRESS_SSD) break;
			wts = newWts;
		}
		System.err.printf("Last eval: %e\n", eval);
		return wts;
	}

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> perceptronOptimize(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric) {
		List<ScoredFeaturizedTranslation<IString, String>> target = (new HillClimbingMultiTranslationMetricMax<IString, String>(
				emetric)).maximize(nbest);
		ClassicCounter<String> targetFeatures = summarizedAllFeaturesVector(target);
		ClassicCounter<String> wts = initialWts;

		while (true) {
			Scorer<String> scorer = new StaticScorer(wts);
			MultiTranslationMetricMax<IString, String> oneBestSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(
					new ScorerWrapperEvaluationMetric<IString, String>(scorer));
			List<ScoredFeaturizedTranslation<IString, String>> oneBest = oneBestSearch
					.maximize(nbest);
			ClassicCounter<String> dir = summarizedAllFeaturesVector(oneBest);
      Counters.multiplyInPlace(dir, -1.0);
      dir.addAll(targetFeatures);
			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			double ssd = 0;
			for (String k : newWts.keySet()) {
				double diff = wts.getCount(k) - newWts.getCount(k);
				ssd += diff * diff;
			}
			wts = newWts;
			if (ssd < NO_PROGRESS_SSD)
				break;
		}
		return wts;
	}

	@SuppressWarnings( { "deprecation", "unchecked" })
	static public ClassicCounter<String> pointwisePerceptron(
			MosesNBestList nbest, ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric) {
		List<ScoredFeaturizedTranslation<IString, String>> targets = (new HillClimbingMultiTranslationMetricMax<IString, String>(
				emetric)).maximize(nbest);

		ClassicCounter<String> wts = new ClassicCounter<String>(initialWts);

		int changes = 0, totalChanges = 0, iter = 0;

		do {
			for (int i = 0; i < targets.size(); i++) {
				// get current classifier argmax
				Scorer<String> scorer = new StaticScorer(wts);
				GreedyMultiTranslationMetricMax<IString, String> argmaxByScore = new GreedyMultiTranslationMetricMax<IString, String>(
						new ScorerWrapperEvaluationMetric<IString, String>(scorer));
				List<List<ScoredFeaturizedTranslation<IString, String>>> nbestSlice = Arrays
						.asList(nbest.nbestLists().get(i));
				List<ScoredFeaturizedTranslation<IString, String>> current = argmaxByScore
						.maximize(new MosesNBestList(nbestSlice));
				ClassicCounter<String> dir = summarizedAllFeaturesVector(Arrays
						.asList(targets.get(i)));
        Counters.subtractInPlace(dir, summarizedAllFeaturesVector(current));
        ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
				double ssd = wtSsd(wts, newWts);
				System.err.printf(
						"%d.%d - ssd: %e changes(total: %d iter: %d) eval: %f\n", iter, i,
						ssd, totalChanges, changes, evalAtPoint(nbest, newWts, emetric));
				wts = newWts;
				if (ssd >= NO_PROGRESS_SSD) {
					changes++;
					totalChanges++;
				}
			}
			iter++;
		} while (changes != 0);

		return wts;
	}

	static public List<ScoredFeaturizedTranslation<IString, String>> randomBetterTranslations(
			MosesNBestList nbest, ClassicCounter<String> wts,
			EvaluationMetric<IString, String> emetric) {
		return randomBetterTranslations(nbest, transArgmax(nbest, wts), emetric);
	}

	static public List<ScoredFeaturizedTranslation<IString, String>> randomBetterTranslations(
			MosesNBestList nbest,
			List<ScoredFeaturizedTranslation<IString, String>> current,
			EvaluationMetric<IString, String> emetric) {
		List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
				.nbestLists();
		List<ScoredFeaturizedTranslation<IString, String>> trans = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
				nbestLists.size());
		IncrementalEvaluationMetric<IString, String> incEval = emetric
				.getIncrementalMetric();
		for (ScoredFeaturizedTranslation<IString, String> tran : current) {
			incEval.add(tran);
		}
		double baseScore = incEval.score();
		List<List<ScoredFeaturizedTranslation<IString, String>>> betterTrans = new ArrayList<List<ScoredFeaturizedTranslation<IString, String>>>(
				nbestLists.size());
		int lI = -1;
		for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
			lI++;
			betterTrans
					.add(new ArrayList<ScoredFeaturizedTranslation<IString, String>>());
			for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
				incEval.replace(lI, tran);
				if (incEval.score() >= baseScore)
					betterTrans.get(lI).add(tran);
			}
			incEval.replace(lI, current.get(lI));
		}

		for (List<ScoredFeaturizedTranslation<IString, String>> list : betterTrans) {
			trans.add(list.get(r.nextInt(list.size())));
		}

		return trans;
	}

	static public List<ScoredFeaturizedTranslation<IString,String>>
    transEvalArgmax(MosesNBestList nbest,
      EvaluationMetric<IString, String> emetric) {
		  MultiTranslationMetricMax<IString, String> oneBestSearch =
        new HillClimbingMultiTranslationMetricMax<IString, String>(emetric);
		return oneBestSearch.maximize(nbest);
	}


	static public List<ScoredFeaturizedTranslation<IString, String>> transArgmax(
			MosesNBestList nbest, ClassicCounter<String> wts) {
		Scorer<String> scorer = new StaticScorer(wts);
		MultiTranslationMetricMax<IString, String> oneBestSearch = new GreedyMultiTranslationMetricMax<IString, String>(
				new ScorerWrapperEvaluationMetric<IString, String>(scorer));
		return oneBestSearch.maximize(nbest);
	}

	static public List<ScoredFeaturizedTranslation<IString, String>> randomTranslations(
			MosesNBestList nbest) {
		List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
				.nbestLists();
		List<ScoredFeaturizedTranslation<IString, String>> trans = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
				nbestLists.size());

		for (List<ScoredFeaturizedTranslation<IString, String>> list : nbest
				.nbestLists()) {
			trans.add(list.get(r.nextInt(list.size())));
		}

		return trans;
	}

	static public double wtSsd(ClassicCounter<String> oldWts,
			ClassicCounter<String> newWts) {
		double ssd = 0;
		for (String k : newWts.keySet()) {
			double diff = oldWts.getCount(k) - newWts.getCount(k);
			ssd += diff * diff;
		}
		return ssd;
	}

	static public ClassicCounter<String> useRandomNBestPoint(
			MosesNBestList nbest, ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric, boolean better) {
		ClassicCounter<String> wts = initialWts;

		for (int noProgress = 0; noProgress < NO_PROGRESS_LIMIT;) {
			ClassicCounter<String> dir;
			List<ScoredFeaturizedTranslation<IString, String>> rTrans;
			dir = summarizedAllFeaturesVector(rTrans = (better ? randomBetterTranslations(
					nbest, wts, emetric)
					: randomTranslations(nbest)));

			System.err.printf("Random n-best point score: %.5f\n", emetric
					.score(rTrans));
			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			double eval = evalAtPoint(nbest, newWts, emetric);
			double ssd = wtSsd(wts, newWts);
			if (ssd < NO_PROGRESS_SSD)
				noProgress++;
			else
				noProgress = 0;
			System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd,
					noProgress);
			wts = newWts;
		}
		return wts;
	}

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> useRandomPairs(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric) {
		ClassicCounter<String> wts = initialWts;

		for (int noProgress = 0; noProgress < NO_PROGRESS_LIMIT;) {
			ClassicCounter<String> dir;
			List<ScoredFeaturizedTranslation<IString, String>> rTrans1, rTrans2;

      dir = summarizedAllFeaturesVector(rTrans1 = randomTranslations(nbest));
      Counter<String> counter = summarizedAllFeaturesVector(rTrans2 = randomTranslations(nbest));
      Counters.subtractInPlace(dir, counter);

      System.err.printf("Pair scores: %.5f %.5f\n", emetric.score(rTrans1),
					emetric.score(rTrans2));

			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			double eval = evalAtPoint(nbest, newWts, emetric);

			double ssd = 0;
			for (String k : newWts.keySet()) {
				double diff = wts.getCount(k) - newWts.getCount(k);
				ssd += diff * diff;
			}
			System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd,
					noProgress);
			wts = newWts;
			if (ssd < NO_PROGRESS_SSD)
				noProgress++;
			else
				noProgress = 0;
		}
		return wts;
	}

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> useRandomAltPair(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric, boolean forceBetter) {
		ClassicCounter<String> wts = initialWts;

		for (int noProgress = 0; noProgress < NO_PROGRESS_LIMIT;) {
			ClassicCounter<String> dir;
			List<ScoredFeaturizedTranslation<IString, String>> rTrans;
			Scorer<String> scorer = new StaticScorer(wts);

			dir = summarizedAllFeaturesVector(rTrans = (forceBetter ? randomBetterTranslations(
					nbest, wts, emetric)
					: randomTranslations(nbest)));
			MultiTranslationMetricMax<IString, String> oneBestSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(
					new ScorerWrapperEvaluationMetric<IString, String>(scorer));
			List<ScoredFeaturizedTranslation<IString, String>> oneBest = oneBestSearch
					.maximize(nbest);
      Counters.subtractInPlace(dir, summarizedAllFeaturesVector(oneBest));

      System.err.printf("Random alternate score: %.5f \n", emetric
					.score(rTrans));

			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			double eval = evalAtPoint(nbest, newWts, emetric);

			double ssd = 0;
			for (String k : newWts.keySet()) {
				double diff = wts.getCount(k) - newWts.getCount(k);
				ssd += diff * diff;
			}
			System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd,
					noProgress);
			wts = newWts;
			if (ssd < NO_PROGRESS_SSD)
				noProgress++;
			else
				noProgress = 0;
		}
		return wts;
	}

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> cerStyleOptimize(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric) {
		ClassicCounter<String> wts = initialWts;
		double finalEval = 0;
		int iter = 0;
		double initialEval = evalAtPoint(nbest, wts, emetric);
		System.out.printf("Initial (Pre-optimization) Score: %f\n", initialEval);
		for (;; iter++) {
			ClassicCounter<String> dEl = new ClassicCounter<String>();
			IncrementalEvaluationMetric<IString, String> incEvalMetric = emetric
					.getIncrementalMetric();
			ClassicCounter<String> scaledWts = new ClassicCounter<String>(wts);
      Counters.normalize(scaledWts);
      Counters.multiplyInPlace(scaledWts, 0.01);
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
					.nbestLists()) {
				if (incEvalMetric.size() > 0)
					incEvalMetric.replace(incEvalMetric.size() - 1, null);
				incEvalMetric.add(null);
				List<ScoredFeaturizedTranslation<IString, String>> sfTrans = nbestlist;
				List<List<FeatureValue<String>>> featureVectors = new ArrayList<List<FeatureValue<String>>>(
						sfTrans.size());
				double[] us = new double[sfTrans.size()];
				int pos = incEvalMetric.size() - 1;
				for (ScoredFeaturizedTranslation<IString, String> sfTran : sfTrans) {
					incEvalMetric.replace(pos, sfTran);
					us[featureVectors.size()] = incEvalMetric.score();
					featureVectors.add(sfTran.features);
				}

				dEl.addAll(EValueLearningScorer.dEl(new StaticScorer(scaledWts),
						featureVectors, us));
			}

      Counters.normalize(dEl);

      // System.out.printf("Searching %s\n", dEl);
			ClassicCounter<String> wtsdEl = lineSearch(nbest, wts, dEl, emetric);
			double evaldEl = evalAtPoint(nbest, wtsdEl, emetric);

			double eval;
			ClassicCounter<String> oldWts = wts;
			eval = evaldEl;
			wts = wtsdEl;

			double ssd = 0;
			for (String k : wts.keySet()) {
				double diff = oldWts.getCount(k) - wts.getCount(k);
				ssd += diff * diff;
			}

			System.out.printf("Global max along dEl dir(%d): %f wts ssd: %f\n", iter,
					eval, ssd);

			if (ssd < NO_PROGRESS_SSD) {
				finalEval = eval;
				break;
			}
		}

		System.out.printf("Final iters: %d %f->%f\n", iter, initialEval, finalEval);
		return wts;
	}

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> normalize(ClassicCounter<String> wts) {
    Counters.multiplyInPlace(wts, 1.0 / l1norm(wts));
    return wts;
	}

	static public double l1norm(ClassicCounter<String> wts) {
		double sum = 0;
		for (String f : wts) {
			sum += Math.abs(wts.getCount(f));
		}

		return sum;
	}

	static ClassicCounter<String> featureMeans;
	static ClassicCounter<String> featureVars;
	static ClassicCounter<String> featureOccurances;
	static ClassicCounter<String> featureNbestOccurances;

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> cerStyleOptimize2(MosesNBestList nbest,
			ClassicCounter<String> initialWts,
			EvaluationMetric<IString, String> emetric) {
		ClassicCounter<String> wts = new ClassicCounter<String>(initialWts);
		double oldEval = Double.NEGATIVE_INFINITY;
		double finalEval = 0;
		int iter = 0;

		double initialEval = evalAtPoint(nbest, wts, emetric);
		System.out.printf("Initial (Pre-optimization) Score: %f\n", initialEval);

		if (featureMeans == null) {
			featureMeans = new ClassicCounter<String>();
			featureVars = new ClassicCounter<String>();
			featureOccurances = new ClassicCounter<String>();
			featureNbestOccurances = new ClassicCounter<String>();

			int totalVecs = 0;
			for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
					.nbestLists()) {
				Set<String> featureSetNBestList = new HashSet<String>();
				for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
					for (FeatureValue<String> fv : EValueLearningScorer
							.summarizedFeatureVector(trans.features)) {
						featureMeans.incrementCount(fv.name, fv.value);

						if (fv.value != 0) {
							featureOccurances.incrementCount(fv.name);
							featureSetNBestList.add(fv.name);
						}
					}
					totalVecs++;
				}
				for (String f : featureSetNBestList) {
					featureNbestOccurances.incrementCount(f);
				}
			}

      Counters.divideInPlace(featureMeans, totalVecs);

      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
					.nbestLists()) {
				for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
					for (FeatureValue<String> fv : EValueLearningScorer
							.summarizedFeatureVector(trans.features)) {
						double diff = featureMeans.getCount(fv.name) - fv.value;
						featureVars.incrementCount(fv.name, diff * diff);
					}
				}
			}

      Counters.divideInPlace(featureVars, totalVecs - 1);
      System.out.printf("Feature N-best Occurences: (Cut off: %d)\n",
					MIN_NBEST_OCCURANCES);
      for (String w : Counters.toPriorityQueue(featureNbestOccurances)) {
				System.out.printf("%f: %s \n", featureNbestOccurances.getCount(w), w);
			}

			System.out.printf("Feature Occurances\n");
      for (String w : Counters.toPriorityQueue(featureOccurances)) {
				System.out.printf("%f (p %f): %s\n", featureOccurances.getCount(w),
						featureOccurances.getCount(w) / totalVecs, w);
			}

			System.out.printf("Feature Stats (samples: %d):\n", totalVecs);
			List<String> features = new ArrayList<String>(featureMeans.keySet());
			Collections.sort(features);
      for (String fn : Counters.toPriorityQueue(featureVars)) {
				System.out.printf("%s - mean: %.6f var: %.6f sd: %.6f\n", fn,
						featureMeans.getCount(fn), featureVars.getCount(fn), Math
								.sqrt(featureVars.getCount(fn)));
			}
		}

		for (String w : wts) {
			if (featureNbestOccurances.getCount(w) < MIN_NBEST_OCCURANCES) {
				wts.setCount(w, 0);
			}
		}
		normalize(wts);

		for (;; iter++) {
			ClassicCounter<String> dEl = new ClassicCounter<String>();
			double bestEval = Double.NEGATIVE_INFINITY;
			ClassicCounter<String> nextWts = wts;
			List<ClassicCounter<String>> priorSearchDirs = new ArrayList<ClassicCounter<String>>();
			// priorSearchDirs.add(wts);
			for (int i = 0, noProgressCnt = 0; noProgressCnt < 15; i++) {
				boolean atLeastOneParameter = false;
				for (String w : initialWts.keySet()) {
					if (featureNbestOccurances.getCount(w) >= MIN_NBEST_OCCURANCES) {
						dEl.setCount(w, r.nextGaussian()
								* Math.sqrt(featureVars.getCount(w)));
						atLeastOneParameter = true;
					}
				}
				if (!atLeastOneParameter) {
					System.err
							.printf(
									"Error: no feature occurs on %d or more n-best lists - can't optimization.\n",
									MIN_NBEST_OCCURANCES);
					System.err
							.printf("(This probably means your n-best lists are too small)\n");
					System.exit(-1);
				}
				normalize(dEl);
				ClassicCounter<String> searchDir = new ClassicCounter<String>(dEl);
				for (ClassicCounter<String> priorDir : priorSearchDirs) {
					ClassicCounter<String> projOnPrior = new ClassicCounter<String>(
							priorDir);
          Counters.multiplyInPlace(projOnPrior, Counters.dotProduct(priorDir, dEl)
                    / Counters.dotProduct(priorDir, priorDir));
          Counters.subtractInPlace(searchDir, projOnPrior);
        }
				if (Counters.dotProduct(searchDir, searchDir) < NO_PROGRESS_SSD) {
					noProgressCnt++;
					continue;
				}
				priorSearchDirs.add(searchDir);
				if (DEBUG)
					System.out.printf("Searching %s\n", searchDir);
				nextWts = lineSearch(nbest, nextWts, searchDir, emetric);
				double eval = evalAtPoint(nbest, nextWts, emetric);
				if (Math.abs(eval - bestEval) < 1e-9) {
					noProgressCnt++;
				} else {
					noProgressCnt = 0;
				}

				bestEval = eval;
			}

			normalize(nextWts);
			double eval;
			ClassicCounter<String> oldWts = wts;
			eval = bestEval;
			wts = nextWts;

			double ssd = 0;
			for (String k : wts.keySet()) {
				double diff = oldWts.getCount(k) - wts.getCount(k);
				ssd += diff * diff;
			}

			System.out
					.printf(
							"Global max along dEl dir(%d): %f obj diff: %f (*-1+%f=%f) Total Cnt: %f l1norm: %f\n",
							iter, eval, Math.abs(oldEval - eval), MIN_OBJECTIVE_DIFF,
							MIN_OBJECTIVE_DIFF - Math.abs(oldEval - eval), wts.totalCount(),
							l1norm(wts));

			if (Math.abs(oldEval - eval) < MIN_OBJECTIVE_DIFF) {
				finalEval = eval;
				break;
			}

			oldEval = eval;
		}

		System.out.printf("Final iters: %d %f->%f\n", iter, initialEval, finalEval);
		return wts;
	}

	/* not exactly thread safe */
	static IncrementalEvaluationMetric<IString, String> quickIncEval;

	static private void resetQuickEval(EvaluationMetric<IString, String> emetric,
			MosesNBestList nbest) {
		quickIncEval = emetric.getIncrementalMetric();
		int sz = nbest.nbestLists().size();
		for (int i = 0; i < sz; i++) {
			quickIncEval.add(null);
		}
	}

	/**
	 * Specialized evalAt point just for line search
	 *
	 * Previously, profiling revealed that this was a serious hotspot
	 *
	 * @param nbest
	 * @return
	 */

	static private double quickEvalAtPoint(MosesNBestList nbest,
			Set<InterceptIDs> s) {
		if (DEBUG)
			System.out.printf("replacing %d points\n", s.size());
		for (InterceptIDs iId : s) {
			ScoredFeaturizedTranslation<IString, String> trans = nbest.nbestLists()
					.get(iId.list).get(iId.trans);
			quickIncEval.replace(iId.list, trans);
		}
		return quickIncEval.score();
	}

	static public double evalAtPoint(MosesNBestList nbest,
			ClassicCounter<String> wts, EvaluationMetric<IString, String> emetric) {
		Scorer<String> scorer = new StaticScorer(wts);
		IncrementalEvaluationMetric<IString, String> incEval = emetric
				.getIncrementalMetric();
		for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
				.nbestLists()) {
			ScoredFeaturizedTranslation<IString, String> highestScoreTrans = null;
			double highestScore = Double.NEGATIVE_INFINITY;
			for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
				double score = scorer.getIncrementalScore(trans.features);
				if (score > highestScore) {
					highestScore = score;
					highestScoreTrans = trans;
				}
			}
			incEval.add(highestScoreTrans);
		}
		return incEval.score();
	}

	public static ClassicCounter<String> readWeights(String filename) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		ClassicCounter<String> wts = new ClassicCounter<String>();
		for (String line = reader.readLine(); line != null; line = reader
				.readLine()) {
			String[] fields = line.split("\\s+");
			wts.incrementCount(fields[0], Double.parseDouble(fields[1]));
		}
		reader.close();
		return wts;
	}

	@SuppressWarnings("deprecation")
	static void writeWeights(String filename, ClassicCounter<String> wts)
			throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

		ClassicCounter<String> wtsMag = new ClassicCounter<String>();
		for (String w : wts.keySet()) {
			wtsMag.setCount(w, Math.abs(wts.getCount(w)));
		}

    for (String f : Counters.toPriorityQueue(wtsMag).toSortedList()) {
			writer.append(f).append(" ").append(Double.toString(wts.getCount(f)))
					.append("\n");
		}
		writer.close();
	}

	static void displayWeights(ClassicCounter<String> wts) {
		for (String f : wts.keySet()) {
			System.out.printf("%s %g\n", f, wts.getCount(f));
		}
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		if (args.length != 6) {
			System.err
					.printf("Usage:\n\tjava mt.UnsmoothedMERT (eval metric) (nbest list) (local n-best) (file w/initial weights) (reference list); (new weights file)\n");
			System.exit(-1);
		}

		String evalMetric = args[0];
		String nbestListFile = args[1];
		String localNbestListFile = args[2];
		String initialWtsFile = args[3];
		String referenceList = args[4];
		String finalWtsFile = args[5];

		EvaluationMetric<IString, String> emetric = null;
		List<List<Sequence<IString>>> references = Metrics
				.readReferences(referenceList.split(","));
		if (evalMetric.startsWith("ter")) {
      String[] fields = evalMetric.split(":");
      if (fields.length > 1) {
        int beamWidth = Integer.parseInt(fields[1]);
        TERcalc.setBeamWidth(beamWidth);
        System.err.printf("TER beam width set to %d (default: 20)\n",beamWidth);
        if (fields.length > 2) {
          int maxShiftDist = Integer.parseInt(fields[2]);
          TERcalc.setShiftDist(maxShiftDist);
          System.err.printf("TER maximum shift distance set to %d (default: 50)\n",maxShiftDist);
        }
      }
			emetric = new TERMetric<IString, String>(references);
		} else if (evalMetric.endsWith("bleu")) {
			emetric = new BLEUMetric<IString, String>(references);
    } else if (evalMetric.startsWith("bleu-ter")) {
      String[] fields = evalMetric.split(":");
      double terW = 1.0;
      if(fields.length > 1) {
        assert(fields.length == 2);
        terW = Double.parseDouble(fields[1]);
      }
      emetric = new LinearCombinationMetric<IString, String>
           (new double[] {1.0, terW},
            new BLEUMetric<IString, String>(references),
            new TERMetric<IString, String>(references));
    } else {
			System.err.printf("Unrecognized metric: %s\n", evalMetric);
			System.exit(-1);
		}

		ClassicCounter<String> initialWts = readWeights(initialWtsFile);
		MosesNBestList nbest = new MosesNBestList(nbestListFile);
		MosesNBestList localNbest = new MosesNBestList(localNbestListFile,
				nbest.sequenceSelfMap);
		Scorer<String> scorer = new StaticScorer(initialWts);

		List<ScoredFeaturizedTranslation<IString, String>> localNbestArgmax = transArgmax(localNbest, initialWts);
		List<ScoredFeaturizedTranslation<IString, String>> nbestArgmax = transArgmax(nbest, initialWts);
		double localNbestEval = emetric.score(localNbestArgmax);
		double nbestEval    = emetric.score(nbestArgmax);
		System.err.printf("Eval: %f Local eval: %f\n", nbestEval, localNbestEval);
		System.err.printf("Rescoring entries\n");
		// rescore all entries by weights
		System.err.printf("n-best list sizes %d, %d\n", localNbest.nbestLists()
				.size(), nbest.nbestLists().size());
		if (localNbest.nbestLists().size() != nbest.nbestLists().size()) {
			System.err
					.printf(
							"Error incompatible local and cummulative n-best lists, sizes %d != %d\n",
							localNbest.nbestLists().size(), nbest.nbestLists().size());
			System.exit(-1);
		}
		{
			int lI = -1;
			for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
					.nbestLists()) {
				lI++;
				List<ScoredFeaturizedTranslation<IString, String>> lNbestList = localNbest
						.nbestLists().get(lI);
				// If we wanted, we could get the value of minReachableScore by just
				// checking the bottom of the n-best list.
				// However, lets make things robust to the order of the entries in the
				// n-best list being mangled as well as
				// score rounding.
				double minReachableScore = Double.POSITIVE_INFINITY;
				for (ScoredFeaturizedTranslation<IString, String> trans : lNbestList) {
					double score = scorer.getIncrementalScore(trans.features);
					if (score < minReachableScore)
						minReachableScore = score;
				}
				System.err.printf("l %d - min reachable score: %f (orig size: %d)\n",
						lI, minReachableScore, nbestlist.size());
				for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
					trans.score = scorer.getIncrementalScore(trans.features);
					if (filterUnreachable && trans.score > minReachableScore) { // mark as
						// potentially
						// unreachable
						trans.score = Double.NaN;
					}
				}
			}
		}

		System.err.printf("removing anything that might not be reachable\n");
		// remove everything that might not be reachable
		for (int lI = 0; lI < nbest.nbestLists().size(); lI++) {
			List<ScoredFeaturizedTranslation<IString, String>> newList = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
					nbest.nbestLists().get(lI).size());
			List<ScoredFeaturizedTranslation<IString, String>> lNbestList = localNbest
					.nbestLists().get(lI);

			for (ScoredFeaturizedTranslation<IString, String> trans : nbest
					.nbestLists().get(lI)) {
				if (trans.score == trans.score)
					newList.add(trans);
			}
			if (filterUnreachable)
				newList.addAll((Collection) lNbestList); // otherwise entries are
			// already on the n-best list
			nbest.nbestLists().set(lI, newList);
			System.err.printf(
					"l %d - final (filtered) combined n-best list size: %d\n", lI,
					newList.size());
		}

		// add entries for all wts in n-best list
		for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
				.nbestLists()) {
			for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
				for (FeatureValue<String> f : trans.features) {
					initialWts.incrementCount(f.name, 0);
				}
			}
		}

		double initialEval = evalAtPoint(nbest, initialWts, emetric);
		System.out.printf("Initial Eval Score: %e\n", initialEval);
		System.out.printf("Initial Weights:\n==================\n");
		displayWeights(initialWts);
		ClassicCounter<String> bestWts = null;
		double bestObj = Double.POSITIVE_INFINITY;
		long startTime = System.currentTimeMillis();

	  if (System.getProperty("C") != null) {
       C = Double.parseDouble(System.getProperty("C"));
       System.err.printf("Using C %f rather than default of %f\n",
          C, DEFAULT_C);
    }

	  if (System.getProperty("T") != null) {
	  	T = Double.parseDouble(System.getProperty("T"));
	  	System.err.printf("Using T %f rather than default of %f\n", T, DEFAULT_T);
	  }

	  lrate = (C != 0 ? DEFAULT_UNSCALED_L_RATE/C : DEFAULT_UNSCALED_L_RATE);
	  System.out.printf("sgd lrate: %e\n", lrate);
	  double initialObjValue = 0;
	  boolean mcmcObj = (System.getProperty("mcmcELossDirExact") != null ||
		    System.getProperty("mcmcELossSGD") != null ||
		    System.getProperty("mcmcELossCG") != null);

	  if (mcmcObj) {
	  	initialObjValue = mcmcTightExpectedEval(nbest, initialWts, emetric);
	  } else {
	  	initialObjValue = nbestEval;
	  }

		for (int ptI = 0; ptI < STARTING_POINTS; ptI++) {
			ClassicCounter<String> wts;
			if (ptI == 0 && Math.abs(localNbestEval - nbestEval) < MAX_LOCAL_ALL_GAP_WTS_REUSE) {
				System.err.printf("Re-using initial wts, gap: %e", Math.abs(localNbestEval - nbestEval));
				wts = initialWts;
			} else {
				if (ptI == 0) System.err.printf("*NOT* Re-using initial wts, gap: %e max gap: %e", Math.abs(localNbestEval - nbestEval), MAX_LOCAL_ALL_GAP_WTS_REUSE);
				wts = randomWts(initialWts.keySet());
			}
			ClassicCounter<String> newWts;

			if (System.getProperty("useKoehn") != null) {
				System.out.printf("Using koehn\n");
				newWts = koehnStyleOptimize(nbest, wts, emetric);
			} else if (System.getProperty("useBasicPowell") != null) {
				System.out.printf("Using *basic* powell (och)\n");
				newWts = basicPowell(nbest, wts, emetric);
			} else if (System.getProperty("usePowell") != null) {
				System.out.printf("Using powell (och)\n");
				newWts = powell(nbest, wts, emetric);
			} else if (System.getProperty("usePerceptron") != null) {
				System.out.printf("use perceptron\n");
				newWts = perceptronOptimize(nbest, wts, emetric);
			} else if (System.getProperty("useRandomPairs") != null) {
				System.out.printf("using random pairs\n");
				newWts = useRandomPairs(nbest, wts, emetric);
			} else if (System.getProperty("useRandomBetter") != null) {
				System.out.printf("use random better\n");
				newWts = useRandomAltPair(nbest, wts, emetric, true);
			} else if (System.getProperty("useRandomAltPair") != null) {
				System.out.printf("use random alt pair\n");
				newWts = useRandomAltPair(nbest, wts, emetric, false);
			} else if (System.getProperty("useRandomNBestPoint") != null) {
				System.out.printf("use random n-best point\n");
				newWts = useRandomNBestPoint(nbest, wts, emetric, false);
			} else if (System.getProperty("useRandomBetterNBestPoint") != null) {
				System.out.printf("use random better n-best point\n");
				newWts = useRandomNBestPoint(nbest, wts, emetric, true);
			} else if (System.getProperty("betterWorseCentroids") != null) {
				System.out.printf("using better worse centroids\n");
				newWts = betterWorseCentroids(nbest, wts, emetric, false, false);
			} else if (System.getProperty("betterCentroidPerceptron") != null) {
				System.out.printf("using better centroid perceptron\n");
				newWts = betterWorseCentroids(nbest, wts, emetric, true, false);
			} else if (System.getProperty("betterCentroid") != null) {
				System.out.printf("using better centroid\n");
				newWts = betterWorseCentroids(nbest, wts, emetric, false, true);
			} else if (System.getProperty("betterWorseKMeans") != null) {
				System.out.printf("using better worse k-means\n");
				newWts = betterWorse2KMeans(nbest, wts, emetric, false, false);
			} else if (System.getProperty("betterWorseKMeansPerceptron") != null) {
				System.out.printf("using better worse k-means perceptron\n");
				newWts = betterWorse2KMeans(nbest, wts, emetric, true, false);
			} else if (System.getProperty("betterWorseKMeansPerceptronWts") != null) {
				System.out.printf("using better worse k-means wts perceptron\n");
				newWts = betterWorse2KMeans(nbest, wts, emetric, true, true);
			} else if (System.getProperty("3KMeansBetterPerceptron") != null) {
				System.out.printf("Using 3k means better perceptron\n");
				newWts = betterWorse3KMeans(nbest, wts, emetric,
						Cluster3LearnType.betterPerceptron);
			} else if (System.getProperty("3KMeansBetterSame") != null) {
				System.out.printf("Using 3k means better same\n");
				newWts = betterWorse3KMeans(nbest, wts, emetric,
						Cluster3LearnType.betterSame);
			} else if (System.getProperty("3KMeansBetterWorse") != null) {
				System.out.printf("Using 3k means better worse\n");
				newWts = betterWorse3KMeans(nbest, wts, emetric,
						Cluster3LearnType.betterWorse);
			} else if (System.getProperty("3KMeansAllDirs") != null) {
				System.out.printf("Using 3k means All Dirs\n");
				newWts = betterWorse3KMeans(nbest, wts, emetric,
						Cluster3LearnType.allDirs);
			} else if (System.getProperty("fullKMeans") != null) {
				System.out.printf("Using \"full\" k-means k=%s\n", System
						.getProperty("fullKMeans"));
				newWts = fullKmeans(nbest, wts, emetric, Integer.parseInt(System
						.getProperty("fullKMeans")), false);
			} else if (System.getProperty("fullKMeansClusterToCluster") != null) {
				System.out.printf("Using \"full\" k-means (cluster to cluster) k=%s\n",
						System.getProperty("fullKMeansClusterToCluster"));
				newWts = fullKmeans(nbest, wts, emetric, Integer.parseInt(System
						.getProperty("fullKMeansClusterToCluster")), true);
			} else if (System.getProperty("pointwisePerceptron") != null) {
				System.out.printf("Using pointwise Perceptron\n");
			// only run pointwise for the first iteration, as it is very expensive
				newWts = (ptI == 0 ? pointwisePerceptron(nbest, wts, emetric) : wts);
			} else if (System.getProperty("mcmcELossDirExact") != null) {
				System.out.println("using mcmcELossDirExact");
				newWts = mcmcELossDirOptimize(nbest, wts, emetric);
			} else if (System.getProperty("mcmcELossSGD") != null) {
				System.out.println("using mcmcELossSGD");
				newWts = mcmcELossObjectiveSGD(nbest, wts, emetric);
			} else if (System.getProperty("mcmcELossCG") != null) {
				System.out.println("using mcmcELossCG");
				newWts = mcmcELossObjectiveCG(nbest, wts, emetric);
			} else if (System.getProperty("svdExact") != null) {
				int rank = Integer.parseInt(System.getProperty("svdExact"));
				System.out.printf("Using SVD exact, rank: %d\n", rank);
				newWts = svdReducedObj(nbest, wts, emetric, rank, SVDOptChoices.exact);
			} else if (System.getProperty("svdELoss") != null) {
				int rank = Integer.parseInt(System.getProperty("svdELoss"));
				System.out.printf("Using SVD ELoss - mcmc E(Eval), rank: %d\n", rank);
				newWts = svdReducedObj(nbest, wts, emetric, rank, SVDOptChoices.evalue);
			} else {
				System.out.printf("Using cer\n");
				newWts = cerStyleOptimize2(nbest, wts, emetric);
			}

			normalize(newWts);
			double obj = (mcmcObj ? mcmcTightExpectedEval(nbest, newWts, emetric) : -evalAtPoint(nbest, newWts, emetric));
			if (bestObj > obj) {
				bestWts = newWts;
				bestObj = obj;
			}
			System.err.printf("point %d - eval: %e E(eval): %e obj: %e best obj: %e (l1: %f)\n", ptI,
					evalAtPoint(nbest, newWts, emetric),
		  	  mcmcTightExpectedEval(nbest, bestWts, emetric, false),
					obj, bestObj, l1norm(newWts));
		}

		double finalObjValue = (mcmcObj ?
		  	mcmcTightExpectedEval(nbest, bestWts, emetric) :
		  	evalAtPoint(nbest, bestWts, emetric));

		double finalEval = evalAtPoint(nbest, bestWts, emetric);

		System.out.printf("Obj diff: %e\n", Math.abs(initialObjValue - finalObjValue));

		long endTime = System.currentTimeMillis();
		System.out.printf("Optimization Time: %.3f s\n",
				(endTime - startTime) / 1000.0);
		System.out.printf("Final Eval Score: %e->%e\n", initialEval, finalEval);
		System.out.printf("Final Obj: %e->%e\n", initialObjValue, finalObjValue);
		System.out.printf("Final Weights:\n==================\n");
		displayWeights(bestWts);
    double wtSsd = wtSsd(initialWts, bestWts);
    System.out.printf("wts ssd: %e\n", wtSsd);

		writeWeights(finalWtsFile, bestWts);
	}

	static Random r = new Random(8682522807148012L);

	private static ClassicCounter<String> randomWts(Set<String> keySet) {
		ClassicCounter<String> randpt = new ClassicCounter<String>();
		for (String f : keySet) {
			if (generativeFeatures.contains(f)) {
				randpt.setCount(f, r.nextDouble());
			} else {
				randpt.setCount(f, r.nextDouble() * 2 - 1.0);
			}
		}
		return randpt;
	}
}
