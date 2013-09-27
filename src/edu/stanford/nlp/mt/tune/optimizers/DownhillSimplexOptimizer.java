package edu.stanford.nlp.mt.tune.optimizers;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.optimization.Function;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;


/**
 * MERT via downhill simplex 
 * 
 * @author Daniel Cer
 */
public class DownhillSimplexOptimizer extends AbstractNBestOptimizer {


  public DownhillSimplexOptimizer(MERT mert, String... fields) {
    super(mert);
    
    System.err.println("Downhill simplex training");    
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = new ClassicCounter<String>(initialWts);
        
    // create a mapping between weight names and optimization
    // weight vector positions
    String[] weightNames = new String[initialWts.size()];
    double[] initialWtsArr = new double[initialWts.size()];

    int nameIdx = 0;
    for (String feature : wts.keySet()) {
      initialWtsArr[nameIdx] = wts.getCount(feature);
      weightNames[nameIdx++] = feature;
    }

    Minimizer<Function> dhsm = new DownhillSimplexMinimizer();

    MERTObjective mo = new MERTObjective(weightNames);
    
    double initialValueAt = mo.valueAt(initialWtsArr);
    if (initialValueAt == Double.POSITIVE_INFINITY
        || initialValueAt != initialValueAt) {
      System.err
          .printf("Initial Objective is infinite/NaN - normalizing weight vector");
      double normTerm = Counters.L2Norm(wts);
      for (int i = 0; i < initialWtsArr.length; i++) {
        initialWtsArr[i] /= normTerm;
      }
    }
    
    double initialObjValue = mo.valueAt(initialWtsArr);

    System.err.println("Initial Objective value: " + initialObjValue);
    double newX[] = dhsm.minimize(mo, 1e-6, initialWtsArr); // new
                                                           // double[wts.size()]

    Counter<String> newWts = new ClassicCounter<String>();
    for (int i = 0; i < weightNames.length; i++) {
      newWts.setCount(weightNames[i], newX[i]);
    }

    double finalObjValue = mo.valueAt(newX);
    
    System.err.println("Final Objective value: " + finalObjValue);
    double metricEval = MERT.evalAtPoint(nbest, newWts, emetric);
    MERT.updateBest(newWts, metricEval);
    return newWts;
  }

  class MERTObjective implements Function {
    final String[] weightNames;
    
    public MERTObjective(String[] weightNames) {
      this.weightNames = weightNames;
    }

    private Counter<String> vectorToWeights(double[] x) {
      Counter<String> wts = new ClassicCounter<String>();
      for (int i = 0; i < weightNames.length; i++) {
        wts.setCount(weightNames[i], x[i]);
      }
      return wts;
    }
    
    @Override
    public double valueAt(double[] x) {
      Counter<String> wts = vectorToWeights(x);
      
      return -MERT.evalAtPoint(nbest, wts, emetric);
    }
      
    @Override
    public int domainDimension() {
      return weightNames.length;
    }
  }
}