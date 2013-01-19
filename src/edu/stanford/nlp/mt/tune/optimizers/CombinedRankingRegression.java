package edu.stanford.nlp.mt.tune.optimizers;

import java.util.Random;

import edu.stanford.nlp.classify.CorrelationLinearRegressionObjectiveFunction;
// import edu.stanford.nlp.classify.LinearRegressionObjectiveFunction;
import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.classify.LogisticObjectiveFunction;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * CombinedRankingRegression
 * 
 * Motivated by Sculley's 2010 Google paper "Combined Regression and Ranking"
 * 
 * @author daniel
 *
 */
public class CombinedRankingRegression extends AbstractNBestOptimizer {
  final PairwiseRankingOptimizer ranking;
  final LinearRegressionRankingOptimizer regression;
  
  static boolean updatedBestOnce = false;
  
  public CombinedRankingRegression(MERT mert, String... fields) {
    super(mert);
    ranking = new PairwiseRankingOptimizer(mert);
    regression = new LinearRegressionRankingOptimizer(mert);    
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = new ClassicCounter<String>(initialWts);
    Counters.normalize(wts);
    
    Index<String> sharedFeatureIndex = new HashIndex<String>();
    double seedSeed = Math.abs(Counters.max(wts));
    long seed = (long)Math.exp(Math.log(seedSeed) + Math.log(Long.MAX_VALUE));
    RVFDataset<String, String> rankingData = ranking.getSamples(new Random(seed), sharedFeatureIndex);
    RVFDataset<Double, String> regressionData = regression.buildDataSet(sharedFeatureIndex, false);
    
    LogPrior lprior = new LogPrior();
    lprior.setSigma(PairwiseRankingOptimizer.DEFAULT_L2SIGMA);
    LogisticObjectiveFunction lof = new LogisticObjectiveFunction(rankingData.numFeatureTypes(), rankingData.getDataArray(), rankingData.getValuesArray(), rankingData.getLabelsArray(), lprior);
    // LinearRegressionObjectiveFunction<String> lrf = new LinearRegressionObjectiveFunction<String>(regressionData, LinearRegressionRankingOptimizer.DEFAULT_REGULARIZER_COEFFICIENT);
    CorrelationLinearRegressionObjectiveFunction<String> lrf = new CorrelationLinearRegressionObjectiveFunction<String>(regressionData, LinearRegressionRankingOptimizer.DEFAULT_REGULARIZER_COEFFICIENT);
    System.err.printf("Ranking training data instances: %d\n", rankingData.size());
    System.err.printf("Regression training data instances: %d\n", regressionData.size());
    double reblanceFactor = regressionData.size()/(double)rankingData.size();
    System.err.printf("Rebalancing Factor: %e\n", reblanceFactor);
    CombinedObjective obj = new CombinedObjective(lof, lrf, reblanceFactor, 0.999);
    
    Minimizer<DiffFunction> minimizer = new QNMinimizer(obj);
    double[] weightsArr = minimizer.minimize(obj, 1e-4, new double[sharedFeatureIndex.size()]);
    
    Counter<String> outWts = new ClassicCounter<String>();
    
    for (int i = 0; i < weightsArr.length; i++) {
      if (!sharedFeatureIndex.get(i).startsWith("||| BIAS ")) {
        outWts.setCount(sharedFeatureIndex.get(i), weightsArr[i]);
      }
    }
    synchronized (MERT.bestWts) {
      if (!updatedBestOnce) {
        System.err.println("Force updating weights (once)");
        double metricEval = MERT.evalAtPoint(nbest, outWts, emetric);
        MERT.updateBest(outWts, metricEval, true);
        updatedBestOnce = true;
      }
    }
    return outWts;
  } 
  

}

class CombinedObjective extends AbstractCachingDiffFunction {
  final AbstractCachingDiffFunction o1, o2;
  final double alpha;
  final double rebalance;
  
  public CombinedObjective(AbstractCachingDiffFunction o1, AbstractCachingDiffFunction o2,  double rebalance, double alpha) {
    this.o1 = o1;
    this.o2 = o2;
    if (o1.domainDimension() != o2.domainDimension()) {
      throw new RuntimeException("o1.domainDimension() != o2.domainDimension()");
    }
    this.alpha = alpha;
    this.rebalance = rebalance;
  }
  
  @Override
  public int domainDimension() {
    return o1.domainDimension();
  }

  @Override
  protected void calculate(double[] x) {
    double[] do1 = o1.derivativeAt(x);
    double[] do2 = o2.derivativeAt(x);

    value = rebalance*alpha*o1.valueAt(x) + (1-alpha)*o2.valueAt(x);
    
    for (int i = 0; i < derivative.length; i++) {
       derivative[i] = rebalance*alpha*do1[i] + (1-alpha)*do2[i];  
    }
  }
  
}
