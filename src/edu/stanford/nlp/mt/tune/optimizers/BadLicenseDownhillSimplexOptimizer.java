package edu.stanford.nlp.mt.tune.optimizers;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.optimization.Function;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.MutableDouble;
import edu.stanford.nlp.util.MutableInteger;

/**
 * Downhill simplex minimization algorithm (Nelder and Mead, 1965).
 * 
 * @author Michel Galley
 */
@Deprecated
public class BadLicenseDownhillSimplexOptimizer extends AbstractNBestOptimizer {

  static public final boolean DEBUG = false;
  static public final double BAD_WEIGHT_PENALTY = 1 << 20;
  static public final String SIMPLEX_CLASS_NAME = "edu.stanford.nlp.optimization.extern.BadLicenseDownhillSimplexMinimizer";

  private final boolean szMinusOne;
  private final boolean doRandomSteps;
  private final int minIter;

  public BadLicenseDownhillSimplexOptimizer(MERT mert, int minIter, boolean doRandomSteps) {    
    super(mert);
    System.err.println("Warning this class has been deprecated");
    this.minIter = minIter;
    this.doRandomSteps = doRandomSteps;
    this.szMinusOne = MERT.fixedWts == null;
  }

  public BadLicenseDownhillSimplexOptimizer(MERT mert, boolean doRandomSteps) {
    super(mert);
    System.err.println("Warning this class has been deprecated");
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
    if (szMinusOne) {
      for (int i = 0; i < keys.length - 1; ++i)
        c.setCount(keys[i], x[i]);
      double l1norm = ArrayMath.norm_1(x);
      c.setCount(keys[keys.length - 1], 1.0 - l1norm);
    } else {
      for (int i = 0; i < keys.length; ++i)
        c.setCount(keys[i], x[i]);
    }
    return c;
  }

  private double[] counterToArray(String[] keys, Counter<String> wts) {
    int sz = keys.length;
    double[] x = szMinusOne ? new double[sz - 1] : new double[sz];
    for (int i = 0; i < x.length; ++i)
      x[i] = wts.getCount(keys[i]);
    return x;
  }

  @Override
  public Counter<String> optimize(final Counter<String> initialWts) {
    assert (minIter >= 1);
    Counter<String> wts = initialWts;
    for (int i = 0; i < minIter; ++i) {
      System.err.printf("iter %d (before): %s\n", i, wts.toString());
      wts = optimizeOnce(wts);
      System.err.printf("iter %d: (after): %s\n", i, wts.toString());
    }
    return wts;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static Minimizer<Function> createSimplexMinimizer(Class[] argClasses,
      Object[] args) {
    Minimizer<Function> metric;
    try {
      Class<Minimizer<Function>> cls = (Class<Minimizer<Function>>) Class
          .forName(SIMPLEX_CLASS_NAME);
      Constructor<Minimizer<Function>> ct = cls.getConstructor(argClasses);
      metric = ct.newInstance(args);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
    if (doRandomSteps) {
      Set<String> keySet = new HashSet<String>(Arrays.asList(keys));
      Counter<String> randomStep = randomStep(keySet);
      MERT.normalize(randomStep);
      double[] randx = counterToArray(keys, randomStep);
      ArrayMath.multiplyInPlace(randx, SIMPLEX_RELATIVE_SIZE);
      opt = createSimplexMinimizer(new Class[] { Array.class },
          new Object[] { randx });
      // opt = new DownhillSimplexMinimizer(randx);
    } else {
      opt = createSimplexMinimizer(new Class[] { double.class },
          new Object[] { SIMPLEX_RELATIVE_SIZE });
      // opt = new DownhillSimplexMinimizer(SIMPLEX_RELATIVE_SIZE);
    }

    Function f = new Function() {
      @Override
      public double valueAt(double[] x) {
        Counter<String> xC = arrayToCounter(keys, x);

        double penalty = 0.0;
        for (Map.Entry<String, Double> el : xC.entrySet())
          if (el.getValue() < 0
              && MERT.generativeFeatures.contains(el.getKey()))
            penalty += BAD_WEIGHT_PENALTY;

        double curEval = MERT.evalAtPoint(nbest, xC, emetric) - penalty;

        if (curEval > bestEval.doubleValue())
          bestEval.set(curEval);

        it.set(it.intValue() + 1);
        System.err.printf("current eval(%d): %.5f - best eval: %.5f\n",
            it.intValue(), curEval, bestEval.doubleValue());
        return -curEval;
      }

      @Override
      public int domainDimension() {
        return initialWts.size() - 1;
      }
    };

    double[] wtsA = opt.minimize(f, 1e-4, initx, 1000);
    Counter<String> wts = arrayToCounter(keys, wtsA);
    MERT.normalize(wts);
    System.err.printf("\nDownhill simplex converged at: %s value: %.5f\n",
        wts.toString(), MERT.evalAtPoint(nbest, wts, emetric));
    return wts;
  }
}