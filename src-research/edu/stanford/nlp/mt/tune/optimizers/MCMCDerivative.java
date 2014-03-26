package edu.stanford.nlp.mt.tune.optimizers;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.DenseScorer;
import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.MutableDouble;

/**
 * @author danielcer
 */
public class MCMCDerivative extends AbstractBatchOptimizer {

  MutableDouble expectedEval;
  MutableDouble objValue;

  public MCMCDerivative(MERT mert) {
    this(mert, null);
  }

  public MCMCDerivative(MERT mert, MutableDouble expectedEval) {
    this(mert, expectedEval, null);
  }

  public MCMCDerivative(MERT mert, MutableDouble expectedEval,
      MutableDouble objValue) {
    super(mert);
    this.expectedEval = expectedEval;
    this.objValue = objValue;
  }

  @Override
  public Counter<String> optimize(Counter<String> wts) {

    double C = MERT.C;

    System.err.printf("MCMC weights:\n%s\n\n", Counters.toString(wts, 35));

    // for quick mixing, get current classifier argmax
    System.err.println("finding argmax");
    List<ScoredFeaturizedTranslation<IString, String>> argmax = MERT
        .transArgmax(nbest, wts), current = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
        argmax);

    // recover which candidates were selected
    System.err.println("recovering cands");
    int argmaxCandIds[] = new int[current.size()];
    Arrays.fill(argmaxCandIds, -1);
    for (int i = 0; i < nbest.nbestLists().size(); i++) {
      for (int j = 0; j < nbest.nbestLists().get(i).size(); j++) {
        if (current.get(i) == nbest.nbestLists().get(i).get(j))
          argmaxCandIds[i] = j;
      }
    }

    Counter<String> dE = new ClassicCounter<String>();
    Scorer<String> scorer = new DenseScorer(wts, MERT.featureIndex);

    double hardEval = emetric.score(argmax);
    System.err.printf("Hard eval: %.5f\n", hardEval);

    // expected value sums
    OpenAddressCounter<String> sumExpLF = new OpenAddressCounter<String>(0.50f);
    double sumExpL = 0.0;
    OpenAddressCounter<String> sumExpF = new OpenAddressCounter<String>(0.50f);
    int cnt = 0;
    double dEDiff; // = Double.POSITIVE_INFINITY;
    double dECosine = 0.0;
    for (int batch = 0; (dECosine < MERT.NO_PROGRESS_MCMC_COSINE || batch < MERT.MCMC_MIN_BATCHES)
        && batch < MERT.MCMC_MAX_BATCHES; batch++) {
      Counter<String> oldDe = new ClassicCounter<String>(dE);

      // reset current to argmax
      current = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
          argmax);

      // reset incremental evaluation object for the argmax candidates
      IncrementalEvaluationMetric<IString, String> incEval = emetric
          .getIncrementalMetric();

      for (ScoredFeaturizedTranslation<IString, String> tran : current)
        incEval.add(tran);

      OpenAddressCounter<String> currentF;
      // = new
      // OpenAddressCounter<String>(MERT.summarizedAllFeaturesVector(current),
      // 0.50f);

      System.err.println("Sampling");

      long time = -System.currentTimeMillis();
      for (int bi = 0; bi < MERT.MCMC_BATCH_SAMPLES; bi++) {
        // gibbs mcmc sample
        if (cnt != 0) // always sample once from argmax
          for (int sentId = 0; sentId < nbest.nbestLists().size(); sentId++) {
            double Z = 0;
            double[] num = new double[nbest.nbestLists().get(sentId).size()];
            int pos = -1;
            for (ScoredFeaturizedTranslation<IString, String> trans : nbest
                .nbestLists().get(sentId)) {
              pos++;
              Z += num[pos] = Math.exp(scorer
                  .getIncrementalScore(trans.features));
              // System.err.printf("%d: featureOnly score: %g\n", pos,
              // Math.exp(scorer.getIncrementalScore(trans.features)));
            }
            // System.out.printf("%d:%d - Z: %e\n", bi, sentId, Z);
            // System.out.printf("num[]: %s\n", Arrays.toString(num));

            int selection = -1;
            if (Z != 0) {
              double rv = mert.random.nextDouble() * Z;
              for (int i = 0; i < num.length; i++) {
                if ((rv -= num[i]) <= 0) {
                  selection = i;
                  break;
                }
              }
            } else {
              selection = mert.random.nextInt(num.length);
            }

            if (Z == 0) {
              Z = 1.0;
              num[selection] = 1.0 / num.length;
            }
            ErasureUtils.noop(Z);
            // System.out.printf("%d:%d - selection: %d p(%g|f) %g/%g\n", bi,
            // sentId, selection, num[selection]/Z, num[selection], Z);

            // adjust current
            current.set(sentId, nbest.nbestLists().get(sentId).get(selection));
          }

        // collect derivative relevant statistics using sample
        cnt++;

        // adjust currentF & eval
        currentF = new OpenAddressCounter<String>(
            MERT.summarizedAllFeaturesVector(current), 0.50f);
        double eval = emetric.score(current);
        sumExpL += eval;

        System.out.printf("Sample: %d(%d) Eval: %g E(Loss): %g\n", cnt, bi,
            eval, sumExpL / cnt);

        for (Object2DoubleMap.Entry<String> entry : currentF
            .object2DoubleEntrySet()) {
          String k = entry.getKey();
          double val = entry.getDoubleValue();
          sumExpF.incrementCount(k, val);
          sumExpLF.incrementCount(k, val * eval);
        }
      }
      time += System.currentTimeMillis();

      dE = new ClassicCounter<String>(sumExpF);
      Counters.multiplyInPlace(dE, sumExpL / cnt);
      Counters.subtractInPlace(dE, sumExpLF);
      Counters.divideInPlace(dE, -1 * cnt);
      dEDiff = MERT.wtSsd(oldDe, dE);
      dECosine = Counters.dotProduct(oldDe, dE)
          / (Counters.L2Norm(dE) * Counters.L2Norm(oldDe));
      if (Double.isNaN(dECosine))
        dECosine = 0;
      // if (dECosine != dECosine) dECosine = 0;

      System.err.printf("Batch: %d dEDiff: %e dECosine: %g)\n", batch, dEDiff,
          dECosine);
      System.err.printf("Sampling time: %.3f s\n", time / 1000.0);
      System.err.printf("E(loss) = %e\n", sumExpL / cnt);
      System.err
          .printf("E(loss*f):\n%s\n\n",
              Counters.toString(Counters.divideInPlace(
                  new ClassicCounter<String>(sumExpLF), cnt), 35));
      System.err
          .printf("E(f):\n%s\n\n", Counters.toString(
              Counters.divideInPlace(new ClassicCounter<String>(sumExpF), cnt),
              35));
      System.err.printf("dE:\n%s\n\n", Counters.toString(dE, 35));
    }
    System.err.printf("Hard eval: %.5f E(Eval): %.5f diff: %e\n", hardEval,
        sumExpL / cnt, hardEval - sumExpL / cnt);

    double l2wts = Counters.L2Norm(wts);
    double obj = (C != 0 ? 0.5 * l2wts * l2wts - C * sumExpL / cnt : -sumExpL
        / cnt);
    System.err.printf(
        "DRegularized objective 0.5*||w||_2^2 - C * E(Eval): %e\n", obj);
    System.err.printf("C: %e\n", C);
    System.err.printf("||w||_2^2: %e\n", l2wts * l2wts);
    System.err.printf("E(loss) = %e\n", sumExpL / cnt);

    if (expectedEval != null)
      expectedEval.set(sumExpL / cnt);
    if (objValue != null)
      objValue.set(obj);

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