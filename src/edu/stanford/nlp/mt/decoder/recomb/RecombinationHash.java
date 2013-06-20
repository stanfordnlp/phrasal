package edu.stanford.nlp.mt.decoder.recomb;

import java.util.HashMap;
import java.util.List;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.State;
import edu.stanford.nlp.util.Generics;

/**
 * Implements hypothesis recombination according to the specified recombination
 * filter.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <S>
 */
public class RecombinationHash<S extends State<S>> {
  public static String DEBUG_OPT = "RecombinationHashDebug";
  public static boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_OPT, "false"));
  public static boolean DETAILED_DEBUG = false;
  private static int expensiveComparisons = 0;
  private static int comparisons = 0;
  private static int equalityExpensiveComparisions = 0;

  private final HashMap<FilterWrappedHypothesis, FilterWrappedHypothesis> recombinationHash;
  // private
  final RecombinationFilter<S> filter;

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
    System.err.println("RecombinationHash stats:");
    System.err.println("------------------------");
    System.err.printf("Comparisons: %d\n", comparisons);
    System.err.printf("Expensive Comparisions: %d (%f %%)\n",
        expensiveComparisons, (expensiveComparisons * 100.0 / comparisons));
    System.err
        .printf(
            "Filter Equality Expensive Comparisions: %d (%f %% total) (%f %% expensive)\n",
            equalityExpensiveComparisions,
            (equalityExpensiveComparisions * 100.0 / comparisons),
            (equalityExpensiveComparisions * 100.0 / expensiveComparisons));
  }

  /**
	 * 
	 */
  public RecombinationHash(RecombinationFilter<S> filter) {
    this.recombinationHash = new HashMap<FilterWrappedHypothesis, FilterWrappedHypothesis>();
    this.filter = filter;
  }

  /**
   * Result of queryStatus()
   * 
   * NOVEL -- The hypothesis is novel and was inserted into the table.
   * COMBINABLE -- The hypothesis could be combined with a better hypothesis
   * SELF -- Hypothesis is already in the table.
   * BETTER -- The hypothesis could be combined with a worse hypothesis
   */
  public enum Status {
    NOVEL, COMBINABLE, SELF, BETTER
  };

  public int size() {
    return recombinationHash.size();
  }

  public boolean isBest(S hypothesis) {
    FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(
        hypothesis, filter);
    FilterWrappedHypothesis filterEquivWrappedHyp = recombinationHash
        .get(wrappedHyp);
    if (filterEquivWrappedHyp == null)
      return false;
    return filterEquivWrappedHyp.hypothesis == hypothesis;
  }

  /**
	 * Query the status of hypothesis and update if necessary. Return
	 * the re-combined hypothesis, if any.
	 */
  @SuppressWarnings("rawtypes")
  public Status update(S hypothesis) {
    if (filter instanceof NoRecombination) {
      return Status.NOVEL;
    }

    FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(
        hypothesis, filter);
    FilterWrappedHypothesis filterEquivWrappedHyp = recombinationHash
        .get(wrappedHyp);

    if (DETAILED_DEBUG) {
      if (filterEquivWrappedHyp != null) {
        
        Hypothesis h1 = (Hypothesis) hypothesis;
        Hypothesis h2 = (Hypothesis) filterEquivWrappedHyp.hypothesis;
        System.err.printf("Recombining: %d with %d scores %.3f %.3f\n",
            Math.min(h1.id, h2.id), Math.max(h1.id, h2.id),
            Math.min(h1.score(), h2.score()), Math.max(h1.score(), h2.score()));
      }
    }
    if (filterEquivWrappedHyp == null) {
      lastBestOnQuery = hypothesis;
      lastRedundantOnQuery = null;
      recombinationHash.put(wrappedHyp, wrappedHyp);
      return Status.NOVEL;
    }
    if (hypothesis == filterEquivWrappedHyp.hypothesis) {
      lastBestOnQuery = hypothesis;
      lastRedundantOnQuery = null;
      return Status.SELF;
    }
    if (hypothesis.score() > filterEquivWrappedHyp.hypothesis.score()) {
      lastRedundantOnQuery = filterEquivWrappedHyp.hypothesis;
      lastBestOnQuery = hypothesis;
      filterEquivWrappedHyp.hypothesis = hypothesis;
      return Status.BETTER;
    }

    lastRedundantOnQuery = hypothesis;
    lastBestOnQuery = filterEquivWrappedHyp.hypothesis;
    return Status.COMBINABLE;
  }

  private S lastBestOnQuery;
  private S lastRedundantOnQuery;

  public S getLastRedundant() {
    return lastRedundantOnQuery;
  }

  public S getLastBestOnQuery() {
    return lastBestOnQuery;
  }

  /**
	 * 
	 */
  public void put(S hypothesis) {
    if (filter instanceof NoRecombination) {
      return;
    }
    FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(
        hypothesis, filter);
    recombinationHash.put(wrappedHyp, wrappedHyp);
  }

  public void remove(S hypothesis) {
    remove(hypothesis, false);
  }

  public void remove(S hypothesis, boolean missingOkay) {
    if (filter instanceof NoRecombination) {
      return;
    }
    FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(
        hypothesis, filter);
    FilterWrappedHypothesis filterEquivWrappedHyp = recombinationHash
        .get(wrappedHyp);
    if (filterEquivWrappedHyp == null) {
      if (missingOkay)
        return;
      throw new RuntimeException("hypothesis not found in recombination hash");
    }
    if (hypothesis == filterEquivWrappedHyp.hypothesis)
      recombinationHash.remove(wrappedHyp);
  }

  public class FilterWrappedHypothesis {
    public S hypothesis;
    public final RecombinationFilter<S> filter;
    private final long hashCode;

    public FilterWrappedHypothesis(S hyp, RecombinationFilter<S> filter) {
      this.hypothesis = hyp;
      this.filter = filter;
      hashCode = filter.recombinationHashCode(hypothesis);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {

      comparisons++;
      if (!(o instanceof RecombinationHash.FilterWrappedHypothesis)) {
        return false;
      }

      FilterWrappedHypothesis wrappedHyp = (FilterWrappedHypothesis) o;
      if (wrappedHyp.hypothesis == this.hypothesis) {
        return true;
      }

      if (wrappedHyp.longHashCode() != this.longHashCode()) {
        return false;
      }

      boolean isCombinable = filter.combinable(this.hypothesis,
          wrappedHyp.hypothesis);

      if (DEBUG) {
        expensiveComparisons++;
        if (isCombinable) {
          equalityExpensiveComparisions++;
        }
      }
      return isCombinable;
    }

    private long longHashCode() {
      return hashCode;
    }

    @Override
    public int hashCode() {
      return (int) (longHashCode() >> 32);
    }
  }

  /**
   * Get the list of best hypotheses.
   * 
   * @return
   */
  public List<S> hypotheses() {
    List<S> hypotheses = Generics.newArrayList(recombinationHash.size());
    for (FilterWrappedHypothesis fwh : recombinationHash.keySet()) {
      hypotheses.add(fwh.hypothesis);
    }
    return hypotheses;
  }
}
