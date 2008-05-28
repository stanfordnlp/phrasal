package mt.tune;

import java.io.*;
import java.util.*;

import mt.base.*;
import mt.decoder.util.*;
import mt.metrics.*;

import edu.stanford.nlp.cluster.KMeans;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;


/**
 * Minimum Error Rate Training (MERT) 
 * 
 * Optimization for non smooth error surfaces
 * 
 * @author danielcer
 */
public class UnsmoothedMERT {
	public static final String GENERATIVE_FEATURES_LIST_RESOURCE = "mt/resources/generative.features";
	public static final Set<String> generativeFeatures = SSVMScorer.readGenerativeFeatureList(GENERATIVE_FEATURES_LIST_RESOURCE);
	
	static public final boolean DEBUG = false;
	
	static public final double MIN_PLATEAU_DIFF = 1e-6;
	static public final double MIN_OBJECTIVE_DIFF = 1e-5;
	static public final double MIN_UPDATE_DIFF = 1e-6;
	static class InterceptIDs {
		final int list;
		final int trans;
		InterceptIDs (int list, int trans) {
			this.list = list;
			this.trans = trans;
		}
	}
	
	static public ClassicCounter<String> lineSearch(MosesNBestList nbest, ClassicCounter<String> initialWts, ClassicCounter<String> direction, EvaluationMetric<IString,String> emetric) {
		Scorer<String> currentScorer = new StaticScorer(initialWts);
		Scorer<String> slopScorer = new StaticScorer(direction);
		ArrayList<Double> intercepts = new ArrayList<Double>();
		Map<Double, Set<InterceptIDs>> interceptToIDs = new HashMap<Double, Set<InterceptIDs>>();
		
		{ int lI = -1;
		for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) { lI++;
			// calculate slops/intercepts
			double[] m = new double[nbestlist.size()]; double b[] = new double[nbestlist.size()];
			{int tI = -1; for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) { tI++;
				m[tI] = slopScorer.getIncrementalScore(trans.features);
				b[tI] = currentScorer.getIncrementalScore(trans.features);
			} } 
			
			
			// find -inf*dir canidate
			int firstBest = 0;
			for (int i = 1; i < m.length; i++) {
				if (m[i] < m[firstBest] || (m[i] == m[firstBest] && b[i] > b[firstBest])) {
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
			for (int currentBest = firstBest; currentBest != -1; ) {
				// find next intersection
				double nearestIntercept = Double.POSITIVE_INFINITY;
				int nextBest = -1;
				for (int i = 0; i < m.length; i++) {
					double intercept = (b[currentBest] - b[i])/(m[i]-m[currentBest]); // wow just like middle school
					if (intercept <= interceptLimit+MIN_PLATEAU_DIFF) continue;
					if (intercept < nearestIntercept) { nextBest = i; nearestIntercept = intercept; }
				}
				if (nearestIntercept == Double.POSITIVE_INFINITY) break;
				if (DEBUG) {
					System.out.printf("Nearest intercept: %e Limit: %e\n", nearestIntercept, interceptLimit);
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
		} }
		
		
		// check eval score at each intercept;
		double bestEval = Double.NEGATIVE_INFINITY;
		ClassicCounter<String> bestWts = initialWts;
		if (intercepts.size() == 0) return initialWts;
		intercepts.add(Double.NEGATIVE_INFINITY);
		Collections.sort(intercepts);
		resetQuickEval(emetric, nbest);
		System.out.printf("Checking %d points\n", intercepts.size()-1);
	
		double[] evals  = new double[intercepts.size()];
		double[] chkpts = new double[intercepts.size()];
	
		for (int i = 0; i < intercepts.size(); i++) {
			double chkpt;
			if (i == 0) {
				chkpt = intercepts.get(i+1) - 1.0;			
			} else if (i + 1 == intercepts.size()){
				chkpt = intercepts.get(i) + 1.0;
			} else {
				if (intercepts.get(i) < 0 && intercepts.get(i+1) > 0) {
					chkpt = 0;
				} else {
					chkpt = (intercepts.get(i) + intercepts.get(i+1))/2.0;
				}
			}
			if (DEBUG) System.out.printf("intercept: %f, chkpt: %f\n", intercepts.get(i), chkpt);
			double eval = quickEvalAtPoint(nbest, interceptToIDs.get(intercepts.get(i)));
		
			chkpts[i] = chkpt;
			evals [i] = eval; 
	
			if (DEBUG) {
				System.out.printf("pt(%d): %e eval: %e best: %e\n", i, chkpt, eval, bestEval);
			}
	/*		if (bestEval < eval) {
				Counter<String> newWts = new Counter<String>(initialWts);
				
				newWts.addMultiple(direction, chkpt);
				bestEval = eval;
				bestWts = newWts;
			} */
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
		newWts.addMultiple(direction, chkpts[bestPt]);
		bestWts = newWts;
		
		evilGlobalBestEval = evalAtPoint(nbest, bestWts, emetric);
		return normalize(bestWts);
	}

	enum SmoothingType {avg, min};
	static final int SEARCH_WINDOW = Integer.parseInt(System.getProperty("SEARCH_WINDOW", "1"));
	static public int MIN_NBEST_OCCURANCES = Integer.parseInt(System.getProperty("MIN_NBEST_OCCURENCES", "5"));
	static final int STARTING_POINTS = Integer.parseInt(System.getProperty("STARTING_POINTS", "5"));
	static final SmoothingType smoothingType = SmoothingType.valueOf(System.getProperty("SMOOTHING_TYPE", "min"));
	static final boolean filterUnreachable = Boolean.parseBoolean(System.getProperty("FILTER_UNREACHABLE", "false"));
	
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
		int strt = Math.max(0, pos-window);
		int nd = Math.min(a.length, pos+window+1);
		
		if (smoothingType == SmoothingType.min) {
  		int minLoc = strt;
  		for (int i = strt+1; i < nd; i++) if (a[i] < a[minLoc]) minLoc = i;
  		return a[minLoc];
		} else if (smoothingType == SmoothingType.avg) {
			double avgSum = 0;
			for (int i = strt; i < nd; i++) avgSum += a[i];
			
			return avgSum/(nd-strt);
		} else {
			throw new RuntimeException();
		}
	}
	
	static double evilGlobalBestEval;
	
	/**
	 * Powell's method, but without heuristics for replacement of search directions.
	 * See Press et al Numerical Recipes (1992) pg 415
	 * 
	 * Unlike the heuristic version, see powell() below, this variant has quadratic convergence guarantees.
	 * However, note that the heuristic version should do better in long and narrow valleys.
	 * 
	 * @param nbest
	 * @param initialWts
	 * @param emetric
	 * @return
	 */
  @SuppressWarnings("unchecked")
	static public ClassicCounter<String> basicPowell(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric) {
  	ClassicCounter<String> wts = initialWts;
		
		// initialize search directions
		List<ClassicCounter<String>> axisDirs = new ArrayList<ClassicCounter<String>>(initialWts.size());
		List<String> featureNames = new ArrayList<String>(wts.keySet()); 
		Collections.sort(featureNames);
		for (String featureName : featureNames) {
			ClassicCounter<String> dir = new ClassicCounter<String>();
			dir.incrementCount(featureName);
			axisDirs.add(dir);
		}
		
		
		
		// main optimization loop
		ClassicCounter p[] = new ClassicCounter[axisDirs.size()];
		double objValue = evalAtPoint(nbest, wts, emetric); // obj value w/o smoothing
		List<ClassicCounter<String>> dirs = null;
		for (int iter = 0; ; iter++) {
			if (iter % p.length == 0) {
			  // reset after N iterations to avoid linearly dependent search directions
				System.err.printf("%d: Search direction reset\n", iter);
				dirs = new ArrayList<ClassicCounter<String>>(axisDirs);  
			}
			// search along each direction
			p[0] = lineSearch(nbest, wts, dirs.get(0), emetric);
			for (int i = 1; i < p.length; i++) {
				p[i] = lineSearch(nbest, (ClassicCounter<String>)p[i-1], dirs.get(i), emetric);
				dirs.set(i-1, dirs.get(i)); // shift search directions
			}
			
			double totalWin = evilGlobalBestEval - objValue;
			System.err.printf("%d: totalWin: %e Objective: %e\n", iter, totalWin, objValue);
			if (Math.abs(totalWin) < MIN_OBJECTIVE_DIFF) break;
			
		  // construct combined direction
			ClassicCounter<String> combinedDir = new ClassicCounter<String>(wts);
			combinedDir.multiplyBy(-1.0);
			combinedDir.addAll(p[p.length-1]);
			
			dirs.set(p.length-1, combinedDir);
			
			// search along combined direction
			wts = lineSearch(nbest, (ClassicCounter<String>)p[p.length-1], dirs.get(p.length-1), emetric);			
			objValue = evilGlobalBestEval;
			System.err.printf("%d: Objective after combined search %e\n", iter, objValue);
		}
		
		return wts;		
  }
  
	
	@SuppressWarnings({ "deprecation", "unchecked" })
	static public ClassicCounter<String> betterWorseCentroids(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric, boolean useCurrentAsWorse, boolean useOnlyBetter) {
		List<List<? extends ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest.nbestLists();
	  ClassicCounter<String> wts = initialWts;
	  
		for (int iter = 0; ; iter++) {
  		List<ScoredFeaturizedTranslation<IString, String>> current = transArgmax(nbest, wts);
  	  IncrementalEvaluationMetric<IString, String> incEval = emetric.getIncrementalMetric();  
  	  for (ScoredFeaturizedTranslation<IString, String> tran : current) {
  	     incEval.add(tran);
  	  }
  	  ClassicCounter<String> betterVec = new ClassicCounter<String>(); int betterCnt = 0;
  	  ClassicCounter<String> worseVec = new ClassicCounter<String>();  int worseCnt = 0;
  	  double baseScore = incEval.score();
  	  System.err.printf("baseScore: %f\n", baseScore);
  	  int lI = -1;
  	  for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) { lI++;
  	     for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
  	        incEval.replace(lI, tran);
  	        if (incEval.score() >= baseScore) {
  	        	betterCnt++;
  	        	betterVec.addAll(normalize(summarizedAllFeaturesVector(Arrays.asList(tran))));
  	        } else {
  	        	worseCnt++;
  	        	worseVec.addAll(normalize(summarizedAllFeaturesVector(Arrays.asList(tran))));
  	        }
  	     } 
  	     incEval.replace(lI, current.get(lI));  
  	  }
  	  normalize(betterVec);
  	  if (useCurrentAsWorse) worseVec = summarizedAllFeaturesVector(current);
  	  normalize(worseVec);
  	  ClassicCounter<String> dir = new ClassicCounter<String>(betterVec);
  	  if (!useOnlyBetter) dir.subtractAll(worseVec);
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
  		System.err.printf("ssd: %f\n",ssd);
  		if (ssd < 1e-6) break;  		  		
  	}
		
		return wts;
	}
	
	
	
	static MosesNBestList lastNbest;
	static List<ClassicCounter<String>> lastKMeans;
  static ClassicCounter<String> lastWts;
  
	@SuppressWarnings({ "unchecked", "deprecation" })
	static public ClassicCounter<String> fullKmeans(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric, int K, boolean clusterToCluster) {
		List<List<? extends ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest.nbestLists();		
	
		List<ClassicCounter<String>> kMeans = new ArrayList<ClassicCounter<String>>(K);
		int[] clusterCnts = new int[K];
		
		if (nbest == lastNbest) {
			kMeans = lastKMeans;
			if (clusterToCluster) return lastWts;
		} else {
  		int vecCnt = 0; for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) for (@SuppressWarnings("unused") ScoredFeaturizedTranslation<IString, String> tran : nbestlist) vecCnt++;
  		
  		List<ClassicCounter<String>> allVecs = new ArrayList<ClassicCounter<String>>(vecCnt);
  		int[] clusterIds = new int[vecCnt];
  		
  		for (int i = 0; i < K; i++) kMeans.add(new ClassicCounter<String>());
  		
  		// Extract all feature vectors & use them to seed the clusters;
  		for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) { 
  			for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) { 
          ClassicCounter<String> feats = l2normalize(summarizedAllFeaturesVector(Arrays.asList(tran)));
          int clusterId = r.nextInt(K);
          clusterIds[kMeans.size()] = clusterId;
          allVecs.add(feats);
          kMeans.get(clusterId).addAll(feats);
          clusterCnts[clusterId]++;
  			}
  		}
  		
  		// normalize cluster vectors
  		for (int i = 0; i < K; i++) kMeans.get(i).divideBy(clusterCnts[i]);
  		
  		// K-means main loop
  		for (int changes = vecCnt; changes != 0; ) { changes = 0;
  			int[] newClusterCnts = new int[K];
  			List<ClassicCounter<String>> newKMeans = new ArrayList<ClassicCounter<String>>(K);
  			for (int i = 0; i < K; i++) newKMeans.add(new ClassicCounter<String>());
  		
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
  						dist += d*d;
  					}
  					if (dist < minDist) {
  						bestCluster = j;
  						minDist = dist;
  					}
  				}
  				newKMeans.get(bestCluster).addAll(feats);
  				newClusterCnts[bestCluster]++;
  				if (bestCluster != clusterIds[i]) changes++;
  				clusterIds[i] = bestCluster;
  			}
  
  		  // normalize new cluster vectors
  			for (int i = 0; i < K; i++) newKMeans.get(i).divideBy(newClusterCnts[i]);
  			
  			// some output for the user
  			System.err.printf("Cluster Vectors:\n");
  			for (int i = 0; i < K; i++) {
  				System.err.printf("%d:\nCurrent (l2: %f):\n%s\nPrior(l2: %f):\n%s\n\n", i, 
  						l2norm(newKMeans.get(i)), newKMeans.get(i), l2norm(kMeans.get(i)), kMeans.get(i));
  			}
  			System.err.printf("\nCluster sizes:\n");
  			for (int i = 0; i < K; i++) {
  				System.err.printf("\t%d: %d (prior: %d)\n", i, newClusterCnts[i], clusterCnts[i]);
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
  				if (clusterCnts[i] == 0) continue;
  				for (int j = 0; j < K; j++) {
  					if (i == j) continue;
  					if (clusterCnts[j] == 0) continue;
  					System.err.printf("seach pair: %d->%d\n", j, i);
  					ClassicCounter<String> dir = new ClassicCounter<String>(kMeans.get(i));  				
  					dir.subtractAll(kMeans.get(j));  					
  	  			ClassicCounter<String> eWts = lineSearch(nbest, kMeans.get(j), dir, emetric);
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
  		for (int iter = 0; ; iter++) {
  			ClassicCounter<String> newWts = new ClassicCounter<String>(wts);
  			for (int i = 0; i < K; i++) {
  				List<ScoredFeaturizedTranslation<IString, String>> current = transArgmax(nbest, newWts);
  				ClassicCounter<String> c = l2normalize(summarizedAllFeaturesVector(current));
  				ClassicCounter<String> dir = new ClassicCounter<String>(kMeans.get(i));
  				dir.subtractAll(c);
  				
  				System.err.printf("seach perceptron to cluster: %d\n", i);
  				newWts = lineSearch(nbest, newWts, dir, emetric);
    			System.err.printf("new eval: %f\n", evalAtPoint(nbest, newWts, emetric));
  				for (int j = i; j < K; j++) {
  					dir = new ClassicCounter<String>(kMeans.get(i));
  					if (j != i) {
  						System.err.printf("seach pair: %d<->%d\n", j, i);
  						dir.subtractAll(kMeans.get(j));
  					} else {
  						System.err.printf("seach singleton: %d\n", i);
  					}
  					
  	  			newWts = lineSearch(nbest, newWts, dir, emetric);
  	  			System.err.printf("new eval: %f\n", evalAtPoint(nbest, newWts, emetric));
  				}
  			}
  			System.err.printf("new wts:\n%s\n\n", newWts);
    		double ssd = wtSsd(wts, newWts);
    		wts = newWts;
    		System.err.printf("ssd: %f\n",ssd);
    		if (ssd < 1e-6) break;
  		}
		}
		
		lastWts = wts;
		return wts;
  }
	
	static enum Cluster3 {better, worse, same};
	static enum Cluster3LearnType {betterWorse, betterSame, betterPerceptron, allDirs};
	@SuppressWarnings({ "deprecation", "unchecked" })
	static public ClassicCounter<String> betterWorse3KMeans(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric, Cluster3LearnType lType) {
		List<List<? extends ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest.nbestLists();
	  ClassicCounter<String> wts = initialWts;
	  
	  
		for (int iter = 0; ; iter++) {
  		List<ScoredFeaturizedTranslation<IString, String>> current = transArgmax(nbest, wts);
  	  IncrementalEvaluationMetric<IString, String> incEval = emetric.getIncrementalMetric();  
  	  for (ScoredFeaturizedTranslation<IString, String> tran : current) {
  	     incEval.add(tran);
  	  }
  	  ClassicCounter<String> betterVec = new ClassicCounter<String>(); int betterClusterCnt = 0;
  	  ClassicCounter<String> worseVec = new ClassicCounter<String>(); int worseClusterCnt = 0;
  	  ClassicCounter<String> sameVec = new ClassicCounter<String>(l2normalize(summarizedAllFeaturesVector(current))); int sameClusterCnt = 0;
  	  
  	  double baseScore = incEval.score();
  	  System.err.printf("baseScore: %f\n", baseScore);
  	  int lI = -1;
  	  List<ClassicCounter<String>> allPoints = new ArrayList<ClassicCounter<String>>();
  	  List<Cluster3> inBetterCluster = new ArrayList<Cluster3>();
  	  for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) { lI++;
  	     for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
  	        incEval.replace(lI, tran);
  	        ClassicCounter<String> feats = l2normalize(summarizedAllFeaturesVector(Arrays.asList(tran))); 
  	        if (incEval.score() >= baseScore) {
  	        	betterVec.addAll(feats); betterClusterCnt++;
  	        	inBetterCluster.add(Cluster3.better);
  	        } else {
  	        	worseVec.addAll(feats); worseClusterCnt++;
  	        	inBetterCluster.add(Cluster3.worse);
  	        }
  	        allPoints.add(feats);
  	     } 
  	     incEval.replace(lI, current.get(lI));  
  	  }
  	  
  	  System.err.printf("Better cnt: %d\n", betterClusterCnt);
  	  System.err.printf("Worse cnt: %d\n", worseClusterCnt);
  	  
  	  betterVec.multiplyBy(1.0/betterClusterCnt);
  	  worseVec.multiplyBy(1.0/worseClusterCnt);
  	  
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
  	  			pDist += pd*pd;
  	  			double nd = worseVec.getCount(k) - pt.getCount(k);
  	  			nDist += nd*nd;
  	  			double sd = sameVec.getCount(k) - pt.getCount(k);
  	  			sDist += sd*sd;
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
  	  	System.err.printf("Cluster Iter: %d Changes: %d BetterClust: %d WorseClust: %d SameClust: %d\n", clustIter, changes, betterClusterCnt, worseClusterCnt, sameClusterCnt);
  	  	newBetterVec.multiplyBy(1.0/betterClusterCnt);
  	  	newWorseVec.multiplyBy(1.0/worseClusterCnt);
  	  	newSameVec.multiplyBy(1.0/sameClusterCnt);
  	  	betterVec = newBetterVec;
  	  	worseVec = newWorseVec;
  	  	sameVec = newSameVec;
  	  	System.err.printf("Better Vec:\n%s\n", betterVec);
  	  	System.err.printf("Worse Vec:\n%s\n", worseVec);
  	  	System.err.printf("Same Vec:\n%s\n", sameVec);
  	  }
  	  
  	  
  	  ClassicCounter<String> dir = new ClassicCounter<String>();
  	  if (betterClusterCnt != 0) dir.addAll(betterVec);
  	  
  	  switch (lType) {
  	  case betterPerceptron:
  	  	ClassicCounter<String> c = l2normalize(summarizedAllFeaturesVector(current));
	  		c.multiplyBy(eSize(betterVec));
	  		dir.subtractAll(c);
	  		System.out.printf("betterPerceptron");
	  		System.out.printf("current:\n%s\n\n", c);
  	  	break;  	  	
  	  case betterSame: 
  	  	System.out.printf("betterSame");
  	  	System.out.printf("sameVec:\n%s\n\n", sameVec);
  	  	if (sameClusterCnt != 0) dir.subtractAll(sameVec);
  	  	break;
  	  	
  	  case betterWorse: 
  	  	System.out.printf("betterWorse");
  	  	System.out.printf("worseVec:\n%s\n\n", worseVec);
  	  	if (worseClusterCnt != 0) dir.subtractAll(worseVec);
  	  	break;
  	  }
  	  	
  	  normalize(dir);
  	  System.err.printf("iter: %d\n", iter);
  	  System.err.printf("Better cnt: %d\n", betterClusterCnt);
 	  	System.err.printf("SameClust: %d\n", sameClusterCnt);
  	  System.err.printf("Worse cnt: %d\n", worseClusterCnt);
  	  System.err.printf("Better Vec:\n%s\n\n", betterVec);
  	  System.err.printf("l2: %f\n", eSize(betterVec));
  	  System.err.printf("Worse Vec:\n%s\n\n", worseVec);
  	  System.err.printf("Same Vec:\n%s\n\n", sameVec);  	  
  	  System.err.printf("Dir:\n%s\n\n", dir);
  	  
  		ClassicCounter<String> newWts;
  		if (lType != Cluster3LearnType.allDirs) {
  			newWts = lineSearch(nbest, wts, dir, emetric);
  		} else {
  			ClassicCounter<String> c = l2normalize(summarizedAllFeaturesVector(current));
	  		c.multiplyBy(eSize(betterVec));
	  		
	  		newWts = wts;
	  		
	  		// Better Same
	  		dir = new ClassicCounter<String>(betterVec);
  			dir.subtractAll(sameVec);
  			newWts = lineSearch(nbest, newWts, dir, emetric);
  			
	  		// Better Perceptron
  			dir = new ClassicCounter<String>(betterVec);
  			dir.subtractAll(c);
  			newWts = lineSearch(nbest, newWts, dir, emetric);
  			
  			// Better Worse
  			dir = new ClassicCounter<String>(betterVec);
  			dir.subtractAll(worseVec);
  			newWts = lineSearch(nbest, newWts, dir, emetric);
  			
  			
  			// Same Worse
  			dir = new ClassicCounter<String>(sameVec);
  			dir.subtractAll(worseVec);
  			newWts = lineSearch(nbest, newWts, dir, emetric);
  			
  		  // Same Perceptron
  			dir = new ClassicCounter<String>(sameVec);
  			dir.subtractAll(c);
  			newWts = lineSearch(nbest, newWts, dir, emetric);
  			
  			// Perceptron Worse
  			dir = new ClassicCounter<String>(c);
  			dir.subtractAll(worseVec);
  			newWts = lineSearch(nbest, newWts, dir, emetric);  			  			
  		}
  		System.err.printf("new wts:\n%s\n\n", newWts);
  		double ssd = wtSsd(wts, newWts);
  		wts = newWts;
  		System.err.printf("ssd: %f\n",ssd);
  		if (ssd < 1e-6) break;  		  		
  	}
		
		return wts;
	}
	
	
	@SuppressWarnings({ "deprecation", "unchecked" })
	static public ClassicCounter<String> betterWorse2KMeans(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric, boolean perceptron, boolean useWts) {
		List<List<? extends ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest.nbestLists();
	  ClassicCounter<String> wts = initialWts;
	  
		for (int iter = 0; ; iter++) {
  		List<ScoredFeaturizedTranslation<IString, String>> current = transArgmax(nbest, wts);
  	  IncrementalEvaluationMetric<IString, String> incEval = emetric.getIncrementalMetric();  
  	  for (ScoredFeaturizedTranslation<IString, String> tran : current) {
  	     incEval.add(tran);
  	  }
  	  ClassicCounter<String> betterVec = new ClassicCounter<String>(); int betterClusterCnt = 0;
  	  ClassicCounter<String> worseVec = new ClassicCounter<String>(); int worseClusterCnt = 0;
  	  double baseScore = incEval.score();
  	  System.err.printf("baseScore: %f\n", baseScore);
  	  int lI = -1;
  	  List<ClassicCounter<String>> allPoints = new ArrayList<ClassicCounter<String>>();
  	  List<Boolean> inBetterCluster = new ArrayList<Boolean>();
  	  for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) { lI++;
  	     for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
  	        incEval.replace(lI, tran);
  	        ClassicCounter<String> feats = l2normalize(summarizedAllFeaturesVector(Arrays.asList(tran))); 
  	        if (incEval.score() >= baseScore) {
  	        	betterVec.addAll(feats); betterClusterCnt++;
  	        	inBetterCluster.add(true);
  	        } else {
  	        	worseVec.addAll(feats); worseClusterCnt++;
  	        	inBetterCluster.add(false);
  	        }
  	        allPoints.add(feats);
  	     } 
  	     incEval.replace(lI, current.get(lI));  
  	  }
  	  
  	  System.err.printf("Better cnt: %d\n", betterClusterCnt);
  	  System.err.printf("Worse cnt: %d\n", worseClusterCnt);
  	  
  	  betterVec.multiplyBy(1.0/betterClusterCnt);
  	  worseVec.multiplyBy(1.0/worseClusterCnt);
  	  
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
  	  			pDist += pd*pd;
  	  			double nd = worseVec.getCount(k) - pt.getCount(k);
  	  			nDist += nd*nd;
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
  	  	System.err.printf("Cluster Iter: %d Changes: %d BetterClust: %d WorseClust: %d\n", clustIter, changes, betterClusterCnt, worseClusterCnt);
  	  	newBetterVec.multiplyBy(1.0/betterClusterCnt);
  	  	newWorseVec.multiplyBy(1.0/worseClusterCnt);
  	  	betterVec = newBetterVec;
  	  	worseVec = newWorseVec;
  	  	System.err.printf("Better Vec:\n%s\n", betterVec);
  	  	System.err.printf("Worse Vec:\n%s\n", worseVec);
  	  }
  	  
  	  
  	  ClassicCounter<String> dir = new ClassicCounter<String>();
  	  if (betterClusterCnt != 0) dir.addAll(betterVec);
  	  if (perceptron) {
  	  	if (useWts) { 
  	  		ClassicCounter<String> normWts = new ClassicCounter<String>(wts);
  	  		l2normalize(normWts);
  	  		normWts.multiplyBy(eSize(betterVec));
  	  		System.err.printf("Subing wts:\n%s\n", normWts);
  	  		dir.subtractAll(normWts);
  	  		System.err.printf("l2: %f\n", eSize(normWts));
  	  	} else {
  	  		ClassicCounter<String> c = l2normalize(summarizedAllFeaturesVector(current));
  	  		c.multiplyBy(eSize(betterVec));
  	  		System.err.printf("Subing current:\n%s\n", c);
  	  		dir.subtractAll(c);
  	  		System.err.printf("l2: %f\n", eSize(c));
  	  	}
  	  } else {
  	  	if (worseClusterCnt != 0) dir.subtractAll(worseVec);
  	  }
  	  normalize(dir);
  	  System.err.printf("iter: %d\n", iter);
  	  System.err.printf("Better cnt: %d\n", betterClusterCnt);
  	  System.err.printf("Worse cnt: %d\n", worseClusterCnt);
  	  System.err.printf("Better Vec:\n%s\n\n", betterVec);
  	  System.err.printf("l2: %f\n", eSize(betterVec));
  	  System.err.printf("Worse Vec:\n%s\n\n", worseVec);  	  
  	  System.err.printf("Dir:\n%s\n\n", dir);
  		ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
  		System.err.printf("new wts:\n%s\n\n", wts);
  		double ssd = wtSsd(wts, newWts);
  		wts = newWts;
  		System.err.printf("ssd: %f\n",ssd);
  		if (ssd < 1e-6) break;  		  		
  	}
		
		return wts;
	}
  
	/**
	 * Powell's Method
	 * 
	 * A typical implementation - with details originally based on 
	 * David Chiang's CMERT 0.5 (as distributed with Moses 1.5.8)
	 * 
	 * This implementation appears to be based on that given in 
	 * Press et al's Numerical Recipes (1992) pg. 417.
	 * 
	 */
	@SuppressWarnings("unchecked")
	static public ClassicCounter<String> powell(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric) {
		ClassicCounter<String> wts = initialWts;
				
		// initialize search directions
		List<ClassicCounter<String>> dirs = new ArrayList<ClassicCounter<String>>(initialWts.size());
		List<String> featureNames = new ArrayList<String>(wts.keySet()); 
		Collections.sort(featureNames);
		for (String featureName : featureNames) {
			ClassicCounter<String> dir = new ClassicCounter<String>();
			dir.incrementCount(featureName);
			dirs.add(dir);
		}
		
		// main optimization loop
		ClassicCounter p[] = new ClassicCounter[dirs.size()];
		double objValue = evalAtPoint(nbest, wts, emetric); // obj value w/o smoothing
		for (int iter = 0; ; iter++) {
			// search along each direction
			p[0] = lineSearch(nbest, wts, dirs.get(0), emetric);			
			double biggestWin = Math.max(0, evilGlobalBestEval - objValue);
      System.err.printf("initial totalWin: %e (%e-%e)\n", biggestWin, evilGlobalBestEval, objValue);
      System.err.printf("eval @ wts: %e\n", evalAtPoint(nbest, wts, emetric));
      System.err.printf("eval @ p[0]: %e\n", evalAtPoint(nbest, p[0], emetric));
			objValue = evilGlobalBestEval;
			int biggestWinId = 0;
			double totalWin = biggestWin;
      double initObjValue = objValue;
			for (int i = 1; i < p.length; i++) {
				p[i] = lineSearch(nbest, (ClassicCounter<String>)p[i-1], dirs.get(i), emetric);
				if (Math.max(0, evilGlobalBestEval - objValue) > biggestWin) {
					biggestWin = evilGlobalBestEval - objValue;
					biggestWinId = i;
				}
				totalWin += Math.max(0, evilGlobalBestEval - objValue);
        System.err.printf("\t%d totalWin: %e(%e-%e)\n", i, totalWin, evilGlobalBestEval, objValue);
				objValue = evilGlobalBestEval;
			}
 
			System.err.printf("%d: totalWin %e biggestWin: %e objValue: %e\n", iter, totalWin, biggestWin, objValue);
			
			// construct combined direction
			ClassicCounter<String> combinedDir = new ClassicCounter<String>(wts);
			combinedDir.multiplyBy(-1.0);
			combinedDir.addAll(p[p.length-1]);
			
			// check to see if we should replace the dominant 'win' direction
			// during the last iteration of search with the combined search direction
			ClassicCounter<String> testPoint = new ClassicCounter<String>(p[p.length-1]);
			testPoint.addAll(combinedDir);
			double testPointEval = evalAtPoint(nbest, testPoint, emetric);
			double extrapolatedWin = testPointEval - objValue;
      System.err.printf("Test Point Eval: %e, extrapolated win: %e\n",
           testPointEval, extrapolatedWin);
			if (extrapolatedWin > 0 && 2*(2*totalWin - extrapolatedWin)*Math.pow(totalWin -biggestWin, 2.0) < 
					Math.pow(extrapolatedWin, 2.0)*biggestWin) {
          System.err.printf(
             "%d: updating direction %d with combined search dir\n", 
             iter, biggestWinId);
				  normalize(combinedDir);
				  dirs.set(biggestWinId, combinedDir);
			}
			
			// Search along combined dir even if replacement didn't happen			
			wts = lineSearch(nbest, p[p.length-1], combinedDir, emetric);
      System.err.printf(
        "%d: Objective after combined search %e (gain: %e prior:%e)\n", 
         iter, evilGlobalBestEval, evilGlobalBestEval - objValue, objValue);
			objValue = evilGlobalBestEval;

      double finalObjValue = objValue;
      System.err.printf("Actual win: %e (%e-%e)\n", 
        finalObjValue-initObjValue,
        finalObjValue, initObjValue);
			if (Math.abs(initObjValue - finalObjValue) < MIN_OBJECTIVE_DIFF) break; // changed to prevent infinite loops
		}
		
		return wts;
	}
	
	/**
	 * Optimization algorithm used by cmert included in Moses. 
	 * 
	 * @author danielcer
	 */
	static public ClassicCounter<String> koehnStyleOptimize(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric){
		ClassicCounter<String> wts = initialWts;
		
		for (double oldEval = Double.NEGATIVE_INFINITY; ;) {
			ClassicCounter<String> wtsFromBestDir = null;
			double fromBestDirScore = Double.NEGATIVE_INFINITY;
			String bestDirName = null;
			for (String feature : wts.keySet()) {
				if (DEBUG) System.out.printf("Searching %s\n", feature);
				ClassicCounter<String> dir = new ClassicCounter<String>(); 
				dir.incrementCount(feature, 1.0);
				ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
				double eval = evalAtPoint(nbest, newWts, emetric);
				if (DEBUG) System.out.printf("\t%e\n", eval);
				if (eval > fromBestDirScore) {
					fromBestDirScore = eval;
					wtsFromBestDir = newWts;
					bestDirName = feature;
				}
			}
			
			System.out.printf("Best dir: %s Global max along dir: %f\n", bestDirName, fromBestDirScore);
			wts = wtsFromBestDir;
			
			double eval = evalAtPoint(nbest, wts, emetric);
			if (Math.abs(eval - oldEval) < MIN_OBJECTIVE_DIFF) break;
			oldEval = eval;
		}
		
		return wts;
	}
	
	public static ClassicCounter<String> summarizedAllFeaturesVector(List<ScoredFeaturizedTranslation<IString, String>> trans) {
		ClassicCounter<String> sumValues = new ClassicCounter<String>();
		
		for (ScoredFeaturizedTranslation<IString,String> tran : trans) {			
			for (FeatureValue<String> fValue : tran.features) {
				sumValues.incrementCount(fValue.name, fValue.value);
			}
		}
		
		return sumValues;
	}
	
	static public ClassicCounter<String> perceptronOptimize(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric) {
		List<ScoredFeaturizedTranslation<IString, String>> target = (new HillClimbingMultiTranslationMetricMax<IString, String>(emetric)).maximize(nbest);
		ClassicCounter<String> targetFeatures = summarizedAllFeaturesVector(target);
		ClassicCounter<String> wts = initialWts;
		
		while (true) {
			Scorer<String> scorer = new StaticScorer(wts);
			MultiTranslationMetricMax<IString, String> oneBestSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(new ScorerWrapperEvaluationMetric<IString, String>(scorer));
			List<ScoredFeaturizedTranslation<IString, String>> oneBest = oneBestSearch.maximize(nbest);
			ClassicCounter<String> dir = summarizedAllFeaturesVector(oneBest);
			dir.multiplyBy(-1.0);
			dir.addAll(targetFeatures);
			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			double ssd = 0;
			for (String k : newWts.keySet()) {
				double diff = wts.getCount(k) - newWts.getCount(k);
				ssd += diff*diff;
			}
			wts = newWts;
			if (ssd < 1e-6) break;
		}
		return wts;
	}
	
	static final int NO_PROGRESS_LIMIT = 20;
	static final double NO_PROGRESS_SSD = 1e-6;
	

  static public List<ScoredFeaturizedTranslation<IString, String>> randomBetterTranslations(MosesNBestList nbest, ClassicCounter<String> wts, EvaluationMetric<IString,String> emetric) {
     return randomBetterTranslations(nbest, transArgmax(nbest, wts), emetric);
  }

  static public List<ScoredFeaturizedTranslation<IString, String>> randomBetterTranslations(MosesNBestList nbest, 
      List<ScoredFeaturizedTranslation<IString, String>> current, EvaluationMetric<IString,String> emetric) {
		List<List<? extends ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest.nbestLists();
		List<ScoredFeaturizedTranslation<IString,String>> trans = new ArrayList<ScoredFeaturizedTranslation<IString,String>>(nbestLists.size());
    IncrementalEvaluationMetric<IString, String> incEval = emetric.getIncrementalMetric();  
    for (ScoredFeaturizedTranslation<IString, String> tran : current) {
       incEval.add(tran);
    }
    double baseScore = incEval.score();
    List<List<ScoredFeaturizedTranslation<IString, String>>> betterTrans = 
       new ArrayList<List<ScoredFeaturizedTranslation<IString, String>>>(nbestLists.size());
    int lI = -1;
    for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) { lI++;
       betterTrans.add(new ArrayList<ScoredFeaturizedTranslation<IString, String>>());
       for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          incEval.replace(lI, tran);
          if (incEval.score() >= baseScore) betterTrans.get(lI).add(tran); 
       } 
       incEval.replace(lI, current.get(lI));  
    }

		for (List<? extends ScoredFeaturizedTranslation<IString, String>> list : betterTrans) {
			trans.add(list.get(r.nextInt(list.size())));
		}	

    return trans;
  }

  static public List<ScoredFeaturizedTranslation<IString, String>> transArgmax(MosesNBestList nbest, ClassicCounter<String> wts) {
			Scorer<String> scorer = new StaticScorer(wts);
			MultiTranslationMetricMax<IString, String> oneBestSearch = new GreedyMultiTranslationMetricMax<IString, String>(new ScorerWrapperEvaluationMetric<IString, String>(scorer));
			return oneBestSearch.maximize(nbest);
   }

   
	static public List<ScoredFeaturizedTranslation<IString, String>> randomTranslations(MosesNBestList nbest) {
		List<List<? extends ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest.nbestLists();
		List<ScoredFeaturizedTranslation<IString,String>> trans = new ArrayList<ScoredFeaturizedTranslation<IString,String>>(nbestLists.size());
		
		for (List<? extends ScoredFeaturizedTranslation<IString, String>> list : nbest.nbestLists()) {
			trans.add(list.get(r.nextInt(list.size())));
		}	
		
		return trans;
	}

  static public double wtSsd(ClassicCounter<String> oldWts, ClassicCounter<String> newWts) {
		double ssd = 0;
		for (String k : newWts.keySet()) {
			double diff = oldWts.getCount(k) - newWts.getCount(k);
			ssd += diff*diff;
		}
    return ssd;
  }

  static public ClassicCounter<String> useRandomNBestPoint(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric,
     boolean better) {
		ClassicCounter<String> wts = initialWts;

		for (int noProgress = 0; noProgress < NO_PROGRESS_LIMIT; ) {
			ClassicCounter<String> dir; List<ScoredFeaturizedTranslation<IString, String>> rTrans;
      dir = summarizedAllFeaturesVector(rTrans = (better ? randomBetterTranslations(nbest, wts, emetric) : randomTranslations(nbest)));
     
			System.err.printf("Random n-best point score: %.5f\n", emetric.score(rTrans));
			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			double eval = evalAtPoint(nbest, newWts, emetric);
      double ssd = wtSsd(wts, newWts);
      if (ssd < 1e-6) noProgress++; else noProgress = 0;
			System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd, noProgress);
      wts = newWts; 
    }   
    return wts;
  }	

	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> useRandomPairs(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric) {
		ClassicCounter<String> wts = initialWts;
		
		for (int noProgress = 0; noProgress < NO_PROGRESS_LIMIT; ) {
			ClassicCounter<String> dir; List<ScoredFeaturizedTranslation<IString, String>> rTrans1, rTrans2;
			
			(dir = summarizedAllFeaturesVector(rTrans1 = randomTranslations(nbest))).subtractAll(summarizedAllFeaturesVector(rTrans2 = randomTranslations(nbest)));
			
			System.err.printf("Pair scores: %.5f %.5f\n", emetric.score(rTrans1), emetric.score(rTrans2));
			
			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			double eval = evalAtPoint(nbest, newWts, emetric);
			
			double ssd = 0;
			for (String k : newWts.keySet()) {
				double diff = wts.getCount(k) - newWts.getCount(k);
				ssd += diff*diff;
			}
			System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd, noProgress);
			wts = newWts;
			if (ssd < 1e-6) noProgress++; else noProgress = 0;
		}
		return wts;
	}
	
	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> useRandomAltPair(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric, boolean forceBetter) {
		ClassicCounter<String> wts = initialWts;
		
		for (int noProgress = 0; noProgress < NO_PROGRESS_LIMIT; ) {
			ClassicCounter<String> dir; List<ScoredFeaturizedTranslation<IString, String>> rTrans; Scorer<String> scorer = new StaticScorer(wts);
			
			double currentEval = evalAtPoint(nbest, wts, emetric);
			double rEval;
      dir = summarizedAllFeaturesVector(rTrans = (forceBetter ? randomBetterTranslations(nbest, wts, emetric) : randomTranslations(nbest)));
			rEval = emetric.score(rTrans);
			MultiTranslationMetricMax<IString, String> oneBestSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(new ScorerWrapperEvaluationMetric<IString, String>(scorer));
			List<ScoredFeaturizedTranslation<IString, String>> oneBest = oneBestSearch.maximize(nbest);
			dir.subtractAll(summarizedAllFeaturesVector(oneBest));
			
			System.err.printf("Random alternate score: %.5f \n", emetric.score(rTrans));
			
			ClassicCounter<String> newWts = lineSearch(nbest, wts, dir, emetric);
			double eval = evalAtPoint(nbest, newWts, emetric);
			
			double ssd = 0;
			for (String k : newWts.keySet()) {
				double diff = wts.getCount(k) - newWts.getCount(k);
				ssd += diff*diff;
			}
			System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd, noProgress);
			wts = newWts;
			if (ssd < 1e-6) noProgress++; else noProgress = 0;
		}
		return wts;
	}
	
	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> cerStyleOptimize(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric){
		ClassicCounter<String> wts = initialWts;
	  double finalEval = 0;
		int iter = 0;
		double initialEval = evalAtPoint(nbest, wts, emetric);
		System.out.printf("Initial (Pre-optimization) Score: %f\n", initialEval); 
		for (; ; iter++) {
			ClassicCounter<String> dEl = new ClassicCounter<String>();
			IncrementalEvaluationMetric<IString, String> incEvalMetric = emetric.getIncrementalMetric();
			ClassicCounter<String> scaledWts = new ClassicCounter<String>(wts);
			scaledWts.normalize();
			scaledWts.multiplyBy(0.01);
			for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) {
				if (incEvalMetric.size() > 0) incEvalMetric.replace(incEvalMetric.size()-1, null);
				incEvalMetric.add(null);
				List<? extends ScoredFeaturizedTranslation<IString, String>> sfTrans = nbestlist;
  			List<List<FeatureValue<String>>> featureVectors = new ArrayList<List<FeatureValue<String>>>(sfTrans.size());
   		  double[] us = new double[sfTrans.size()];
   		  int pos = incEvalMetric.size()-1;
  		  for (ScoredFeaturizedTranslation<IString, String> sfTran : sfTrans) {  		  	
  		  	incEvalMetric.replace(pos, sfTran);
  		  	us[featureVectors.size()] = incEvalMetric.score();
  		  	featureVectors.add(sfTran.features);
  		  }
  			
  		  dEl.addAll(EValueLearningScorer.dEl(new StaticScorer(scaledWts), featureVectors, us));
			}
			
			dEl.normalize();
						
			//System.out.printf("Searching %s\n", dEl);
			ClassicCounter<String> wtsdEl = lineSearch(nbest, wts, dEl, emetric);
			double evaldEl = evalAtPoint(nbest, wtsdEl, emetric);
			
			double eval;
			ClassicCounter<String> oldWts = wts;
			eval = evaldEl;
			wts = wtsdEl;
		
			double ssd = 0;
			for (String k : wts.keySet()) {
				double diff = oldWts.getCount(k) - wts.getCount(k);
				ssd += diff*diff;
			}
			
			System.out.printf("Global max along dEl dir(%d): %f wts ssd: %f\n", iter, eval, ssd);
			
			if (ssd < 1e-6) {
				finalEval = eval;
				break; 
			}
		}
		
		System.out.printf("Final iters: %d %f->%f\n", iter, initialEval, finalEval);
		return wts;
	}
	
	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> normalize(ClassicCounter<String> wts) {
			wts.multiplyBy(1.0/l1norm(wts));
			return wts;
	}
	
	static public double l1norm(ClassicCounter<String> wts) {
		double sum = 0;
		for (String f : wts) {
			sum += Math.abs(wts.getCount(f));
		}
		
		return sum;
	}
	
	static public double eSize(ClassicCounter<String> wts) {
		double len = 0;
		for (String k : wts.keySet()) {
			double d = wts.getCount(k);
			len += d*d;
		}
		return Math.sqrt(len);
	}
	
	static public ClassicCounter<String> l2normalize(ClassicCounter<String> wts) {
		wts.multiplyBy(1.0/l2norm(wts));
		return wts;
  }
	
	static public double l2norm(ClassicCounter<String> wts) {
		double sum = 0;
		for (String f : wts) {
			double d = wts.getCount(f);
			sum += d*d;
		}
		
		return Math.sqrt(sum);
	}
	
	
	static ClassicCounter<String> featureMeans;
	static ClassicCounter<String> featureVars;
	static ClassicCounter<String> featureOccurances;
	static ClassicCounter<String> featureNbestOccurances;
	
	@SuppressWarnings("deprecation")
	static public ClassicCounter<String> cerStyleOptimize2(MosesNBestList nbest, ClassicCounter<String> initialWts, EvaluationMetric<IString,String> emetric){
		ClassicCounter<String> wts = new ClassicCounter<String>(initialWts);
		double oldEval = Double.NEGATIVE_INFINITY;
		double finalEval = 0;
		int iter = 0;
		
		
		double initialEval = evalAtPoint(nbest, wts, emetric);		
		System.out.printf("Initial (Pre-optimization) Score: %f\n", initialEval);
	
		if (featureMeans == null) { 
			featureMeans           = new ClassicCounter<String>();
			featureVars            = new ClassicCounter<String>();
			featureOccurances      = new ClassicCounter<String>();
			featureNbestOccurances = new ClassicCounter<String>();
  	  		
  		int totalVecs = 0;
  		for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) {
  			Set<String> featureSetNBestList = new HashSet<String>();
  			for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {				
  				for (FeatureValue<String> fv : EValueLearningScorer.summarizedFeatureVector(trans.features)) {
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
  		
  		featureMeans.divideBy(totalVecs);
  		
  		for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) {
  			for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
  				for (FeatureValue<String> fv : EValueLearningScorer.summarizedFeatureVector(trans.features)) {
  					double diff = featureMeans.getCount(fv.name) -  fv.value;
  					featureVars.incrementCount(fv.name, diff*diff);
  				}
  			}
  		}
  	
  		featureVars.divideBy(totalVecs-1);
  		System.out.printf("Feature N-best Occurences: (Cut off: %d)\n", MIN_NBEST_OCCURANCES);
  		for (String w : featureNbestOccurances.asPriorityQueue()) {
  			System.out.printf("%f: %s \n", featureNbestOccurances.getCount(w), w);
  		}
  		
  		System.out.printf("Feature Occurances\n");
  		for (String w : featureOccurances.asPriorityQueue()) {
  			System.out.printf("%f (p %f): %s\n", featureOccurances.getCount(w), featureOccurances.getCount(w)/totalVecs, w);
  		}
  		
  		System.out.printf("Feature Stats (samples: %d):\n", totalVecs);
  		List<String> features = new ArrayList<String>(featureMeans.keySet());
  		Collections.sort(features);
  		for (String fn : featureVars.asPriorityQueue()) {
  			System.out.printf("%s - mean: %.6f var: %.6f sd: %.6f\n", fn, featureMeans.getCount(fn), featureVars.getCount(fn), Math.sqrt(featureVars.getCount(fn)));
  		} 
		} 
		
		for (String w : wts) {
			if (featureNbestOccurances.getCount(w) < MIN_NBEST_OCCURANCES) {
				wts.setCount(w, 0);
			}
		}
		normalize(wts);
		
		for (; ; iter++) {
			ClassicCounter<String> dEl = new ClassicCounter<String>();
			double bestEval = Double.NEGATIVE_INFINITY;
			ClassicCounter<String> nextWts = wts;
			List<ClassicCounter<String>> priorSearchDirs = new ArrayList<ClassicCounter<String>>();			
			// priorSearchDirs.add(wts);
			for (int i = 0, noProgressCnt = 0; noProgressCnt < 15 ; i++) {
				boolean atLeastOneParameter = false;
  			for (String w : initialWts.keySet()) {  				
  				if (featureNbestOccurances.getCount(w) >= MIN_NBEST_OCCURANCES) {
  					dEl.setCount(w, r.nextGaussian()*Math.sqrt(featureVars.getCount(w)));
  					atLeastOneParameter = true;
  				}
  			}
  			if (!atLeastOneParameter) {
  				System.err.printf("Error: no feature occurs on %d or more n-best lists - can't optimization.\n", MIN_NBEST_OCCURANCES);
  				System.err.printf("(This probably means your n-best lists are too small)\n");
  				System.exit(-1);
  			}
  			normalize(dEl);
  			ClassicCounter<String> searchDir = new ClassicCounter<String>(dEl);
  			for (ClassicCounter<String> priorDir : priorSearchDirs) {
  				ClassicCounter<String> projOnPrior = new ClassicCounter<String>(priorDir);
    			projOnPrior.multiplyBy(Counters.dotProduct(priorDir, dEl)/Counters.dotProduct(priorDir, priorDir));
    			searchDir.subtractAll(projOnPrior);
  			}
  			if (Counters.dotProduct(searchDir, searchDir) < 1e-6) {
  				noProgressCnt++;
  				continue;
  			}
  			priorSearchDirs.add(searchDir);
  			if (DEBUG) System.out.printf("Searching %s\n", searchDir);
  			nextWts = lineSearch(nbest, nextWts, searchDir, emetric);
  			if (Math.abs(evilGlobalBestEval - bestEval) < 1e-9) {
  				noProgressCnt++; 
  			} else {
  				noProgressCnt = 0;
  			}
  			
  			bestEval = evilGlobalBestEval;
			}
			
			normalize(nextWts);
			double eval;
			ClassicCounter<String> oldWts = wts;
			eval = bestEval;
			wts = nextWts;
		
			double ssd = 0;
			for (String k : wts.keySet()) {
				double diff = oldWts.getCount(k) - wts.getCount(k);
				ssd += diff*diff;
			}
			

			System.out.printf("Global max along dEl dir(%d): %f obj diff: %f (*-1+%f=%f) Total Cnt: %f l1norm: %f\n", iter, eval, Math.abs(oldEval - eval),MIN_OBJECTIVE_DIFF, 
					MIN_OBJECTIVE_DIFF - Math.abs(oldEval - eval), wts.totalCount(), l1norm(wts));
			
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
	
	static private void resetQuickEval(EvaluationMetric<IString,String> emetric, MosesNBestList nbest) {
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
	 * @param emetric
	 * @param mbMap
	 * @param pt
	 * @return
	 */
	
	static private double quickEvalAtPoint(MosesNBestList nbest, Set<InterceptIDs> s) {
		if (DEBUG) System.out.printf("replacing %d points\n", s.size());
		for (InterceptIDs iId : s) {
			ScoredFeaturizedTranslation<IString, String> trans = nbest.nbestLists().get(iId.list).get(iId.trans);
			quickIncEval.replace(iId.list, trans);
		}
		return quickIncEval.score();
	}
	
	static public double evalAtPoint(MosesNBestList nbest, ClassicCounter<String> wts, EvaluationMetric<IString,String> emetric) {
		Scorer<String> scorer = new StaticScorer(wts);
		IncrementalEvaluationMetric<IString, String> incEval = emetric.getIncrementalMetric();
		for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) {
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
	
	static ClassicCounter<String> readWeights(String filename) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		ClassicCounter<String> wts = new ClassicCounter<String>();
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			String[] fields = line.split("\\s+");
			wts.incrementCount(fields[0], Double.parseDouble(fields[1]));
		}
		reader.close();
		return wts;
	}
	
	@SuppressWarnings("deprecation")
	static void writeWeights(String filename, ClassicCounter<String> wts) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
		
	 ClassicCounter<String> wtsMag = new ClassicCounter<String>();
	 for (String w : wts.keySet()) {
		 wtsMag.setCount(w, Math.abs(wts.getCount(w)));
	 }

		for (String f : wtsMag.asPriorityQueue().toSortedList()) {
			writer.append(f).append(" ").append(Double.toString(wts.getCount(f))).append("\n");
		}
		writer.close();
	}
	
	static void displayWeights(ClassicCounter<String> wts) {
		for (String f : wts.keySet()) { System.out.printf("%s %f\n", f, wts.getCount(f)); }
	}
	
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception{
		if (args.length != 6) {
			System.err.printf("Usage:\n\tjava mt.UnsmoothedMERT (eval metric) (nbest list) (local n-best) (file w/initial weights) (reference list); (new weights file)\n");
			System.exit(-1);
		}
	
		String evalMetric = args[0];
		String nbestListFile = args[1];
		String localNbestListFile = args[2];
		String initialWtsFile = args[3];
		String referenceList = args[4];
		String finalWtsFile = args[5];
	
		EvaluationMetric<IString,String> emetric = null;
		List<List<Sequence<IString>>> references = Metrics.readReferences(referenceList.split(","));
		if (evalMetric.equals("ter")) {
			emetric = new TERMetric<IString, String>(references);
		} else if (evalMetric.endsWith("bleu")) {
			emetric = new BLEUMetric<IString, String>(references);
		} else {
			System.err.printf("Unrecognized metric: %s\n", evalMetric);
			System.exit(-1);
		}
		
		
		ClassicCounter<String> initialWts = readWeights(initialWtsFile);
		MosesNBestList nbest = new MosesNBestList(nbestListFile);
		MosesNBestList localNbest = new MosesNBestList(localNbestListFile, nbest.sequenceSelfMap);
		Scorer<String> scorer = new StaticScorer(initialWts);
		
		System.err.printf("Rescoring entries\n");
		// rescore all entries by weights
		System.err.printf("n-best list sizes %d, %d\n", localNbest.nbestLists().size(), nbest.nbestLists().size());
		if (localNbest.nbestLists().size() != nbest.nbestLists().size()) {
			System.err.printf("Error incompatible local and cummulative n-best lists, sizes %d != %d\n", localNbest.nbestLists().size(), nbest.nbestLists().size());
			System.exit(-1);
		}
		{ int lI = -1;
		for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) { lI++;
		  List<? extends ScoredFeaturizedTranslation<IString, String>> lNbestList = localNbest.nbestLists().get(lI);
		  // If we wanted, we could get the value of minReachableScore by just checking the bottom of the n-best list.
		  // However, lets make things robust to the order of the entries in the n-best list being mangled as well as 
		  // score rounding. 
		  double minReachableScore = Double.POSITIVE_INFINITY;
		  for (ScoredFeaturizedTranslation<IString,String> trans : lNbestList) {
		  	double score = scorer.getIncrementalScore(trans.features);
		  	if (score < minReachableScore) minReachableScore = score;
		  }
		  System.err.printf("l %d - min reachable score: %f (orig size: %d)\n", lI, minReachableScore, nbestlist.size());
			for (ScoredFeaturizedTranslation<IString, String> trans : nbestlist) {
				trans.score =  scorer.getIncrementalScore(trans.features);
				if (filterUnreachable && trans.score > minReachableScore) { // mark as potentially unreachable
					trans.score = Double.NaN;
				}
			}
		} }
		
		
		System.err.printf("removing anything that might not be reachable\n");
		// remove everything that might not be reachable
		for (int lI = 0; lI < nbest.nbestLists().size(); lI++) {
			List<ScoredFeaturizedTranslation<IString, String>> newList = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(nbest.nbestLists().get(lI).size());
			List<? extends ScoredFeaturizedTranslation<IString, String>> lNbestList = localNbest.nbestLists().get(lI);
			
			for (ScoredFeaturizedTranslation<IString, String> trans : nbest.nbestLists().get(lI)) {
				if (trans.score == trans.score) newList.add(trans);
			}
			if (filterUnreachable) newList.addAll((Collection) lNbestList); // otherwise entries are already on the n-best list
			nbest.nbestLists().set(lI, newList);
			System.err.printf("l %d - final (filtered) combined n-best list size: %d\n", lI, newList.size());
		}
		
		// add entries for all wts in n-best list
		for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest.nbestLists()) {
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
		double bestEval = Double.NEGATIVE_INFINITY;
		long startTime = System.currentTimeMillis();
		for (int ptI = 0; ptI < STARTING_POINTS; ptI++) {
			ClassicCounter<String> wts;
			if (ptI == 0) wts = initialWts;
			else wts = randomWts(initialWts.keySet());
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
			}	else if (System.getProperty("useRandomAltPair") != null) {
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
      }  else if (System.getProperty("betterWorseKMeansPerceptron") != null) {
      	System.out.printf("using better worse k-means perceptron\n");
      	newWts = betterWorse2KMeans(nbest, wts, emetric, true, false);
      } else if (System.getProperty("betterWorseKMeansPerceptronWts") != null) {
      	System.out.printf("using better worse k-means wts perceptron\n");
      	newWts = betterWorse2KMeans(nbest, wts, emetric, true, true);
      } else if (System.getProperty("3KMeansBetterPerceptron") != null) {
      	System.out.printf("Using 3k means better perceptron\n");
      	newWts = betterWorse3KMeans(nbest, wts, emetric, Cluster3LearnType.betterPerceptron);
      } else if (System.getProperty("3KMeansBetterSame") != null) {
      	System.out.printf("Using 3k means better same\n");
      	newWts = betterWorse3KMeans(nbest, wts, emetric, Cluster3LearnType.betterSame);
      } else if (System.getProperty("3KMeansBetterWorse") != null) {
      	System.out.printf("Using 3k means better worse\n");
      	newWts = betterWorse3KMeans(nbest, wts, emetric, Cluster3LearnType.betterWorse);
      } else if (System.getProperty("3KMeansAllDirs") != null) {
      	System.out.printf("Using 3k means All Dirs\n");
      	newWts = betterWorse3KMeans(nbest, wts, emetric, Cluster3LearnType.allDirs);
      } else if (System.getProperty("fullKMeans") != null) {
      	System.out.printf("Using \"full\" k-means k=%s\n", System.getProperty("fullKMeans"));
      	newWts = fullKmeans(nbest, wts, emetric, Integer.parseInt(System.getProperty("fullKMeans")), false);
      } else if (System.getProperty("fullKMeansClusterToCluster") != null) {
      	System.out.printf("Using \"full\" k-means k=%s\n", System.getProperty("fullKMeansClusterToCluster"));
      	newWts = fullKmeans(nbest, wts, emetric, Integer.parseInt(System.getProperty("fullKMeansClusterToCluster")), true);
      } else {
				System.out.printf("Using cer\n");
				newWts = cerStyleOptimize2(nbest, wts, emetric);
			}
			
			normalize(newWts);
			double eval = evalAtPoint(nbest, newWts, emetric);
			if (bestEval < eval) {
				bestWts = newWts;
				bestEval = eval;
			}
			System.err.printf("point %d - eval: %e best eval: %e (l1: %f)\n", ptI, eval, bestEval, l1norm(newWts));
		}
		long endTime = System.currentTimeMillis();
		System.out.printf("Optimization Time: %.3f s\n", (endTime-startTime)/1000.0);
		System.out.printf("Final Eval Score: %e->%e\n", initialEval, bestEval);
		System.out.printf("Final Weights:\n==================\n");
		if (bestEval <= initialEval + MIN_UPDATE_DIFF) {
			bestWts = initialWts;			
		}
		displayWeights(bestWts);
		
		writeWeights(finalWtsFile, bestWts);
	}

	static Random r = new Random(8682522807148012L);
	
	private static ClassicCounter<String> randomWts(Set<String> keySet) {
		ClassicCounter<String> randpt = new ClassicCounter<String>();
		for (String f : keySet) {
			if (generativeFeatures.contains(f)) {
				randpt.setCount(f, r.nextDouble());
			} else {
				randpt.setCount(f, r.nextDouble()*2-1.0);
			}
		}
		return randpt;
	}
}
