package edu.stanford.nlp.mt.tune;

import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.MutableDouble;
import edu.stanford.nlp.util.MutableInteger;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Ptr;
import edu.stanford.nlp.svd.ReducedSVD;

import edu.stanford.nlp.optimization.Function;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.HasInitial;
import edu.stanford.nlp.math.ArrayMath;

import edu.stanford.nlp.mt.base.MosesNBestList;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;
import edu.stanford.nlp.mt.metrics.ScorerWrapperEvaluationMetric;
import edu.stanford.nlp.mt.decoder.util.StaticScorer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.feat.WordPenaltyFeaturizer;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.sparse.CompRowMatrix;

/**
 * @author Michel Galley
 */
public class NBestOptimizerFactory {

  private NBestOptimizerFactory() {}

  public static NBestOptimizer factory(String name, MERT mert) {

    if(name.contains("+") || name.endsWith("~")) {
      boolean loop = name.endsWith("~");
      name = name.replaceAll("~","");
      System.err.println("seq: loop: "+loop);
      List<NBestOptimizer> opts = new ArrayList<NBestOptimizer>();
      for(String el : name.split("\\+")) {
        opts.add(factory(el, mert));
        System.err.println("seq: adding "+el);
      }
      return new SequenceOptimizer(mert, opts, loop);
    }

    if (name.equalsIgnoreCase("ll")) {
       return new LogLinearOptimizer(mert);
    } else if (name.equalsIgnoreCase("cer")) {
      return new CerStyleOptimizer(mert);
    } else if (name.equalsIgnoreCase("koehn")) {
      return new KoehnStyleOptimizer(mert);
    } else if (name.equalsIgnoreCase("basicPowell")) {
      return new BasicPowellOptimizer(mert);
    } else if (name.equalsIgnoreCase("powell")) {
      return new PowellOptimizer(mert);
    } else if (name.startsWith("simplex")) {
      String[] els = name.split(":");
      int iter = els.length == 2 ? Integer.parseInt(els[1]) : 1;
      return new DownhillSimplexOptimizer(mert, iter, false);
    } else if (name.startsWith("randomSimplex")) {
      String[] els = name.split(":");
      int iter = els.length == 2 ? Integer.parseInt(els[1]) : 1;
      return new DownhillSimplexOptimizer(mert, iter, true);
    } else if (name.equalsIgnoreCase("length")) {
      return new LineSearchOptimizer(mert);
    } else if (name.equalsIgnoreCase("perceptron")) {
      return new PerceptronOptimizer(mert);
    } else if (name.equalsIgnoreCase("randomPairs")) {
      return new RandomPairs(mert);
    } else if (name.equalsIgnoreCase("randomBetter")) {
      return new RandomAltPairs(mert, true);
    } else if (name.equalsIgnoreCase("randomAltPair")) {
      return new RandomAltPairs(mert, false);
    } else if (name.equalsIgnoreCase("randomNBestPoint")) {
      return new RandomNBestPoint(mert, false);
    } else if (name.equalsIgnoreCase("randomBetterNBestPoint")) {
      return new RandomNBestPoint(mert, true);
    } else if (name.equalsIgnoreCase("betterWorseCentroids")) {
      return new BetterWorseCentroids(mert, false, false);
    } else if (name.equalsIgnoreCase("betterCentroidPerceptron")) {
      return new BetterWorseCentroids(mert, true, false);
    } else if (name.equalsIgnoreCase("betterCentroid")) {
      return new BetterWorseCentroids(mert, false, true);
    } else if (name.equalsIgnoreCase("betterWorseKMeans")) {
      return new BetterWorse2KMeans(mert, false, false);
    } else if (name.equalsIgnoreCase("betterWorseKMeansPerceptron")) {
      return new BetterWorse2KMeans(mert, true, false);
    } else if (name.equalsIgnoreCase("betterWorseKMeansPerceptronWts")) {
      return new BetterWorse2KMeans(mert, true, true);
    } else if (name.equalsIgnoreCase("3KMeansBetterPerceptron")) {
      return new BetterWorse3KMeans(mert, BetterWorse3KMeans.Cluster3LearnType.betterPerceptron);
    } else if (name.equalsIgnoreCase("3KMeansBetterSame")) {
      return new BetterWorse3KMeans(mert, BetterWorse3KMeans.Cluster3LearnType.betterSame);
    } else if (name.equalsIgnoreCase("3KMeansBetterWorse")) {
      return new BetterWorse3KMeans(mert, BetterWorse3KMeans.Cluster3LearnType.betterWorse);
    } else if (name.equalsIgnoreCase("3KMeansAllDirs")) {
      return new BetterWorse3KMeans(mert, BetterWorse3KMeans.Cluster3LearnType.allDirs);
    } else if (name.equalsIgnoreCase("fullKMeans")) {
      return new FullKMeans(mert, Integer.parseInt(System.getProperty("fullKMeans")), false);
    } else if (name.equalsIgnoreCase("fullKMeansClusterToCluster")) {
      return new FullKMeans(mert, Integer.parseInt(System.getProperty("fullKMeansClusterToCluster")), true);
    } else if (name.equalsIgnoreCase("pointwisePerceptron")) {
      return new PointwisePerceptron(mert);
    } else if (name.equalsIgnoreCase("mcmcELossDirExact")) {
      return new MCMCELossDirOptimizer(mert);
    } else if (name.equalsIgnoreCase("mcmcELossSGD")) {
      return new MCMCELossObjectiveSGD(mert);
    } else if (name.equalsIgnoreCase("mcmcELossCG")) {
      return new MCMCELossObjectiveCG(mert);
    } else if (name.equalsIgnoreCase("svdExact")) {
      int rank = Integer.parseInt(System.getProperty("svdExact"));
      return new SVDReducedObj(mert, rank, SVDReducedObj.SVDOptChoices.exact);
    } else if (name.equalsIgnoreCase("svdELoss")) {
      int rank = Integer.parseInt(System.getProperty("svdELoss"));
      return new SVDReducedObj(mert, rank, SVDReducedObj.SVDOptChoices.evalue);
    } else {
      throw new UnsupportedOperationException("Unknown optimizer: "+name);
    }
  }
}

/**
 * @author Michel Galley
 */
class SequenceOptimizer extends AbstractNBestOptimizer {

  private static final double MIN_OBJECTIVE_CHANGE = 1e-5;

  private final List<NBestOptimizer> opts;
  private final boolean loop;

  public SequenceOptimizer(MERT mert, List<NBestOptimizer> opts, boolean loop) {
    super(mert);
    this.opts = opts;
    this.loop = loop;
  }

  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = initialWts;
    for(NBestOptimizer opt : opts) {

      boolean done = false;
      
      while(!done) {
        Counter<String> newWts = opt.optimize(wts);
        
        double wtSsd = MERT.wtSsd(newWts, wts);

        double oldE = MERT.evalAtPoint(nbest,wts,emetric);
        double newE = MERT.evalAtPoint(nbest,newWts,emetric);
        //MERT.updateBest(newWts, -newE);

        boolean worse = oldE > newE;
        done = Math.abs(oldE-newE) <= MIN_OBJECTIVE_CHANGE || !loop || worse;

        System.err.printf("seq optimizer: %s -> %s (%s) ssd: %f done: %s opt: %s\n",
          oldE, newE, newE-oldE, wtSsd, done, opt.toString());
        
        if(worse)
          System.err.printf("WARNING: negative objective change!");
        else
          wts = newWts;
      }
    }
    return wts;
  }
}


/**
 * Optimization algorithm used by cmert included in Moses.
 *
 * @author danielcer
 */
class KoehnStyleOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = false;

  public KoehnStyleOptimizer(MERT mert) {
    super(mert);
  }

  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = initialWts;

    for (double oldEval = Double.NEGATIVE_INFINITY;;) {
      Counter<String> wtsFromBestDir = null;
      double fromBestDirScore = Double.NEGATIVE_INFINITY;
      String bestDirName = null;
      assert(wts != null);
      for (String feature : wts.keySet()) {
        //if (DEBUG)
        System.out.printf("Searching %s\n", feature);
        Counter<String> dir = new ClassicCounter<String>();
        dir.incrementCount(feature, 1.0);
        Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
        double eval = MERT.evalAtPoint(nbest, newWts, emetric);
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

      double eval = MERT.evalAtPoint(nbest, wts, emetric);
      if (Math.abs(eval - oldEval) < MERT.MIN_OBJECTIVE_DIFF)
        break;
      oldEval = eval;
    }

    return wts;
  }
}

class LogLinearOptimizer extends AbstractNBestOptimizer {
	public LogLinearOptimizer(MERT mert) {
		super(mert);
	}
	
	@Override
	public Counter<String> optimize(Counter<String> initialWts) {
		Counter<String> wts = new ClassicCounter<String>(initialWts);
		List<ScoredFeaturizedTranslation<IString, String>> target = (new HillClimbingMultiTranslationMetricMax<IString, String>(
	            emetric)).maximize(nbest);
		
		// create a mapping between weight names and optimization 
		// weight vector positions
		
		String[] weightNames = new String[wts.size()];
		int nameIdx = 0;
		for (String feature : wts.keySet()) {
			weightNames[nameIdx++] = feature;
		}
		
		System.err.println("Target Score: "+emetric.score(target));
		QNMinimizer qn = new QNMinimizer(15, true);
		LogLinearObjective llo = new LogLinearObjective(weightNames, target);
		double newX[] = qn.minimize(llo, 1e-4, new double[weightNames.length]);
		
		Counter<String> newWts = new ClassicCounter<String>();
		for (int i = 0; i < weightNames.length; i++) {
			newWts.setCount(weightNames[i], newX[i]);
		}
		
		System.err.println("Final Objective value: "+llo.valueAt(newX));
		System.err.println("Final Eval at point: "+MERT.evalAtPoint(nbest, newWts, emetric));
		return newWts;
	}

	class LogLinearObjective implements DiffFunction {
		final String[] weightNames;
		final List<ScoredFeaturizedTranslation<IString, String>> target;
		
		
		public LogLinearObjective(String[] weightNames, List<ScoredFeaturizedTranslation<IString, String>> target) {
			this.weightNames = weightNames;
			this.target = target;
		}
		
		private Counter<String> vectorToWeights(double[] x) {
			Counter<String> wts = new ClassicCounter<String>();
			for (int i = 0; i < weightNames.length; i++) {
				wts.setCount(weightNames[i], x[i]);
			}
			return wts;
		}
		
		private double[]  counterToVector(Counter<String> c) {
			double[] v = new double[weightNames.length];			
			for (int i = 0; i < weightNames.length; i++) {
				v[i] = c.getCount(weightNames[i]);
			}
			return v;
		}
		
		private double scoreTranslation(Counter<String> wts, ScoredFeaturizedTranslation<IString,String> trans) {
			double s = 0;
			for (FeatureValue<String> fv : trans.features) {
				s += fv.value * wts.getCount(fv.name);
			}
			return s;
		}
		
		@Override
		public double[] derivativeAt(double[] x) {
           Counter<String> wts = vectorToWeights(x);
           Counter<String> dOplus = new ClassicCounter<String>();
           Counter<String> dOminus = new ClassicCounter<String>();
           
	       Counter<String> dOdW = new ClassicCounter<String>();
	       List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLists = nbest.nbestLists();
	       for (int sentId = 0; sentId < nbestLists.size(); sentId++) {
	           List<ScoredFeaturizedTranslation<IString,String>> nbestList = nbestLists.get(sentId);
	           ScoredFeaturizedTranslation<IString,String> targetTrans = target.get(sentId);
	           
	    	   double Z = 0;
	    	   for (ScoredFeaturizedTranslation<IString,String> trans : nbestList) {
	    		   Z += Math.exp(scoreTranslation(wts, trans));
	    	   }

		       for (FeatureValue<String> fv : targetTrans.features) {
		    	  dOplus.incrementCount(fv.name, fv.value);
		       }
		       
		       for (ScoredFeaturizedTranslation<IString,String> trans : nbestList) {
		    	   double p = Math.exp(scoreTranslation(wts, trans))/Z;
	    		   for (FeatureValue<String> fv : trans.features) {
	    			   dOminus.incrementCount(fv.name, fv.value*p);
	    		   }
		       }
	       }
	       System.err.println("dOPlus "+dOplus);
	       
	       System.err.println("dOMinus "+dOminus);
	       
	       dOdW.addAll(dOplus);
	       Counters.subtractInPlace(dOdW, dOminus);
           Counters.multiplyInPlace(dOdW, -1);
	       return counterToVector(dOdW);
		}

		@Override
		public double valueAt(double[] x) {
			System.err.println("valueAt x[]: "+Arrays.toString(x));
			Counter<String> wts = vectorToWeights(x);
			System.err.println("wts: "+wts);
			List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLists = nbest.nbestLists();
		    double sumLogP = 0;
 	        for (int sentId = 0; sentId < nbestLists.size(); sentId++) {
	           List<ScoredFeaturizedTranslation<IString,String>> nbestList = nbestLists.get(sentId);
	           ScoredFeaturizedTranslation<IString,String> targetTrans = target.get(sentId);
	           
	    	   double Z = 0;
	    	   for (ScoredFeaturizedTranslation<IString,String> trans : nbestList) {
	    		   Z += Math.exp(scoreTranslation(wts, trans));
	    		   //System.err.println("raw: "+scoreTranslation(wts, trans));
	    	   }
	    	   //System.err.println("Z: "+Z);

		       double p = Math.exp(scoreTranslation(wts, targetTrans))/Z;
		       if (p != p) return Double.POSITIVE_INFINITY;
	    	   
		       //System.err.println("p: "+p);
		       sumLogP += -Math.log(p);
	        }		    
 	        
 	        System.err.println("sumLogP: "+sumLogP + "Eval at point: "+MERT.evalAtPoint(nbest, wts, emetric));
			return sumLogP;
		}

		@Override
		public int domainDimension() {
			return weightNames.length;
		}
	}
}


/**
 * @author danielcer
 */
class CerStyleOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = false;

  private Counter<String> featureMeans;
  private Counter<String> featureVars;
  private Counter<String> featureNbestOccurances;

  public CerStyleOptimizer(MERT mert) {
    super(mert);
  }

  @SuppressWarnings("deprecation")
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> featureOccurances;
    Counter<String> wts = new ClassicCounter<String>(initialWts);
    double oldEval = Double.NEGATIVE_INFINITY;
    double finalEval;
    int iter = 0;

    double initialEval = MERT.evalAtPoint(nbest, wts, emetric);
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
              MERT.MIN_NBEST_OCCURRENCES);
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

    for (String w : wts.keySet()) {
      if (featureNbestOccurances.getCount(w) < MERT.MIN_NBEST_OCCURRENCES) {
        wts.setCount(w, 0);
      }
    }
    MERT.normalize(wts);

    for (;; iter++) {
      Counter<String> dEl = new ClassicCounter<String>();
      double bestEval = Double.NEGATIVE_INFINITY;
      Counter<String> nextWts = wts;
      List<Counter<String>> priorSearchDirs = new ArrayList<Counter<String>>();
      // priorSearchDirs.add(wts);
      for (int i = 0, noProgressCnt = 0; noProgressCnt < 15; i++) {
        ErasureUtils.noop(i);
        boolean atLeastOneParameter = false;
        for (String w : initialWts.keySet()) {
          if (featureNbestOccurances.getCount(w) >= MERT.MIN_NBEST_OCCURRENCES) {
            dEl.setCount(w, random.nextGaussian()
                    * Math.sqrt(featureVars.getCount(w)));
            atLeastOneParameter = true;
          }
        }
        if (!atLeastOneParameter) {
          System.err
                  .printf(
                          "Error: no feature occurs on %d or more n-best lists - can't optimization.\n",
                          MERT.MIN_NBEST_OCCURRENCES);
          System.err
                  .printf("(This probably means your n-best lists are too small)\n");
          System.exit(-1);
        }
        MERT.normalize(dEl);
        Counter<String> searchDir = new ClassicCounter<String>(dEl);
        for (Counter<String> priorDir : priorSearchDirs) {
          Counter<String> projOnPrior = new ClassicCounter<String>(
                  priorDir);
          Counters.multiplyInPlace(projOnPrior, Counters.dotProduct(priorDir, dEl)
                  / Counters.dotProduct(priorDir, priorDir));
          Counters.subtractInPlace(searchDir, projOnPrior);
        }
        if (Counters.dotProduct(searchDir, searchDir) < MERT.NO_PROGRESS_SSD) {
          noProgressCnt++;
          continue;
        }
        priorSearchDirs.add(searchDir);
        if (DEBUG)
          System.out.printf("Searching %s\n", searchDir);
        nextWts = mert.lineSearch(nbest, nextWts, searchDir, emetric);
        double eval = MERT.evalAtPoint(nbest, nextWts, emetric);
        if (Math.abs(eval - bestEval) < 1e-9) {
          noProgressCnt++;
        } else {
          noProgressCnt = 0;
        }

        bestEval = eval;
      }

      MERT.normalize(nextWts);
      double eval;
      Counter<String> oldWts = wts;
      eval = bestEval;
      wts = nextWts;

      double ssd = 0;
      for (String k : wts.keySet()) {
        double diff = oldWts.getCount(k) - wts.getCount(k);
        ssd += diff * diff;
      }
      ErasureUtils.noop(ssd);

      System.out
              .printf(
                      "Global max along dEl dir(%d): %f obj diff: %f (*-1+%f=%f) Total Cnt: %f l1norm: %f\n",
                      iter, eval, Math.abs(oldEval - eval), MERT.MIN_OBJECTIVE_DIFF,
                      MERT.MIN_OBJECTIVE_DIFF - Math.abs(oldEval - eval), wts.totalCount(),
                      MERT.l1norm(wts));

      if (Math.abs(oldEval - eval) < MERT.MIN_OBJECTIVE_DIFF) {
        finalEval = eval;
        break;
      }

      oldEval = eval;
    }

    System.out.printf("Final iters: %d %f->%f\n", iter, initialEval, finalEval);
    return wts;
  }
}

/**
 * @author danielcer
 */
@SuppressWarnings("unused")
class OldCerStyleOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = false;

  public OldCerStyleOptimizer(MERT mert) {
    super(mert);
  }

  @SuppressWarnings("deprecation")
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;
    double finalEval;
    int iter = 0;
    double initialEval = MERT.evalAtPoint(nbest, wts, emetric);
    System.out.printf("Initial (Pre-optimization) Score: %f\n", initialEval);
    for (;; iter++) {
      Counter<String> dEl = new ClassicCounter<String>();
      IncrementalEvaluationMetric<IString, String> incEvalMetric = emetric
              .getIncrementalMetric();
      Counter<String> scaledWts = new ClassicCounter<String>(wts);
      Counters.normalize(scaledWts);
      Counters.multiplyInPlace(scaledWts, 0.01);
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbest
              .nbestLists()) {
        if (incEvalMetric.size() > 0)
          incEvalMetric.replace(incEvalMetric.size() - 1, null);
        incEvalMetric.add(null);
        //List<ScoredFeaturizedTranslation<IString, String>> sfTrans = nbestlist;
        List<Collection<FeatureValue<String>>> featureVectors = new ArrayList<Collection<FeatureValue<String>>>(
                nbestlist.size());
        double[] us = new double[nbestlist.size()];
        int pos = incEvalMetric.size() - 1;
        for (ScoredFeaturizedTranslation<IString, String> sfTran : nbestlist) {
          incEvalMetric.replace(pos, sfTran);
          us[featureVectors.size()] = incEvalMetric.score();
          featureVectors.add(sfTran.features);
        }

        dEl.addAll(EValueLearningScorer.dEl(new StaticScorer(scaledWts, MERT.featureIndex),
                featureVectors, us));
      }

      Counters.normalize(dEl);

      // System.out.printf("Searching %s\n", dEl);
      Counter<String> wtsdEl = mert.lineSearch(nbest, wts, dEl, emetric);
      double evaldEl = MERT.evalAtPoint(nbest, wtsdEl, emetric);

      double eval;
      Counter<String> oldWts = wts;
      eval = evaldEl;
      wts = wtsdEl;

      double ssd = 0;
      for (String k : wts.keySet()) {
        double diff = oldWts.getCount(k) - wts.getCount(k);
        ssd += diff * diff;
      }

      System.out.printf("Global max along dEl dir(%d): %f wts ssd: %f\n", iter,
              eval, ssd);

      if (ssd < MERT.NO_PROGRESS_SSD) {
        finalEval = eval;
        break;
      }
    }

    System.out.printf("Final iters: %d %f->%f\n", iter, initialEval, finalEval);
    return wts;
  }
}

/**
 * Line Search optimizer to tune one single feature.
 * If no feature name is previded, LineSearchOptimizer tunes the word penalty
 * (useful when one only needs to make translation a little bit shorter or longer).
 *
 * @author Michel Galley
 */
class LineSearchOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = false;

  private String featureName;

  public LineSearchOptimizer(MERT mert) {
    super(mert);
    featureName = WordPenaltyFeaturizer.FEATURE_NAME;
  }

  @SuppressWarnings("unused")
  public LineSearchOptimizer(MERT mert, String featureName) {
    super(mert);
    this.featureName = featureName;
  }

  public Counter<String> optimize(final Counter<String> initialWts) {
    Counter<String> dir = new ClassicCounter<String>();
    dir.incrementCount(featureName, 1.0);
    return mert.lineSearch(nbest, initialWts, dir, emetric);
  }
}

/**
 * Downhill simplex minimization algorithm (Nelder and Mead, 1965).
 *
 * @author Michel Galley
 */
class DownhillSimplexOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = false;
  static public final double BAD_WEIGHT_PENALTY = 1<<20;
  static public final String SIMPLEX_CLASS_NAME =
    "edu.stanford.nlp.optimization.extern.DownhillSimplexMinimizer";

  private final boolean szMinusOne;
  private final boolean doRandomSteps;
  private final int minIter;

  public DownhillSimplexOptimizer(MERT mert, int minIter, boolean doRandomSteps) {
    super(mert);
    this.minIter = minIter;
    this.doRandomSteps = doRandomSteps;
    this.szMinusOne = MERT.fixedWts == null;
  }

  @SuppressWarnings("unused")
  public DownhillSimplexOptimizer(MERT mert, boolean doRandomSteps) {
    super(mert);
    this.minIter = 1;
    this.doRandomSteps = doRandomSteps;
    this.szMinusOne = MERT.fixedWts == null;
  }

  private static final double SIMPLEX_RELATIVE_SIZE = 4;

  private Counter<String> randomStep(Set<String> keySet) {
    Counter<String> randpt = new ClassicCounter<String>();
    for (String f : keySet) {
      if (MERT.generativeFeatures.contains(f)) {
        randpt.setCount(f, random.nextDouble());
      } else {
        randpt.setCount(f, random.nextDouble() * 2 - 1.0);
      }
    }
    return randpt;
  }

  private Counter<String> arrayToCounter(String[] keys, double[] x) {
    Counter<String> c = new ClassicCounter<String>();
    if(szMinusOne) {
      for(int i=0; i<keys.length-1; ++i)
        c.setCount(keys[i], x[i]);
      double l1norm = ArrayMath.norm_1(x);
      c.setCount(keys[keys.length-1], 1.0-l1norm);
    } else {
      for(int i=0; i<keys.length; ++i)
        c.setCount(keys[i], x[i]);
    }
    return c;
  }

  private double[] counterToArray(String[] keys, Counter<String> wts) {
    int sz = keys.length;
    double[] x = szMinusOne ? new double[sz-1] : new double[sz];
    for(int i=0; i<x.length; ++i)
      x[i] = wts.getCount(keys[i]);
    return x;
  }

  public Counter<String> optimize(final Counter<String> initialWts) {
    assert(minIter >= 1);
    Counter<String> wts = initialWts;
    for(int i=0; i<minIter; ++i) {
      System.err.printf("iter %d (before): %s\n", i, wts.toString());
      wts = optimizeOnce(wts);
      System.err.printf("iter %d: (after): %s\n", i, wts.toString());
    }
    return wts;
  }

  @SuppressWarnings("unchecked")
  private static Minimizer<Function> createSimplexMinimizer(Class[] argClasses, Object[] args) {
    Minimizer<Function> metric;
    try {
      Class<Minimizer<Function>> cls = (Class<Minimizer<Function>>)Class.forName(SIMPLEX_CLASS_NAME);
      Constructor<Minimizer<Function>> ct = cls.getConstructor(argClasses);
      metric = ct.newInstance(args);
    }
    catch (Exception e) { throw new RuntimeException(e); }
    return metric;
  }

  private Counter<String> optimizeOnce(final Counter<String> initialWts) {

    System.err.printf("\nDownhill simplex starts at: %s value: %.5f\n",
       initialWts.toString(), MERT.evalAtPoint(nbest, initialWts, emetric));

    final int sz = initialWts.size();
    final String[] keys = initialWts.keySet().toArray(new String[sz]);
    final MutableDouble bestEval = new MutableDouble(-Double.MAX_VALUE);
    final MutableInteger it = new MutableInteger(0);
    MERT.normalize(initialWts);

    double[] initx = counterToArray(keys, initialWts);

    final Minimizer<Function> opt;
    if(doRandomSteps) {
      Set<String> keySet = new HashSet<String>(Arrays.asList(keys));
      Counter<String> randomStep = randomStep(keySet);
      MERT.normalize(randomStep);
      double[] randx = counterToArray(keys, randomStep);
      ArrayMath.multiplyInPlace(randx, SIMPLEX_RELATIVE_SIZE);
      opt = createSimplexMinimizer(new Class[] {Array.class}, new Object[] {randx});
      //opt = new DownhillSimplexMinimizer(randx);
    } else {
      opt = createSimplexMinimizer(new Class[] {double.class}, new Object[] {SIMPLEX_RELATIVE_SIZE});
      //opt = new DownhillSimplexMinimizer(SIMPLEX_RELATIVE_SIZE);
    }

    Function f = new Function() {
      public double valueAt(double[] x) {
        Counter<String> xC = arrayToCounter(keys, x);

        double penalty = 0.0;
        for (Map.Entry<String,Double> el : xC.entrySet())
          if (el.getValue() < 0 && MERT.generativeFeatures.contains(el.getKey()))
            penalty += BAD_WEIGHT_PENALTY;

        double curEval = MERT.evalAtPoint(nbest, xC, emetric) - penalty;

        if(curEval > bestEval.doubleValue())
          bestEval.set(curEval);
        
        it.set(it.intValue()+1);
        System.err.printf("current eval(%d): %.5f - best eval: %.5f\n", it.intValue(), curEval, bestEval.doubleValue());
        return -curEval;
      }
      public int domainDimension() { return initialWts.size()-1; }
    };

    double[] wtsA = opt.minimize(f, 1e-4, initx, 1000);
    Counter<String> wts = arrayToCounter(keys, wtsA);
    MERT.normalize(wts);
    System.err.printf("\nDownhill simplex converged at: %s value: %.5f\n", wts.toString(), MERT.evalAtPoint(nbest, wts, emetric));
    return wts;
  }
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
 * @author danielcer
 */
class PowellOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = false;

  public PowellOptimizer(MERT mert) {
    super(mert);
  }

  @SuppressWarnings( { "unchecked", "deprecation" })
  public Counter<String> optimize(Counter<String> initialWts) {
    
    Counter<String> wts = initialWts;

    // initialize search directions
    List<Counter<String>> dirs = new ArrayList<Counter<String>>(
            initialWts.size());
    List<String> featureNames = new ArrayList<String>(wts.keySet());
    Collections.sort(featureNames);
    for (String featureName : featureNames) {
      Counter<String> dir = new ClassicCounter<String>();
      dir.incrementCount(featureName);
      dirs.add(dir);
    }

    // main optimization loop
    Counter[] p = new ClassicCounter[dirs.size()];
    double objValue = MERT.evalAtPoint(nbest, wts, emetric); // obj value w/o
    // smoothing
    for (int iter = 0;; iter++) {
      // search along each direction
      p[0] = mert.lineSearch(nbest, wts, dirs.get(0), emetric);
      double eval = MERT.evalAtPoint(nbest, p[0], emetric);
      double biggestWin = Math.max(0, eval - objValue);
      System.err.printf("initial totalWin: %e (%e-%e)\n", biggestWin,
              eval, objValue);
      System.err.printf("eval @ wts: %e\n", MERT.evalAtPoint(nbest, wts, emetric));
      System.err.printf("eval @ p[0]: %e\n", MERT.evalAtPoint(nbest, p[0], emetric));
      objValue = eval;
      int biggestWinId = 0;
      double totalWin = biggestWin;
      double initObjValue = objValue;
      for (int i = 1; i < p.length; i++) {
        p[i] = mert.lineSearch(nbest, (Counter<String>) p[i - 1],
                dirs.get(i), emetric);
        eval = MERT.evalAtPoint(nbest, p[i], emetric);
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
      Counter<String> combinedDir = new ClassicCounter<String>(wts);
      Counters.multiplyInPlace(combinedDir, -1.0);
      combinedDir.addAll(p[p.length - 1]);

      // check to see if we should replace the dominant 'win' direction
      // during the last iteration of search with the combined search direction
      Counter<String> testPoint = new ClassicCounter<String>(
              p[p.length - 1]);
      testPoint.addAll(combinedDir);
      double testPointEval = MERT.evalAtPoint(nbest, testPoint, emetric);
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
        MERT.normalize(combinedDir);
        dirs.set(biggestWinId, combinedDir);
      }

      // Search along combined dir even if replacement didn't happen
      wts = mert.lineSearch(nbest, p[p.length - 1], combinedDir, emetric);
      eval = MERT.evalAtPoint(nbest, wts, emetric);
      System.err.printf(
              "%d: Objective after combined search (gain: %e prior:%e)\n", iter,
              eval - objValue, objValue);

      objValue = eval;

      double finalObjValue = objValue;
      System.err.printf("Actual win: %e (%e-%e)\n", finalObjValue
              - initObjValue, finalObjValue, initObjValue);
      if (Math.abs(initObjValue - finalObjValue) < MERT.MIN_OBJECTIVE_DIFF)
        break; // changed to prevent infinite loops
    }

    return wts;
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
 * @author danielcer
 */
class BasicPowellOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = false;

  public BasicPowellOptimizer(MERT mert) {
    super(mert);
  }

  @SuppressWarnings( { "unchecked", "deprecation" })
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = initialWts;

    // initialize search directions
    List<Counter<String>> axisDirs = new ArrayList<Counter<String>>(
            initialWts.size());
    List<String> featureNames = new ArrayList<String>(wts.keySet());
    Collections.sort(featureNames);
    for (String featureName : featureNames) {
      Counter<String> dir = new ClassicCounter<String>();
      dir.incrementCount(featureName);
      axisDirs.add(dir);
    }

    // main optimization loop
    Counter[] p = new ClassicCounter[axisDirs.size()];
    double objValue = MERT.evalAtPoint(nbest, wts, emetric); // obj value w/o
    // smoothing
    List<Counter<String>> dirs = null;
    for (int iter = 0;; iter++) {
      if (iter % p.length == 0) {
        // reset after N iterations to avoid linearly dependent search
        // directions
        System.err.printf("%d: Search direction reset\n", iter);
        dirs = new ArrayList<Counter<String>>(axisDirs);
      }
      // search along each direction
      assert(dirs != null);
      p[0] = mert.lineSearch(nbest, wts, dirs.get(0), emetric);
      for (int i = 1; i < p.length; i++) {
        p[i] = mert.lineSearch(nbest, (Counter<String>) p[i - 1],
                dirs.get(i), emetric);
        dirs.set(i - 1, dirs.get(i)); // shift search directions
      }

      double totalWin = MERT.evalAtPoint(nbest, p[p.length-1], emetric) - objValue;
      System.err.printf("%d: totalWin: %e Objective: %e\n", iter, totalWin,
              objValue);
      if (Math.abs(totalWin) < MERT.MIN_OBJECTIVE_DIFF)
        break;

      // construct combined direction
      Counter<String> combinedDir = new ClassicCounter<String>(wts);
      Counters.multiplyInPlace(combinedDir, -1.0);
      combinedDir.addAll(p[p.length - 1]);

      dirs.set(p.length - 1, combinedDir);

      // search along combined direction
      wts = mert.lineSearch(nbest, (Counter<String>) p[p.length - 1], dirs
              .get(p.length - 1), emetric);
      objValue = MERT.evalAtPoint(nbest, wts, emetric);
      System.err.printf("%d: Objective after combined search %e\n", iter,
              objValue);
    }

    return wts;
  }
}


/**
 * @author danielcer
 */
class MCMCDerivative extends AbstractNBestOptimizer {

	MutableDouble expectedEval;
	MutableDouble objValue;

  @SuppressWarnings("unused")
  public MCMCDerivative(MERT mert) {
    this(mert,null);
  }

  public MCMCDerivative(MERT mert, MutableDouble expectedEval) {
    this(mert,expectedEval,null);
  }

  public MCMCDerivative(MERT mert, MutableDouble expectedEval, MutableDouble objValue) {
    super(mert);
		this.expectedEval = expectedEval;
		this.objValue = objValue;
  }

  @SuppressWarnings({ "deprecation" })
  public Counter<String> optimize(Counter<String> wts) {

    double C = MERT.C;

    System.err.printf("MCMC weights:\n%s\n\n", Counters.toString(wts, 35));

    // for quick mixing, get current classifier argmax
    System.err.println("finding argmax");
    List<ScoredFeaturizedTranslation<IString, String>> argmax =
            MERT.transArgmax(nbest, wts), current =
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

    Counter<String> dE = new ClassicCounter<String>();
    Scorer<String> scorer = new StaticScorer(wts, MERT.featureIndex);

    double hardEval = emetric.score(argmax);
    System.err.printf("Hard eval: %.5f\n", hardEval);

    // expected value sums
    OpenAddressCounter<String> sumExpLF = new OpenAddressCounter<String>(0.50f);
    double sumExpL = 0.0;
    OpenAddressCounter<String> sumExpF = new OpenAddressCounter<String>(0.50f);
    int cnt = 0;
    double dEDiff; // = Double.POSITIVE_INFINITY;
    double dECosine = 0.0;
    for (int batch = 0;
         (dECosine < MERT.NO_PROGRESS_MCMC_COSINE
                 || batch < MERT.MCMC_MIN_BATCHES) &&
                 batch < MERT.MCMC_MAX_BATCHES; batch++) {
      Counter<String> oldDe = new ClassicCounter<String>(dE);

      // reset current to argmax
      current = new ArrayList<ScoredFeaturizedTranslation<IString, String>>
              (argmax);

      // reset incremental evaluation object for the argmax candidates
      IncrementalEvaluationMetric<IString, String> incEval =
              emetric.getIncrementalMetric();

      for (ScoredFeaturizedTranslation<IString, String> tran : current)
        incEval.add(tran);

      OpenAddressCounter<String> currentF;
      //= new OpenAddressCounter<String>(MERT.summarizedAllFeaturesVector(current), 0.50f);

      System.err.println("Sampling");

      long time = -System.currentTimeMillis();
      for (int bi = 0; bi < MERT.MCMC_BATCH_SAMPLES; bi++) {
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
              double rv = mert.random.nextDouble()*Z;
              for (int i = 0; i < num.length; i++) {
                if ((rv -= num[i]) <= 0) { selection = i; break; }
              }
            } else {
              selection = mert.random.nextInt(num.length);
            }

            if (Z == 0) {
              Z = 1.0;
              num[selection] = 1.0/num.length;
            }
            ErasureUtils.noop(Z);
            //System.out.printf("%d:%d - selection: %d p(%g|f) %g/%g\n", bi, sentId, selection, num[selection]/Z, num[selection], Z);

            // adjust current
            current.set(sentId, nbest.nbestLists().get(sentId).get(selection));
          }

        // collect derivative relevant statistics using sample
        cnt++;

        // adjust currentF & eval
        currentF =
                new OpenAddressCounter<String>(MERT.summarizedAllFeaturesVector(current), 0.50f);
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
      dEDiff = MERT.wtSsd(oldDe, dE);
      dECosine = Counters.dotProduct(oldDe, dE)/
              (Counters.L2Norm(dE)*Counters.L2Norm(oldDe));
      if (Double.isNaN(dECosine)) dECosine = 0;
      //if (dECosine != dECosine) dECosine = 0;

      System.err.printf("Batch: %d dEDiff: %e dECosine: %g)\n",
              batch, dEDiff, dECosine);
      System.err.printf("Sampling time: %.3f s\n", time/1000.0);
      System.err.printf("E(loss) = %e\n", sumExpL/cnt);
      System.err.printf("E(loss*f):\n%s\n\n", Counters.toString(Counters.divideInPlace(new ClassicCounter<String>(sumExpLF), cnt), 35));
      System.err.printf("E(f):\n%s\n\n", Counters.toString(Counters.divideInPlace(new ClassicCounter<String>(sumExpF), cnt), 35));
      System.err.printf("dE:\n%s\n\n", Counters.toString(dE, 35));
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
    Counter<String> dObj = new ClassicCounter<String>(dE);


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
}


/**
 * @author danielcer
 */
class BetterWorseCentroids extends AbstractNBestOptimizer {

  boolean useCurrentAsWorse;
	boolean useOnlyBetter;

  public BetterWorseCentroids(MERT mert, boolean useCurrentAsWorse, boolean useOnlyBetter) {
    super(mert);
		this.useCurrentAsWorse = useCurrentAsWorse;
		this.useOnlyBetter = useOnlyBetter;
  }

  @SuppressWarnings( { "deprecation", "unchecked" })
  public Counter<String> optimize(Counter<String> wts) {

    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
            .nbestLists();
    //Counter<String> wts = initialWts;

    for (int iter = 0;; iter++) {
      List<ScoredFeaturizedTranslation<IString, String>> current = MERT.transArgmax(
              nbest, wts);
      IncrementalEvaluationMetric<IString, String> incEval = emetric
              .getIncrementalMetric();
      for (ScoredFeaturizedTranslation<IString, String> tran : current) {
        incEval.add(tran);
      }
      Counter<String> betterVec = new ClassicCounter<String>();
      int betterCnt = 0;
      Counter<String> worseVec = new ClassicCounter<String>();
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
            betterVec.addAll(MERT.normalize(MERT.summarizedAllFeaturesVector(Arrays
                    .asList(tran))));
          } else {
            worseCnt++;
            worseVec.addAll(MERT.normalize(MERT.summarizedAllFeaturesVector(Arrays
                    .asList(tran))));
          }
        }
        incEval.replace(lI, current.get(lI));
      }
      MERT.normalize(betterVec);
      if (useCurrentAsWorse)
        worseVec = MERT.summarizedAllFeaturesVector(current);
      MERT.normalize(worseVec);
      Counter<String> dir = new ClassicCounter<String>(betterVec);
      if (!useOnlyBetter)
        Counters.subtractInPlace(dir, worseVec);
      MERT.normalize(dir);
      System.err.printf("iter: %d\n", iter);
      System.err.printf("Better cnt: %d\n", betterCnt);
      System.err.printf("Worse cnt: %d\n", worseCnt);
      System.err.printf("Better Vec:\n%s\n\n", betterVec);
      System.err.printf("Worse Vec:\n%s\n\n", worseVec);
      System.err.printf("Dir:\n%s\n\n", dir);
      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      System.err.printf("new wts:\n%s\n\n", wts);
      double ssd = MERT.wtSsd(wts, newWts);
      wts = newWts;
      System.err.printf("ssd: %f\n", ssd);
      if (ssd < MERT.NO_PROGRESS_SSD)
        break;
    }

    return wts;
  }
}


/**
 * @author danielcer
 */
class FullKMeans extends AbstractNBestOptimizer {

  static MosesNBestList lastNbest;
  static List<Counter<String>> lastKMeans;
  static Counter<String> lastWts;

	int K;
	boolean clusterToCluster;

  public FullKMeans(MERT mert, int K, boolean clusterToCluster) {
    super(mert);
		this.K = K;
		this.clusterToCluster = clusterToCluster;
  }

  @SuppressWarnings( { "deprecation", "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
            .nbestLists();

    List<Counter<String>> kMeans = new ArrayList<Counter<String>>(
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
        ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          ErasureUtils.noop(tran);
          vecCnt++;
        }

      List<Counter<String>> allVecs = new ArrayList<Counter<String>>(
              vecCnt);
      int[] clusterIds = new int[vecCnt];

      for (int i = 0; i < K; i++)
        kMeans.add(new ClassicCounter<String>());

      // Extract all feature vectors & use them to seed the clusters;
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
        for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          Counter<String> feats = Counters.L2Normalize(MERT.summarizedAllFeaturesVector(Arrays
                  .asList(tran)));
          int clusterId = random.nextInt(K);
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
        List<Counter<String>> newKMeans = new ArrayList<Counter<String>>(
                K);
        for (int i = 0; i < K; i++)
          newKMeans.add(new ClassicCounter<String>());

        for (int i = 0; i < vecCnt; i++) {
          Counter<String> feats = allVecs.get(i);
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
    Counter<String> wts = new ClassicCounter<String>(initialWts);
    if (clusterToCluster) {
      Counter<String> bestWts = null;
      double bestEval = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < K; i++) {
        if (clusterCnts[i] == 0)
          continue;
        for (int j = i + 1; j < K; j++) {
          if (clusterCnts[j] == 0)
            continue;
          System.err.printf("seach pair: %d->%d\n", j, i);
          Counter<String> dir = new ClassicCounter<String>(kMeans.get(i));
          Counters.subtractInPlace(dir, kMeans.get(j));
          Counter<String> eWts = mert.lineSearch(nbest, kMeans.get(j), dir,
                  emetric);
          double eval = MERT.evalAtPoint(nbest, eWts, emetric);
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
        ErasureUtils.noop(iter);
        Counter<String> newWts = new ClassicCounter<String>(wts);
        for (int i = 0; i < K; i++) {
          List<ScoredFeaturizedTranslation<IString, String>> current = MERT.transArgmax(
                  nbest, newWts);
          Counter<String> c = Counters.L2Normalize(MERT.summarizedAllFeaturesVector(current));
          Counter<String> dir = new ClassicCounter<String>(kMeans.get(i));
          Counters.subtractInPlace(dir, c);

          System.err.printf("seach perceptron to cluster: %d\n", i);
          newWts = mert.lineSearch(nbest, newWts, dir, emetric);
          System.err.printf("new eval: %f\n", MERT.evalAtPoint(nbest, newWts,
                  emetric));
          for (int j = i; j < K; j++) {
            dir = new ClassicCounter<String>(kMeans.get(i));
            if (j != i) {
              System.err.printf("seach pair: %d<->%d\n", j, i);
              Counters.subtractInPlace(dir, kMeans.get(j));
            } else {
              System.err.printf("seach singleton: %d\n", i);
            }

            newWts = mert.lineSearch(nbest, newWts, dir, emetric);
            System.err.printf("new eval: %f\n", MERT.evalAtPoint(nbest, newWts,
                    emetric));
          }
        }
        System.err.printf("new wts:\n%s\n\n", newWts);
        double ssd = MERT.wtSsd(wts, newWts);
        wts = newWts;
        System.err.printf("ssd: %f\n", ssd);
        if (ssd < MERT.NO_PROGRESS_SSD)
          break;
      }
    }

    lastWts = wts;
    return wts;
  }
}


/**
 * @author danielcer
 */
class BetterWorse3KMeans extends AbstractNBestOptimizer {

  static enum Cluster3 {
    better, worse, same
  }

  static enum Cluster3LearnType {
    betterWorse, betterSame, betterPerceptron, allDirs
  }

  Cluster3LearnType lType;

  public BetterWorse3KMeans(MERT mert, Cluster3LearnType lType) {
    super(mert);
		this.lType = lType;
  }

  @SuppressWarnings( { "deprecation", "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
            .nbestLists();
    Counter<String> wts = initialWts;

    for (int iter = 0;; iter++) {
      List<ScoredFeaturizedTranslation<IString, String>> current = MERT.transArgmax(
              nbest, wts);
      IncrementalEvaluationMetric<IString, String> incEval = emetric
              .getIncrementalMetric();
      for (ScoredFeaturizedTranslation<IString, String> tran : current) {
        incEval.add(tran);
      }
      Counter<String> betterVec = new ClassicCounter<String>();
      int betterClusterCnt = 0;
      Counter<String> worseVec = new ClassicCounter<String>();
      int worseClusterCnt = 0;
      Counter<String> sameVec = new ClassicCounter<String>(
              Counters.L2Normalize(MERT.summarizedAllFeaturesVector(current)));
      int sameClusterCnt = 0;

      double baseScore = incEval.score();
      System.err.printf("baseScore: %f\n", baseScore);
      int lI = -1;
      List<Counter<String>> allPoints = new ArrayList<Counter<String>>();
      List<Cluster3> inBetterCluster = new ArrayList<Cluster3>();
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
        lI++;
        for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          incEval.replace(lI, tran);
          Counter<String> feats = Counters.L2Normalize(MERT.summarizedAllFeaturesVector(Arrays
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
        Counter<String> newBetterVec = new ClassicCounter<String>();
        Counter<String> newSameVec = new ClassicCounter<String>();
        Counter<String> newWorseVec = new ClassicCounter<String>();
        betterClusterCnt = 0;
        worseClusterCnt = 0;
        sameClusterCnt = 0;
        for (int i = 0; i < allPoints.size(); i++) {
          Counter<String> pt = allPoints.get(i);
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

      Counter<String> dir = new ClassicCounter<String>();
      if (betterClusterCnt != 0)
        dir.addAll(betterVec);

      switch (lType) {
        case betterPerceptron:
          Counter<String> c = Counters.L2Normalize(MERT.summarizedAllFeaturesVector(current));
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

      MERT.normalize(dir);
      System.err.printf("iter: %d\n", iter);
      System.err.printf("Better cnt: %d\n", betterClusterCnt);
      System.err.printf("SameClust: %d\n", sameClusterCnt);
      System.err.printf("Worse cnt: %d\n", worseClusterCnt);
      System.err.printf("Better Vec:\n%s\n\n", betterVec);
      System.err.printf("l2: %f\n", Counters.L2Norm(betterVec));
      System.err.printf("Worse Vec:\n%s\n\n", worseVec);
      System.err.printf("Same Vec:\n%s\n\n", sameVec);
      System.err.printf("Dir:\n%s\n\n", dir);

      Counter<String> newWts;
      if (lType != Cluster3LearnType.allDirs) {
        newWts = mert.lineSearch(nbest, wts, dir, emetric);
      } else {
        Counter<String> c = Counters.L2Normalize(MERT.summarizedAllFeaturesVector(current));
        Counters.multiplyInPlace(c, Counters.L2Norm(betterVec));

        newWts = wts;

        // Better Same
        dir = new ClassicCounter<String>(betterVec);
        Counters.subtractInPlace(dir, sameVec);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Better Perceptron
        dir = new ClassicCounter<String>(betterVec);
        Counters.subtractInPlace(dir, c);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Better Worse
        dir = new ClassicCounter<String>(betterVec);
        Counters.subtractInPlace(dir, worseVec);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Same Worse
        dir = new ClassicCounter<String>(sameVec);
        Counters.subtractInPlace(dir, worseVec);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Same Perceptron
        dir = new ClassicCounter<String>(sameVec);
        Counters.subtractInPlace(dir, c);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);

        // Perceptron Worse
        dir = new ClassicCounter<String>(c);
        Counters.subtractInPlace(dir, worseVec);
        newWts = mert.lineSearch(nbest, newWts, dir, emetric);
      }
      System.err.printf("new wts:\n%s\n\n", newWts);
      double ssd = MERT.wtSsd(wts, newWts);
      wts = newWts;
      System.err.printf("ssd: %f\n", ssd);
      if (ssd < MERT.NO_PROGRESS_SSD)
        break;
    }

    return wts;
  }
}


/**
 * @author danielcer
 */
class BetterWorse2KMeans extends AbstractNBestOptimizer {

	boolean perceptron;
	boolean useWts;

  public BetterWorse2KMeans(MERT mert, boolean perceptron, boolean useWts) {
    super(mert);
		this.perceptron = perceptron;
		this.useWts = useWts;
  }

  @SuppressWarnings( { "deprecation", "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

    List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists = nbest
            .nbestLists();
    Counter<String> wts = initialWts;

    for (int iter = 0;; iter++) {
      List<ScoredFeaturizedTranslation<IString, String>> current = MERT.transArgmax(
              nbest, wts);
      IncrementalEvaluationMetric<IString, String> incEval = emetric
              .getIncrementalMetric();
      for (ScoredFeaturizedTranslation<IString, String> tran : current) {
        incEval.add(tran);
      }
      Counter<String> betterVec = new ClassicCounter<String>();
      int betterClusterCnt = 0;
      Counter<String> worseVec = new ClassicCounter<String>();
      int worseClusterCnt = 0;
      double baseScore = incEval.score();
      System.err.printf("baseScore: %f\n", baseScore);
      int lI = -1;
      List<Counter<String>> allPoints = new ArrayList<Counter<String>>();
      List<Boolean> inBetterCluster = new ArrayList<Boolean>();
      for (List<ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists) {
        lI++;
        for (ScoredFeaturizedTranslation<IString, String> tran : nbestlist) {
          incEval.replace(lI, tran);
          Counter<String> feats = Counters.L2Normalize(MERT.summarizedAllFeaturesVector(Arrays
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
        Counter<String> newBetterVec = new ClassicCounter<String>();
        Counter<String> newWorseVec = new ClassicCounter<String>();
        betterClusterCnt = 0;
        worseClusterCnt = 0;
        for (int i = 0; i < allPoints.size(); i++) {
          Counter<String> pt = allPoints.get(i);
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

      Counter<String> dir = new ClassicCounter<String>();
      if (betterClusterCnt != 0)
        dir.addAll(betterVec);
      if (perceptron) {
        if (useWts) {
          Counter<String> normWts = new ClassicCounter<String>(wts);
          Counters.L2Normalize(normWts);
          Counters.multiplyInPlace(normWts, Counters.L2Norm(betterVec));
          System.err.printf("Subing wts:\n%s\n", normWts);
          Counters.subtractInPlace(dir, normWts);
          System.err.printf("l2: %f\n", Counters.L2Norm(normWts));
        } else {
          Counter<String> c = Counters.L2Normalize(MERT.summarizedAllFeaturesVector(current));
          Counters.multiplyInPlace(c, Counters.L2Norm(betterVec));
          System.err.printf("Subing current:\n%s\n", c);
          Counters.subtractInPlace(dir, c);
          System.err.printf("l2: %f\n", Counters.L2Norm(c));
        }
      } else {
        if (worseClusterCnt != 0)
          Counters.subtractInPlace(dir, worseVec);
      }
      MERT.normalize(dir);
      System.err.printf("iter: %d\n", iter);
      System.err.printf("Better cnt: %d\n", betterClusterCnt);
      System.err.printf("Worse cnt: %d\n", worseClusterCnt);
      System.err.printf("Better Vec:\n%s\n\n", betterVec);
      System.err.printf("l2: %f\n", Counters.L2Norm(betterVec));
      System.err.printf("Worse Vec:\n%s\n\n", worseVec);
      System.err.printf("Dir:\n%s\n\n", dir);
      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      System.err.printf("new wts:\n%s\n\n", wts);
      double ssd = MERT.wtSsd(wts, newWts);
      wts = newWts;
      System.err.printf("ssd: %f\n", ssd);
      if (ssd < MERT.NO_PROGRESS_SSD)
        break;
    }

    return wts;
  }
}


/**
 * @author danielcer
 */
class SVDReducedObj extends AbstractNBestOptimizer {

  enum SVDOptChoices { exact, evalue }

  static Ptr<DenseMatrix> pU = null;
  static Ptr<DenseMatrix> pV = null;

	int rank;
	SVDOptChoices opt;

  public SVDReducedObj(MERT mert, int rank, SVDOptChoices opt) {
    super(mert);
		this.rank = rank;
		this.opt = opt;
  }

  @SuppressWarnings( { "deprecation", "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

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

    Counter<String> reducedInitialWts = weightsToReducedWeights(initialWts, pU.deref(), pFeatureIdMap.deref());

    System.err.println("Initial Wts:");
    System.err.println("====================");
    System.err.println(Counters.toString(initialWts, 35));

    System.err.println("Reduced Initial Wts:");
    System.err.println("====================");
    System.err.println(Counters.toString(reducedInitialWts, 35));


    System.err.println("Recovered Reduced Initial Wts");
    System.err.println("=============================");
    Counter<String> recoveredInitialWts =
            reducedWeightsToWeights(reducedInitialWts, pU.deref(), pFeatureIdMap.deref());
    System.err.println(Counters.toString(recoveredInitialWts, 35));


    Counter<String> reducedWts;
    switch (opt) {
      case exact:
        System.err.println("Using exact MERT");
        NBestOptimizer opt = new KoehnStyleOptimizer(mert);
        reducedWts = opt.optimize(reducedInitialWts);
        break;
      case evalue:
        System.err.println("Using E(Eval) MERT");
        NBestOptimizer opt2 = new MCMCELossObjectiveCG(mert);
        reducedWts = opt2.optimize(reducedInitialWts);
        break;
      default:
        throw new UnsupportedOperationException();
    }
    System.err.println("Reduced Learned Wts:");
    System.err.println("====================");
    System.err.println(Counters.toString(reducedWts, 35));


    Counter<String> recoveredWts = reducedWeightsToWeights(reducedWts, pU.deref(), pFeatureIdMap.deref());
    System.err.println("Recovered Learned Wts:");
    System.err.println("======================");
    System.err.println(Counters.toString(recoveredWts, 35));

    double wtSsd = MERT.wtSsd(reducedInitialWts, reducedWts);
    System.out.printf("reduced wts ssd: %e\n", wtSsd);

    double twtSsd = MERT.wtSsd(initialWts, recoveredWts);
    System.out.printf("recovered wts ssd: %e\n", twtSsd);
    return recoveredWts;
  }

  static public Counter<String> weightsToReducedWeights(
          Counter<String> wts, Matrix reducedRepU,
          Map<String,Integer> featureIdMap) {

    Matrix vecWtsOrig = new DenseMatrix(featureIdMap.size(), 1);
    for (Map.Entry<String,Integer> entry : featureIdMap.entrySet()) {
      vecWtsOrig.set(entry.getValue(), 0, wts.getCount(entry.getKey()));
    }

    Matrix vecWtsReduced = new DenseMatrix(reducedRepU.numColumns(), 1);
    reducedRepU.transAmult(vecWtsOrig, vecWtsReduced);

    Counter<String> reducedWts = new ClassicCounter<String>();
    for (int i = 0; i < vecWtsReduced.numRows(); i++) {
      reducedWts.setCount((Integer.valueOf(i)).toString(), vecWtsReduced.get(i,0));
    }

    return reducedWts;
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

  static public Counter<String> reducedWeightsToWeights(
          Counter<String> reducedWts,
          Matrix reducedRepU, Map<String,Integer> featureIdMap) {

    int col = reducedRepU.numColumns();
    Vector vecReducedWts = new DenseVector(col);
    for (int i = 0; i < col; i++) {
      vecReducedWts.set(i, reducedWts.getCount((Integer.valueOf(i)).toString()));
    }

    Vector vecWts = new DenseVector(reducedRepU.numRows());
    reducedRepU.mult(vecReducedWts, vecWts);

    Counter<String> wts = new ClassicCounter<String>();
    for (Map.Entry<String,Integer> entry : featureIdMap.entrySet()) {
      wts.setCount(entry.getKey(), vecWts.get(entry.getValue()));
    }

    return wts;
  }

  @SuppressWarnings("unused")
  static public MosesNBestList nbestListToDimReducedNbestList(
          MosesNBestList nbest, Matrix reducedRepV) {

    List<List<ScoredFeaturizedTranslation<IString,String>>> oldNbestLists = nbest.nbestLists();
    List<List<ScoredFeaturizedTranslation<IString,String>>> newNbestLists = new ArrayList<List<ScoredFeaturizedTranslation<IString, String>>>(oldNbestLists.size());

    int nbestId = -1;
    int numNewFeat = reducedRepV.numColumns();
    //System.err.printf("V rows: %d cols: %d\n", reducedRepV.numRows(),
    //    reducedRepV.numColumns());
    for (List<ScoredFeaturizedTranslation<IString, String>> oldNbestlist : oldNbestLists) {
      List<ScoredFeaturizedTranslation<IString, String>> newNbestlist =
           new ArrayList<ScoredFeaturizedTranslation<IString, String>>
                (oldNbestlist.size());
      newNbestLists.add(newNbestlist);
      for (ScoredFeaturizedTranslation<IString, String> anOldNbestlist : oldNbestlist) {
        nbestId++;
        List<FeatureValue<String>> reducedFeatures =
            new ArrayList<FeatureValue<String>>(numNewFeat);
        for (int featId = 0; featId < numNewFeat; featId++) {
          //      System.err.printf("%d:%d\n", featId, nbestId);
          reducedFeatures.add(new FeatureValue<String>(
              (Integer.valueOf(featId)).toString(),
              reducedRepV.get(nbestId, featId)));
        }
        ScoredFeaturizedTranslation<IString, String> newTrans =
            new ScoredFeaturizedTranslation<IString, String>
                (anOldNbestlist.translation, reducedFeatures, 0);
        newNbestlist.add(newTrans);
      }
    }

    return new MosesNBestList(newNbestLists, false);
  }
}


/**
 * @author danielcer
 */
class MCMCELossObjectiveCG extends AbstractNBestOptimizer {

  public MCMCELossObjectiveCG(MERT mert) {
    super(mert);
  }

  public Counter<String> optimize(Counter<String> initialWts) {

    double C = MERT.C;

    Counter<String> sgdWts;
    System.err.println("Begin SGD optimization\n");
    sgdWts = new MCMCELossObjectiveSGD(mert, 50).optimize(initialWts);
    double eval = MERT.evalAtPoint(nbest, sgdWts, emetric);
    double regE = mert.mcmcTightExpectedEval(nbest, sgdWts, emetric);
    double l2wtsSqred = Counters.L2Norm(sgdWts); l2wtsSqred *= l2wtsSqred;
    System.err.printf("SGD final reg objective 0.5||w||_2^2 - C*E(Eval): %e\n",
            -regE);
    System.err.printf("||w||_2^2: %e\n", l2wtsSqred);
    System.err.printf("E(Eval): %e\n", (regE + 0.5*l2wtsSqred)/C);
    System.err.printf("C: %e\n", C);
    System.err.printf("Last eval: %e\n", eval);

    System.err.println("Begin CG optimization\n");
    ObjELossDiffFunction obj = new ObjELossDiffFunction(mert, sgdWts);
    //CGMinimizer minim = new CGMinimizer(obj);
    QNMinimizer minim = new QNMinimizer(obj, 10, true);

    //double[] wtsDense = minim.minimize(obj, 1e-5, obj.initial);
    //            Counter<String> wts = new ClassicCounter<String>();
    //            for (int i = 0; i < wtsDense.length; i++) {
    //                    wts.incrementCount(obj.featureIdsToString.get(i), wtsDense[i]);
    //            }
    while (true) {
      try {
        minim.minimize(obj, 1e-5, obj.initial);
        break;
      } catch (Exception e) {
        //continue;
      }
    }
    Counter<String> wts = obj.getBestWts();

    eval = MERT.evalAtPoint(nbest, wts, emetric);
    regE = mert.mcmcTightExpectedEval(nbest, wts, emetric);
    System.err.printf("CG final reg 0.5||w||_2^2 - C*E(Eval): %e\n", -regE);
    l2wtsSqred = Counters.L2Norm(wts); l2wtsSqred *= l2wtsSqred;
    System.err.printf("||w||_2^2: %e\n", l2wtsSqred);
    System.err.printf("E(Eval): %e\n", (regE + 0.5*l2wtsSqred)/C);
    System.err.printf("C: %e\n", C);
    System.err.printf("Last eval: %e\n", eval);
    return wts;
  }

  static class ObjELossDiffFunction implements DiffFunction, HasInitial {

    final MERT mert;
    final MosesNBestList nbest;
    final Counter<String> initialWts;
    final EvaluationMetric<IString,String> emetric;
    final int domainDimension;

    final List<String> featureIdsToString;
    final double[] initial;
    final double[] derivative;

    public ObjELossDiffFunction(MERT mert, Counter<String> initialWts) {
      this.mert = mert;
      this.nbest = MERT.nbest;
      this.initialWts = initialWts;
      this.emetric = mert.emetric;
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

      Counter<String> wtsCounter = new ClassicCounter<String>();
      for (int i = 0; i < wtsDense.length; i++) {
        wtsCounter.incrementCount(featureIdsToString.get(i), wtsDense[i]);
      }

      MutableDouble expectedEval = new MutableDouble();
      Counter<String> dE = new MCMCDerivative(mert, expectedEval).optimize(wtsCounter);

      for (int i = 0; i < derivative.length; i++) {
        derivative[i] = dE.getCount(featureIdsToString.get(i));
      }
      return derivative;
    }

    public Counter<String> getBestWts() {
      Counter<String> wtsCounter = new ClassicCounter<String>();
      for (int i = 0; i < bestWts.length; i++) {
        wtsCounter.incrementCount(featureIdsToString.get(i), bestWts[i]);
      }
      return wtsCounter;
    }

    @Override
    public double valueAt(double[] wtsDense) {
      Counter<String> wtsCounter = new ClassicCounter<String>();

      for (int i = 0; i < wtsDense.length; i++) {
        if (wtsDense[i] != wtsDense[i]) throw new RuntimeException("Weights contain NaN");
        wtsCounter.incrementCount(featureIdsToString.get(i), wtsDense[i]);
      }
      double eval =  mert.mcmcTightExpectedEval(nbest, wtsCounter, emetric);
      if (eval < bestEval) {
        bestWts = wtsDense.clone();
      }
      return mert.mcmcTightExpectedEval(nbest, wtsCounter, emetric);
    }
    double bestEval = Double.POSITIVE_INFINITY;
    double[] bestWts;
  }
}


/**
 * @author danielcer
 */
class MCMCELossObjectiveSGD extends AbstractNBestOptimizer {

  static final int DEFAULT_MAX_ITER_SGD = 1000;

	int max_iter;

  public MCMCELossObjectiveSGD(MERT mert) {
    this(mert,DEFAULT_MAX_ITER_SGD);
  }

  public MCMCELossObjectiveSGD(MERT mert, int max_iter) {
    super(mert);
		this.max_iter = max_iter;
  }

  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = new ClassicCounter<String>(initialWts);
    double eval = 0;
    double lastExpectedEval = Double.NEGATIVE_INFINITY;
    double lastObj = Double.NEGATIVE_INFINITY;
    double[] objDiffWin = new double[10];

    for (int iter = 0; iter < max_iter; iter++) {
      MutableDouble expectedEval = new MutableDouble();
      MutableDouble objValue = new MutableDouble();

      Counter<String> dE = new MCMCDerivative(mert, expectedEval, objValue).optimize(wts);
      Counters.multiplyInPlace(dE, -1.0* MERT.lrate);
      wts.addAll(dE);

      double ssd = Counters.L2Norm(dE);
      double expectedEvalDiff = expectedEval.doubleValue() - lastExpectedEval;
      double objDiff = objValue.doubleValue() - lastObj;
      lastObj = objValue.doubleValue();
      objDiffWin[iter % objDiffWin.length] = objDiff;
      double winObjDiff = Double.POSITIVE_INFINITY;
      if (iter > objDiffWin.length) {
        double sum = 0;
        for (double anObjDiffWin : objDiffWin) {
          sum += anObjDiffWin;
        }
        winObjDiff = sum/objDiffWin.length;
      }
      lastExpectedEval = expectedEval.doubleValue();
      eval = MERT.evalAtPoint(nbest, wts, emetric);
      System.err.printf("sgd step %d: eval: %e wts ssd: %e E(Eval): %e delta E(Eval): %e obj: %e (delta: %e)\n", iter, eval, ssd, expectedEval.doubleValue(), expectedEvalDiff, objValue.doubleValue(), objDiff);
      if (iter > objDiffWin.length) {
        System.err.printf("objDiffWin: %e\n", winObjDiff);
      }
      if (MERT.MIN_OBJECTIVE_CHANGE_SGD > Math.abs(winObjDiff)) break;
    }
    System.err.printf("Last eval: %e\n", eval);
    return wts;
  }
}


/**
 * @author danielcer
 */
class MCMCELossDirOptimizer extends AbstractNBestOptimizer {

  public MCMCELossDirOptimizer(MERT mert) {
    super(mert);
  }

  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;

    double eval;
    for (int iter = 0; ; iter++) {
      double[] tset = {1e-5, 1e-4, 0.001, 0.01, 0.1, 1, 10, 100, 1000, 1e4, 1e5};
      Counter<String> newWts = new ClassicCounter<String>(wts);
      for (double aTset : tset) {
        MERT.T = aTset;
        MutableDouble md = new MutableDouble();
        Counter<String> dE = new MCMCDerivative(mert, md).optimize(newWts);
        newWts = mert.lineSearch(nbest, newWts, dE, emetric);
        eval = MERT.evalAtPoint(nbest, newWts, emetric);
        System.err.printf("T:%e Eval: %.5f E(Eval): %.5f\n", aTset, eval, md.doubleValue());
      }
      double ssd = MERT.wtSsd(wts, newWts);


      eval = MERT.evalAtPoint(nbest, newWts, emetric);
      System.err.printf("line opt %d: eval: %e ssd: %e\n", iter, eval, ssd);
      if (ssd < MERT.NO_PROGRESS_SSD) break;
      wts = newWts;
    }
    System.err.printf("Last eval: %e\n", eval);
    return wts;
  }
}


/**
 * @author danielcer
 */
class PerceptronOptimizer extends AbstractNBestOptimizer {

  public PerceptronOptimizer(MERT mert) {
    super(mert);
  }

  public Counter<String> optimize(Counter<String> initialWts) {

    List<ScoredFeaturizedTranslation<IString, String>> target = (new HillClimbingMultiTranslationMetricMax<IString, String>(
            emetric)).maximize(nbest);
    Counter<String> targetFeatures = MERT.summarizedAllFeaturesVector(target);
    Counter<String> wts = initialWts;

    while (true) {
      Scorer<String> scorer = new StaticScorer(wts, MERT.featureIndex);
      MultiTranslationMetricMax<IString, String> oneBestSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(
              new ScorerWrapperEvaluationMetric<IString, String>(scorer));
      List<ScoredFeaturizedTranslation<IString, String>> oneBest = oneBestSearch
              .maximize(nbest);
      Counter<String> dir = MERT.summarizedAllFeaturesVector(oneBest);
      Counters.multiplyInPlace(dir, -1.0);
      dir.addAll(targetFeatures);
      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      double ssd = 0;
      for (String k : newWts.keySet()) {
        double diff = wts.getCount(k) - newWts.getCount(k);
        ssd += diff * diff;
      }
      wts = newWts;
      if (ssd < MERT.NO_PROGRESS_SSD)
        break;
    }
    return wts;
  }
}


/**
 * @author danielcer
 */
class PointwisePerceptron extends AbstractNBestOptimizer {

  public PointwisePerceptron(MERT mert) {
    super(mert);
  }

  @SuppressWarnings( { "deprecation", "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

    List<ScoredFeaturizedTranslation<IString, String>> targets = (new HillClimbingMultiTranslationMetricMax<IString, String>(
            emetric)).maximize(nbest);

    Counter<String> wts = new ClassicCounter<String>(initialWts);

    int changes = 0, totalChanges = 0, iter = 0;

    do {
      for (int i = 0; i < targets.size(); i++) {
        // get current classifier argmax
        Scorer<String> scorer = new StaticScorer(wts, MERT.featureIndex);
        GreedyMultiTranslationMetricMax<IString, String> argmaxByScore = new GreedyMultiTranslationMetricMax<IString, String>(
                new ScorerWrapperEvaluationMetric<IString, String>(scorer));
        List<List<ScoredFeaturizedTranslation<IString, String>>> nbestSlice = Arrays
                .asList(nbest.nbestLists().get(i));
        List<ScoredFeaturizedTranslation<IString, String>> current = argmaxByScore
                .maximize(new MosesNBestList(nbestSlice, false));
        Counter<String> dir = MERT.summarizedAllFeaturesVector(Arrays
                .asList(targets.get(i)));
        Counters.subtractInPlace(dir, MERT.summarizedAllFeaturesVector(current));
        Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
        double ssd = MERT.wtSsd(wts, newWts);
        System.err.printf(
                "%d.%d - ssd: %e changes(total: %d iter: %d) eval: %f\n", iter, i,
                ssd, totalChanges, changes, MERT.evalAtPoint(nbest, newWts, emetric));
        wts = newWts;
        if (ssd >= MERT.NO_PROGRESS_SSD) {
          changes++;
          totalChanges++;
        }
      }
      iter++;
    } while (changes != 0);

    return wts;
  }
}


/**
 * @author danielcer
 */
class RandomNBestPoint extends AbstractNBestOptimizer {

	boolean better;

  public RandomNBestPoint(MERT mert, boolean better) {
    super(mert);
		this.better = better;
  }

  @SuppressWarnings( { "deprecation", "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;

    for (int noProgress = 0; noProgress < MERT.NO_PROGRESS_LIMIT;) {
      Counter<String> dir;
      List<ScoredFeaturizedTranslation<IString, String>> rTrans;
      dir = MERT.summarizedAllFeaturesVector(rTrans = (better ? mert.randomBetterTranslations(
              nbest, wts, emetric)
              : mert.randomTranslations(nbest)));

      System.err.printf("Random n-best point score: %.5f\n", emetric
              .score(rTrans));
      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      double eval = MERT.evalAtPoint(nbest, newWts, emetric);
      double ssd = MERT.wtSsd(wts, newWts);
      if (ssd < MERT.NO_PROGRESS_SSD)
        noProgress++;
      else
        noProgress = 0;
      System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd,
              noProgress);
      wts = newWts;
    }
    return wts;
  }
}


/**
 * @author danielcer
 */
class RandomPairs extends AbstractNBestOptimizer {

  public RandomPairs(MERT mert) {
    super(mert);
  }

  @SuppressWarnings( { "deprecation", "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;

    for (int noProgress = 0; noProgress < MERT.NO_PROGRESS_LIMIT;) {
      Counter<String> dir;
      List<ScoredFeaturizedTranslation<IString, String>> rTrans1, rTrans2;

      dir = MERT.summarizedAllFeaturesVector(rTrans1 = mert.randomTranslations(nbest));
      Counter<String> counter = MERT.summarizedAllFeaturesVector(rTrans2 = mert.randomTranslations(nbest));
      Counters.subtractInPlace(dir, counter);

      System.err.printf("Pair scores: %.5f %.5f\n", emetric.score(rTrans1),
              emetric.score(rTrans2));

      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      double eval = MERT.evalAtPoint(nbest, newWts, emetric);

      double ssd = 0;
      for (String k : newWts.keySet()) {
        double diff = wts.getCount(k) - newWts.getCount(k);
        ssd += diff * diff;
      }
      System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd,
              noProgress);
      wts = newWts;
      if (ssd < MERT.NO_PROGRESS_SSD)
        noProgress++;
      else
        noProgress = 0;
    }
    return wts;
  }
}


/**
 * @author danielcer
 */
class RandomAltPairs extends AbstractNBestOptimizer {

	boolean forceBetter;

  public RandomAltPairs(MERT mert, boolean forceBetter) {
    super(mert);
		this.forceBetter = forceBetter;
  }

  @SuppressWarnings( { "deprecation", "unchecked" })
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;

    for (int noProgress = 0; noProgress < MERT.NO_PROGRESS_LIMIT;) {
      Counter<String> dir;
      List<ScoredFeaturizedTranslation<IString, String>> rTrans;
      Scorer<String> scorer = new StaticScorer(wts, MERT.featureIndex);

      dir = MERT.summarizedAllFeaturesVector(rTrans = (forceBetter ? mert.randomBetterTranslations(
              nbest, wts, emetric)
              : mert.randomTranslations(nbest)));
      MultiTranslationMetricMax<IString, String> oneBestSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(
              new ScorerWrapperEvaluationMetric<IString, String>(scorer));
      List<ScoredFeaturizedTranslation<IString, String>> oneBest = oneBestSearch
              .maximize(nbest);
      Counters.subtractInPlace(dir, MERT.summarizedAllFeaturesVector(oneBest));

      System.err.printf("Random alternate score: %.5f \n", emetric
              .score(rTrans));

      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      double eval = MERT.evalAtPoint(nbest, newWts, emetric);

      double ssd = 0;
      for (String k : newWts.keySet()) {
        double diff = wts.getCount(k) - newWts.getCount(k);
        ssd += diff * diff;
      }
      System.err.printf("Eval: %.5f SSD: %e (no progress: %d)\n", eval, ssd,
              noProgress);
      wts = newWts;
      if (ssd < MERT.NO_PROGRESS_SSD)
        noProgress++;
      else
        noProgress = 0;
    }
    return wts;
	}
}
