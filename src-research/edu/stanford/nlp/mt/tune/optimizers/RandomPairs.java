package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;
import java.util.Random;

import edu.stanford.nlp.mt.base.FeatureValues;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * @author danielcer
 */
public class RandomPairs extends AbstractBatchOptimizer {

  public RandomPairs(MERT mert, String[] args) {
    super(mert);
  }

  Random r = new Random(1);
  
  @Override
  public Counter<String> optimize(Counter<String> initialWts) {

    Counter<String> wts = initialWts;
    double epochEval = MERT.evalAtPoint(nbest, wts, emetric);
    
    while (true) {
    for (int idx = 0; idx <  nbest.nbestLists().size(); idx++) {
      Counter<String> dir;
      //List<ScoredFeaturizedTranslation<IString, String>> rTrans1, rTrans2;
      System.err.printf("idx: %d\n", idx);
      int j1 = r.nextInt(nbest.nbestLists().get(idx).size());      
      int j2 = r.nextInt(nbest.nbestLists().get(idx).size());
      
      dir = FeatureValues.toCounter(nbest.nbestLists().get(idx).get(j1).features);
      Counters.subtractInPlace(dir, FeatureValues.toCounter(nbest.nbestLists().get(idx).get(j2).features));
      /*
      dir = MERT.summarizedAllFeaturesVector(rTrans1 = mert
          .randomTranslations(nbest));
      Counter<String> counter = MERT.summarizedAllFeaturesVector(rTrans2 = mert
          .randomTranslations(nbest));
      Counters.subtractInPlace(dir, counter);
      
      System.err.printf("Pair scores: %.5f %.5f\n", emetric.score(rTrans1),
          emetric.score(rTrans2));
      */
      
      Counter<String> newWts = mert.lineSearch(nbest, wts, dir, emetric);
      double eval = MERT.evalAtPoint(nbest, newWts, emetric);

      System.err.printf("Eval: %.5f\n", eval);
      
      wts = newWts;
    }
       double eval = MERT.evalAtPoint(nbest, wts, emetric);
       if (Math.abs(eval - epochEval) < 0.0001) break;  
       epochEval = eval;
    }
    
    return wts;
  }
}