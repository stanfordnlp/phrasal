package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.tune.MERT;
import edu.stanford.nlp.mt.tune.BatchOptimizer;
import edu.stanford.nlp.stats.Counter;

/**
 * @author Michel Galley
 */
public class SequenceOptimizer extends AbstractBatchOptimizer {

  private static final double MIN_OBJECTIVE_CHANGE = 1e-5;

  private final List<BatchOptimizer> opts;
  private final boolean loop;

  public SequenceOptimizer(MERT mert, List<BatchOptimizer> opts, boolean loop) {
    super(mert);
    this.opts = opts;
    this.loop = loop;
  }

  @Override
  public Counter<String> optimize(Counter<String> initialWts) {
    Counter<String> wts = initialWts;
    for (BatchOptimizer opt : opts) {

      boolean done = false;

      while (!done) {
        Counter<String> newWts = opt.optimize(wts);

        double wtSsd = MERT.wtSsd(newWts, wts);

        double oldE = MERT.evalAtPoint(nbest, wts, emetric);
        double newE = MERT.evalAtPoint(nbest, newWts, emetric);
        // MERT.updateBest(newWts, -newE);

        boolean worse = oldE > newE;
        done = Math.abs(oldE - newE) <= MIN_OBJECTIVE_CHANGE || !loop || worse;

        System.err.printf(
            "seq optimizer: %s -> %s (%s) ssd: %f done: %s opt: %s\n", oldE,
            newE, newE - oldE, wtSsd, done, opt.toString());

        if (worse)
          System.err.printf("WARNING: negative objective change!");
        else
          wts = newWts;
      }
    }
    return wts;
  }
}