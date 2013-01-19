package edu.stanford.nlp.mt.tune.optimizers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import edu.stanford.nlp.classify.LinearRegressionFactory;
import edu.stanford.nlp.classify.LinearRegressor;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.EvaluationMetricFactory;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * Linear Regression Ranking Optimizer uses linear regression to map
 * decoding model translation hypothesis feature values to their evaluation
 * metric scores. The resulting linear regression model weights can then be
 * used within the decoder to drive translation.
 * 
 * @author daniel cer
 *
 */
public class LinearRegressionRankingOptimizer extends AbstractNBestOptimizer {
  static final double DEFAULT_REGULARIZER_COEFFICIENT = 1.0;
  
  final double regularizerCoefficient;
  
  public LinearRegressionRankingOptimizer(MERT mert, String... fields) {
    super(mert);
    if (fields.length < 1) {
      regularizerCoefficient = DEFAULT_REGULARIZER_COEFFICIENT; 
    } else {
      regularizerCoefficient = Double.parseDouble(fields[0]);
    }
  }

  
  public RVFDataset<Double, String> buildDataset() {
    return buildDataSet(new HashIndex<String>(), true);
  }
  
  public RVFDataset<Double, String> buildDataSet(Index<String> featureIndex, boolean includeBiasFeatures) {
    RVFDataset<Double, String> dataSet = new RVFDataset<Double, String>(10, featureIndex, new HashIndex<Double>());  
    
    List<List<ScoredFeaturizedTranslation<IString,String>>> nbestLists = nbest.nbestLists();
    
    for (int id = 0; id < nbestLists.size(); id++) {
      List<ScoredFeaturizedTranslation<IString, String>> nbestList = nbestLists.get(id);
      
      EvaluationMetric<IString,String> emetric = EvaluationMetricFactory.newMetric(mert.evalMetric, mert.references.subList(id, id+1));
      
      for (ScoredFeaturizedTranslation<IString, String> tran : nbestList) {
        List<ScoredFeaturizedTranslation<IString,String>> trans = 
            new ArrayList<ScoredFeaturizedTranslation<IString,String>>(1);
        trans.add(tran);
        double emetricScore = emetric.score(trans);
        Counter<String> features = OptimizerUtils.featureValueCollectionToCounter(tran.features);
        if (includeBiasFeatures) {
          features.setCount("||| BIAS |||", 1.0);
          features.setCount(String.format("||| BIAS ID %d |||", id), 1.0);
        }
        dataSet.add(new RVFDatum<Double, String>(features, emetricScore));
      }
    }
    
    return dataSet;    
  }
  
  @Override
  public Counter<String> optimize(Counter<String> initialWts) {    

    RVFDataset<Double, String> dataSet = buildDataset();
    LinearRegressionFactory<String> lrf = new LinearRegressionFactory<String>();
    LinearRegressor<String> regressor = (LinearRegressor<String>)lrf.train(dataSet, regularizerCoefficient);
    Counter<String> weights = regressor.getFeatureWeights();
    
    
    synchronized (MERT.bestWts) {       
      for (String key : new HashSet<String>(weights.keySet())) {
        if (key.startsWith("||| BIAS ")) {
          weights.remove(key);
        }
      }      
      System.err.println("Force weight update");
      double metricEval = MERT.evalAtPoint(nbest, weights, emetric);
      MERT.updateBest(weights, metricEval, true);      
    }
    return weights;       
  }

  
}
