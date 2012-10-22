package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.FeatureValueCollection;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * 1-best MIRA training with hope/fear translations. See Watanabe et al. (2007), 
 * Chiang (2012), and Eidelman (2012).
 * 
 * 
 * TODO:
 *  * Weight vector averaging (Chiang, 2008)
 *  * Oracle BLEU score (Watanabe, 2007)
 *  * Iterative parameter mixing (McDonald, 2010)
 *  * Other PA updates, or extension to k-best MIRA?
 *  
 * @author Spence Green
 */
public class MIRAHopeFearOptimizer extends AbstractNBestOptimizer {
  final double C;
  final int numEpochs; 
  public static final double DEFAULT_C = 0.01;
  public static final int DEFAULT_EPOCHS = 10;
  
  private static final int BLEU_ORDER = 4;
  
  public MIRAHopeFearOptimizer(MERT mert) {
    super(mert);
    C = DEFAULT_C;
    numEpochs = DEFAULT_EPOCHS;
  }
  
  public MIRAHopeFearOptimizer(MERT mert, double C) {
    super(mert);
    this.C = C;                   
    numEpochs = DEFAULT_EPOCHS;
  }
  
  public MIRAHopeFearOptimizer(MERT mert, String... args) {
    super(mert);
    C = (args.length == 1) ? Double.parseDouble(args[0]) : DEFAULT_C;
    numEpochs = (args.length == 2) ? Integer.parseInt(args[1]) : DEFAULT_EPOCHS;    
  }
  
  @Override
  public boolean doNormalization() {
    return false;
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    System.err.printf("1-best MIRA optimization with C: %e%n", C);
    
    Counter<String> wts = new ClassicCounter<String>(initialWts);
    final List<List<ScoredFeaturizedTranslation<IString, String>>> tuningLists = nbest.nbestLists();
    final int tuneSetSize = tuningLists.size();
    for (int e = 0; e < numEpochs; ++e) {
      for (int i = 0; i < tuneSetSize; ++i) {
        List<ScoredFeaturizedTranslation<IString, String>> nbestList = tuningLists.get(i);
        List<Sequence<IString>> references = mert.references.get(i);
        Derivation dHope = getBestHopeDerivation(nbestList, references);
        Derivation dFear = getBestFearDerivation(nbestList, references);
        double margin = dFear.score - dHope.score;
        double cost = dHope.cost - dFear.cost;
        double loss = margin + cost;
        
        if (loss > 0.0) {
          // Compute the PA-I update, which is the loss divided by the 
          double d = updatePAI(dHope, dFear, loss);
          d = Math.min(C, d);
          Counters.addInPlace(wts, d);
        }
      }
    }
    
    double weightsDiff = Counters.L1Norm(Counters.diff(wts, initialWts));
    double metricEval = MERT.evalAtPoint(nbest, wts, emetric);
    System.err.printf("Eval Score: %e Weights diff: %e%n", metricEval, weightsDiff);
    
    // TODO(spenceg): Implement IPM reporting here. Each shard should report back its weights
    // vector, and these should be averaged to obtain the next starting point.
    MERT.updateBest(wts, metricEval, true);
    
    return wts;
  }
  
  /**
   * PA-I update of Crammer and Singer (2006).
   * 
   * @param dHope
   * @param dFear
   * @param loss
   * @return
   */
  private double updatePAI(Derivation dHope, Derivation dFear, double loss) {
    FeatureValueCollection<String> hopeFeatures = dHope.hypothesis.features;
    Counter<String> fearFeatures = OptimizerUtils.featureValueCollectionToCounter(dFear.hypothesis.features);
    assert hopeFeatures.size() == fearFeatures.size();
    double sumSquares = 0.0;
    for (FeatureValue<String> hopeFeature : hopeFeatures) {
      assert fearFeatures.keySet().contains(hopeFeature.name);
      double featureDiff = hopeFeature.value - fearFeatures.getCount(hopeFeature.name);
      sumSquares += featureDiff*featureDiff;
    }
    return loss / sumSquares;
  }

  /**
   * Max model score - cost
   * 
   * @param nbestList
   * @return
   */
  private Derivation getBestHopeDerivation(List<ScoredFeaturizedTranslation<IString, String>> nbestList,
      List<Sequence<IString>> references) {
    return getBestDerivation(nbestList, references, 1.0);
  }
  
  /**
   * Gets either a hope or fear derivation dependending on the sign of the loss.
   * 
   * @param nbestList
   * @param lossSign
   * @return
   */
  private Derivation getBestDerivation(List<ScoredFeaturizedTranslation<IString, String>> nbestList, 
      List<Sequence<IString>> references,
      double lossSign) {
    ScoredFeaturizedTranslation<IString,String> d = null;
    double dScore = 0.0;
    double dLoss = 0.0;
    double maxScore = Double.MIN_VALUE;
    for (ScoredFeaturizedTranslation<IString,String> hypothesis : nbestList) {
      // TODO: What is the orientation of the model score? Has it been multiplied by -1?
      // TODO: Replace the Lin and Och smooth BLEU with the Watanabe metric.
      double cost = 1.0 - BLEUMetric.computeLocalSmoothScore(hypothesis.translation, references, BLEU_ORDER);
      double modelScore = hypothesis.score;
      double score = modelScore + (lossSign * cost);
      if (score > maxScore) {
        d = hypothesis;
        dScore = modelScore;
        dLoss = cost;
        maxScore = score;
      }
    }
    return new Derivation(d, dScore, dLoss);
  }
  
  /**
   * Max model score + cost
   * 
   * @param nbestList
   * @return
   */
  private Derivation getBestFearDerivation(List<ScoredFeaturizedTranslation<IString, String>> nbestList,
      List<Sequence<IString>> references) {
    return getBestDerivation(nbestList, references, -1.0);
  }
  
  
  private static class Derivation {
    public ScoredFeaturizedTranslation<IString,String> hypothesis;
    public double score;
    public double cost;
    
    public Derivation(ScoredFeaturizedTranslation<IString,String> hypothesis, 
        double score, double cost) {
      this.hypothesis = hypothesis;
      this.score = score;
      this.cost = cost;
    } 
  }
}
