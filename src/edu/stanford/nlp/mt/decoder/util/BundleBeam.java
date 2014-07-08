package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHash;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHash.Status;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.util.Generics;

/**
 * Implements a beam with hypergraph bundles. The generic type still should be hypothesis.
 * Include an iterator over bundles.
 * 
 * 
 * @author Spence Green
 *
 * @param <Derivation<TK,FV>>
 */
public class BundleBeam<TK,FV> implements Beam<Derivation<TK,FV>> {

  private final RecombinationHash<Derivation<TK,FV>> recombinationHash;
  private final int capacity;
  private int recombined = 0;
  private final int distortionLimit;
  private final int sourceLength;

  private Map<Integer,List<HyperedgeBundle<TK,FV>>> bundles;
  private final RecombinationHistory<Derivation<TK,FV>> recombinationHistory;
  private final RuleGrid<TK, FV> optionGrid;
  private final int coverageCardinality;
  /**
   * 
   * @param capacity
   * @param filter
   * @param optionGrid
   */
  public BundleBeam(int capacity, RecombinationFilter<Derivation<TK,FV>> filter, 
      RuleGrid<TK,FV> optionGrid, int distortionLimit, int coverageCardinality, int maxPhraseLength) {
    this(capacity, filter, optionGrid, null, distortionLimit, coverageCardinality);
  }

  /**
   * 
   * @param capacity
   * @param filter
   * @param optionGrid
   * @param recombinationHistory
   * @param distortionLimit 
   * @param coverageCardinality 
   * @param maxPhraseLength 
   */
  public BundleBeam(int capacity, RecombinationFilter<Derivation<TK,FV>> filter,
      RuleGrid<TK, FV> optionGrid, RecombinationHistory<Derivation<TK,FV>> recombinationHistory, int distortionLimit, 
      int coverageCardinality) {
    recombinationHash = new RecombinationHash<Derivation<TK,FV>>(filter);
    this.capacity = capacity;
    this.recombinationHistory = recombinationHistory;
    this.optionGrid = optionGrid;
    this.sourceLength = optionGrid.gridDimension();
    this.distortionLimit = distortionLimit;
    this.coverageCardinality = coverageCardinality;
  }

  @Override
  public Derivation<TK,FV> put(Derivation<TK,FV> derivation) {
    if (derivation.sourceCoverage.cardinality() != coverageCardinality) {
      throw new RuntimeException("Derivation cardinality does not match beam cardinality");
    }

    final Status status = recombinationHash.update(derivation);
    if (recombinationHistory != null) {
      recombinationHistory.log(recombinationHash.getLastBestOnQuery(),
          recombinationHash.getLastRedundant());
    }

    Derivation<TK,FV> recombinedDerivation = null;
    if (status == Status.COMBINABLE) {
      recombined++;
      recombinedDerivation = derivation;

    } else if(status == Status.BETTER) {
      recombined++;
      recombinedDerivation = recombinationHash.getLastRedundant();
    } 

    return recombinedDerivation;
  }

  /**
   * This method must be called once all hypotheses have been inserted into the beam.
   */
  private void groupBundles() {
    // Group hypotheses by source source coverage
    List<Derivation<TK,FV>> hypothesisList = recombinationHash.hypotheses();
    assert hypothesisList.size() <= capacity : String.format("Beam contents exceeds capacity: %d %d", hypothesisList.size(), capacity);
    Map<CoverageSet,List<Derivation<TK,FV>>> coverageGroups = Generics.newHashMap(hypothesisList.size());
    for (Derivation<TK,FV> hypothesis : hypothesisList) {
      if (coverageGroups.containsKey(hypothesis.sourceCoverage)) {
        coverageGroups.get(hypothesis.sourceCoverage).add(hypothesis);
      } else {
        List<Derivation<TK,FV>> list = Generics.newArrayList();
        list.add(hypothesis);
        coverageGroups.put(hypothesis.sourceCoverage, list);
      }
    }

    // Make hyperedge bundles
    bundles = new HashMap<Integer,List<HyperedgeBundle<TK,FV>>>();
    for (CoverageSet coverage : coverageGroups.keySet()) {
      List<Range> rangeList = ranges(coverage);
      List<Derivation<TK,FV>> itemList = coverageGroups.get(coverage);
      Collections.sort(itemList);
      for (Range range : rangeList) {
        assert range.start <= range.end : "Invalid range";
        List<ConcreteRule<TK,FV>> ruleList = optionGrid.get(range.start, range.end);
        if (ruleList.size() > 0) {
          HyperedgeBundle<TK,FV> bundle = new HyperedgeBundle<TK,FV>(itemList, ruleList);
          if (bundles.containsKey(range.size())) {
            bundles.get(range.size()).add(bundle);
          } else {
            List<HyperedgeBundle<TK,FV>> list = Generics.newLinkedList();
            list.add(bundle);
            bundles.put(range.size(), list);
          }
        }
      }
    }
  }

  /**
   * Fetch all hyperedge bundles for the associated consequent size.
   * 
   * @param n
   * @return
   */
  public List<HyperedgeBundle<TK,FV>> getBundlesForConsequentSize(int n) {
    if (bundles == null) {
      groupBundles();
    }
    int rangeSize = n - coverageCardinality;
    return bundles.containsKey(rangeSize) ? bundles.get(rangeSize) : 
      new ArrayList<HyperedgeBundle<TK,FV>>(1);
  }

  @Override
  public Iterator<Derivation<TK, FV>> iterator() {
    List<Derivation<TK,FV>> hypotheses = recombinationHash.hypotheses();
    Collections.sort(hypotheses);
    return hypotheses.iterator();
  }

  private List<Range> ranges(CoverageSet sourceCoverage) {
    List<Range> rangeList = Generics.newLinkedList();
    int firstCoverageGap = sourceCoverage.nextClearBit(0);
    for (int startPos = firstCoverageGap; startPos < sourceLength; startPos++) {
      int endPosMax = sourceCoverage.nextSetBit(startPos);

      // Re-ordering constraint checks
      // Moses-style hard distortion limit
      if (endPosMax < 0) {
        // TODO(spenceg) weird Moses implementation that allows the first option
        // to cover any span in the source. See MultiBeamDecoder.
        //      if (distortionLimit >= 0 && startPos != firstCoverageGap) {
        if (distortionLimit >= 0) {
          endPosMax = Math.min(firstCoverageGap + distortionLimit + 1,
              sourceLength);
        } else {
          endPosMax = sourceLength;
        }
      }
      for (int endPos = startPos; endPos < endPosMax; endPos++) {
        rangeList.add(new Range(startPos, endPos));
      }
    }
    return rangeList;
  }

  @Override
  public int capacity() {
    return capacity;
  }

  @Override
  public int size() {
    return recombinationHash.size();
  }

  @Override
  public int recombined() {
    return recombined;
  }

  @Override
  public double bestScore() {
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

  @Override
  public Derivation<TK, FV> remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Derivation<TK, FV> removeWorst() {
    throw new UnsupportedOperationException();
  }

  private static class Range {
    public int start;
    public int end;
    public Range(int start, int end) {
      assert start <= end;
      this.start = start;
      this.end = end;
    }
    public int size() { return end-start+1; }

    @Override
    public String toString() {
      return String.format("%d-%d  size: %d", start, end, size());
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if ( ! (other instanceof Range)) {
        return false;
      } else {
        Range o = (Range) other;
        return start == o.start && end == o.end;
      }
    }

    @Override
    public int hashCode() {
      return start*27 ^ (end << 16);
    }
  }
}
