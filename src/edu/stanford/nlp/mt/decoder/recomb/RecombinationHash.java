package edu.stanford.nlp.mt.decoder.recomb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.stanford.nlp.mt.decoder.util.State;


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

  private static final int INITIAL_CAPACITY = 1024;
  
  private final Map<FilterWrappedHypothesis, FilterWrappedHypothesis> recombinationHash;
  // private
  final RecombinationFilter<S> filter;

  /**
	 * 
	 */
  public RecombinationHash(RecombinationFilter<S> filter) {
    this.recombinationHash = new HashMap<>(INITIAL_CAPACITY);
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
  public Status update(S hypothesis) {
    FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(
        hypothesis, filter);
    FilterWrappedHypothesis filterEquivWrappedHyp = recombinationHash
        .get(wrappedHyp);

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
    FilterWrappedHypothesis wrappedHyp = new FilterWrappedHypothesis(hypothesis, filter);
    recombinationHash.put(wrappedHyp, wrappedHyp);
  }

  public void remove(S hypothesis) {
    remove(hypothesis, false);
  }

  public void remove(S hypothesis, boolean missingOkay) {
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
      if (this == o) {
        return true;
      
      } else if (!(o instanceof RecombinationHash.FilterWrappedHypothesis)) {
        return false;

      } else {
        FilterWrappedHypothesis wrappedHyp = (FilterWrappedHypothesis) o;
        if (wrappedHyp.hypothesis == this.hypothesis) {
          return true;
        } else if (hashCode != wrappedHyp.hashCode) {
          return false;
        } else {
          boolean isCombinable = filter.combinable(this.hypothesis, wrappedHyp.hypothesis);
          return isCombinable;
        }
      }
    }

    @Override
    public String toString() {
      return hypothesis.toString();
    }
    
    @Override
    public int hashCode() {
      return (int) (hashCode & 0xffffffff);
    }
  }

  /**
   * Get the list of best hypotheses.
   * 
   * @return
   */
  public List<S> hypotheses() {
    return recombinationHash.keySet().stream().map(fwh -> fwh.hypothesis).collect(Collectors.toList());
  }
  
  @Override
  public String toString() {
    return hypotheses().stream().map(h -> h.toString()).collect(Collectors.joining(" ||| "));
  }
}
