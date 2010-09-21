package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHash;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;

/**
 * 
 * @author danielcer
 * 
 */
public class TreeBeam<S extends State<S>> implements Beam<S> {
  public static final String DEBUG_OPT = "TreeBeamDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_OPT, "false"));

  static int hypothesisInsertions = 0;
  static int recombinations = 0;

  private final RecombinationHash<S> recombinationHash;
  private final int capacity;
  private final TreeMap<S, Object> hypotheses;
  private final RecombinationHistory<S> recombinationHistory;

  static {
    if (DEBUG) {
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          displayStats();
        }
      });
    }
  }

  static private void displayStats() {
    System.err.println("TreeBeam stats:");
    System.err.println("------------------------");
    System.err.printf("Hypothesis Insertions: %d\n", hypothesisInsertions);
    System.err.printf("Recombinations: %d Per Insertions: %f %%\n",
        recombinations, recombinations * 100.0 / hypothesisInsertions);
  }

  /**
	 * 
	 */
  public TreeBeam(int capacity, RecombinationFilter<S> filter) {
    this.capacity = capacity;
    this.hypotheses = new TreeMap<S, Object>();
    this.recombinationHash = new RecombinationHash<S>(filter);
    this.recombinationHistory = null;
  }

  /**
	 * 
	 */
  public TreeBeam(int capacity, RecombinationFilter<S> filter,
      RecombinationHistory<S> recombinationHistory) {
    this.capacity = capacity;
    this.hypotheses = new TreeMap<S, Object>();
    this.recombinationHash = new RecombinationHash<S>(filter);
    this.recombinationHistory = recombinationHistory;
  }

  @Override
  synchronized public S put(S hypothesis) {
    hypothesisInsertions++;
    // recombination check
    RecombinationHash.Status status = recombinationHash.queryStatus(hypothesis,
        true);
    if (recombinationHistory != null) {
      recombinationHistory.log(recombinationHash.getLastBestOnQuery(),
          recombinationHash.getLastRedudent());
    }

    if (status == RecombinationHash.Status.COMBINABLE) {
      recombinations++;
      return hypothesis;
    }

    if (status == RecombinationHash.Status.UPDATED) {
      recombinations++;
      hypotheses.remove(recombinationHash.getLastRedudent());
      insertHypothesis(hypothesis);
      return recombinationHash.getLastRedudent();
    }

    assert status == RecombinationHash.Status.NOVEL_INSERTED;

    // beam capacity check for novel hypothesis
    // if passed, insert the new hypothesis
    if (size() < capacity) {
      insertHypothesis(hypothesis);
      return null;
    }

    // beam capacity check failed
    // check if new hypothesis is better than
    // the worst hypothesis on the beam
    S worst = worst();

    if (worst.compareTo(hypothesis) < 0) {
      hypotheses.remove(worst);
      recombinationHash.remove(worst);
      if (recombinationHistory != null) {
        recombinationHistory.remove(worst);
      }
      insertHypothesis(hypothesis);
      return worst;
    } else {
      recombinationHash.remove(hypothesis);
      return hypothesis;
    }
  }

  private void insertHypothesis(S hypothesis) {
    hypotheses.put(hypothesis, null);
  }

  @Override
  public S remove() {
    if (hypotheses.size() == 0)
      return null;
    S best = best();
    hypotheses.remove(best);
    return best;
  }

  private S worst() {
    return hypotheses.firstKey();
  }

  private S best() {
    return hypotheses.lastKey();
  }

  @Override
  public int size() {
    return hypotheses.size();
  }

  @Override
  public int capacity() {
    return capacity;
  }

  @Override
  public Iterator<S> iterator() {
    return hypotheses.descendingKeySet().iterator();
  }

  @Override
  public String toString() {
    StringBuffer sbuf = new StringBuffer();
    sbuf.append("TreeBeam\n");
    sbuf.append("Capacity: ").append(capacity());
    sbuf.append("Size: ").append(size());
    sbuf.append("Hypothesis:\n");
    sbuf.append("--------------------\n");
    for (S hypothesis : this) {
      sbuf.append(hypothesis).append("\n");
    }

    return sbuf.toString();
  }

  @Override
  public double bestScore() {
    S hyp = best();
    if (hyp == null)
      return Double.NaN;
    return hyp.score();
  }

  @Override
  public S removeWorst() {
    if (hypotheses.size() == 0)
      return null;
    S worst = worst();
    hypotheses.remove(worst);
    return worst;
  }

  @Override
  public int recombined() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int preinsertionDiscarded() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int pruned() {
    throw new UnsupportedOperationException();
  }
}
