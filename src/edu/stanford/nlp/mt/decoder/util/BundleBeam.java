package edu.stanford.nlp.mt.decoder.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHash;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHash.Status;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.TwoDimensionalMap;

/**
 * Implements a beam with hypergraph bundles. The generic type still should be hypothesis.
 * Include an iterator over bundles.
 * 
 * 
 * @author Spence Green
 *
 * @param <Hypothesis<TK,FV>>
 */
public class BundleBeam<TK,FV> implements Beam<Hypothesis<TK,FV>> {

  private final RecombinationHash<Hypothesis<TK,FV>> recombinationHash;
  private final int capacity;
  private int recombined = 0;
  private final int distortionLimit;
  private final int sourceLength;

  private final TwoDimensionalMap<Range,CoverageSet,HyperedgeBundle<TK,FV>> bundles;
  private final RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory;
  private final OptionGrid<TK, FV> optionGrid;
  private final int coverageCardinality;
  private Map<CoverageSet,List<Range>> rangeCache;
  private Map<Hypothesis<TK,FV>, List<HyperedgeBundle<TK,FV>>> hypothesisMap;
  /**
   * 
   * @param capacity
   * @param filter
   * @param optionGrid
   */
  public BundleBeam(int capacity, RecombinationFilter<Hypothesis<TK,FV>> filter, 
      OptionGrid<TK,FV> optionGrid, int distortionLimit, int coverageCardinality, int maxPhraseLength) {
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
  public BundleBeam(int capacity, RecombinationFilter<Hypothesis<TK,FV>> filter,
      OptionGrid<TK, FV> optionGrid, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory, int distortionLimit, 
      int coverageCardinality) {
    recombinationHash = new RecombinationHash<Hypothesis<TK,FV>>(filter);
    this.capacity = capacity;
    this.recombinationHistory = recombinationHistory;
    this.optionGrid = optionGrid;
    this.sourceLength = optionGrid.gridDimension();
    this.distortionLimit = distortionLimit;
    this.coverageCardinality = coverageCardinality;
    this.bundles = new TwoDimensionalMap<Range,CoverageSet,HyperedgeBundle<TK,FV>>();
    this.rangeCache = Generics.newHashMap();
    this.hypothesisMap = Generics.newHashMap(capacity);
  }

  /**
   * This method must be called once all hypotheses have been inserted into the beam.
   */
  public void lock() {
    for (HyperedgeBundle<TK,FV> bundle : bundles.values()) {
      bundle.lock();
    }
    
    // Free intermediate data structures
    rangeCache = null;
    hypothesisMap = null;
  }

  @Override
  public Hypothesis<TK,FV> put(Hypothesis<TK,FV> hypothesis) {
    if (hypothesis.sourceCoverage.cardinality() != coverageCardinality) {
      throw new RuntimeException("Hypothesis cardinality does not match beam cardinality");
    }

    final Status status = recombinationHash.update(hypothesis);
    if (recombinationHistory != null) {
      recombinationHistory.log(recombinationHash.getLastBestOnQuery(),
          recombinationHash.getLastRedundant());
    }
    
    Hypothesis<TK,FV> recombinedHypothesis = null;
    if (status == Status.COMBINABLE) {
      recombined++;
      recombinedHypothesis = hypothesis;

    } else if(status == Status.BETTER) {
      recombined++;
      recombinedHypothesis = recombinationHash.getLastRedundant();
      update(recombinedHypothesis, hypothesis);
      
    } else if(status == Status.NOVEL){
      insert(hypothesis);
    }

    return recombinedHypothesis;
  }

  private void update(Hypothesis<TK, FV> oldHypothesis,
      Hypothesis<TK, FV> newHypothesis) {
    if ( ! hypothesisMap.containsKey(oldHypothesis)) {
      throw new RuntimeException("Missing recombined hypothesis");
    }
    
    List<HyperedgeBundle<TK,FV>> bundleList = hypothesisMap.get(oldHypothesis);
    for (HyperedgeBundle<TK,FV> bundle : bundleList) {
      boolean wasUpdated = bundle.updateItem(oldHypothesis, newHypothesis);
      if ( ! wasUpdated) {
        throw new RuntimeException("Hypothesis not found in bundle");
      }
    }
    hypothesisMap.put(newHypothesis, bundleList);
    hypothesisMap.remove(oldHypothesis);
  }

  /**
   * Insert the hypothesis into the beam and associated lookup structures.
   * 
   * @param hypothesis
   */
  private void insert(Hypothesis<TK, FV> hypothesis) {
    List<Range> rangeList = ranges(hypothesis);
    List<HyperedgeBundle<TK,FV>> bundleList = Generics.newLinkedList();
    for (Range range : rangeList) {
      int leftEdge = range.start;
      int rightEdge = range.end;
      assert leftEdge <= rightEdge : "Invalid range";
      if (bundles.contains(range, hypothesis.sourceCoverage)) {
        HyperedgeBundle<TK,FV> bundle = bundles.get(range, hypothesis.sourceCoverage);
        bundle.add(hypothesis);
        bundleList.add(bundle);
        
      } else {
        List<ConcreteTranslationOption<TK,FV>> ruleList = optionGrid.get(leftEdge, rightEdge);
        if (ruleList.size() > 0) {
          HyperedgeBundle<TK,FV> bundle = new HyperedgeBundle<TK,FV>(ruleList);
          bundle.add(hypothesis);
          bundles.put(range, hypothesis.sourceCoverage, bundle);
          bundleList.add(bundle);
        }
      }
    }
    hypothesisMap.put(hypothesis, bundleList);
  }

  /**
   * Fetch all hyperedge bundles for the associated consequent size.
   * 
   * @param n
   * @return
   */
  public List<HyperedgeBundle<TK,FV>> getBundlesForConsequentSize(int n) {
    int rangeSize = n - coverageCardinality;
    List<HyperedgeBundle<TK,FV>> bundlesForRange = Generics.newLinkedList();
    for (Range range : bundles.firstKeySet()) {
      if (rangeSize == range.size()) {
        for (HyperedgeBundle<TK,FV> bundle : bundles.get(range).values()) {
          bundlesForRange.add(bundle);
        }
      }
    }
    return bundlesForRange;
  }

  @Override
  public Iterator<Hypothesis<TK, FV>> iterator() {
    List<Hypothesis<TK,FV>> hypotheses = recombinationHash.hypotheses();
    Collections.sort(hypotheses);
    return hypotheses.iterator();
  }

  private List<Range> ranges(Hypothesis<TK, FV> hyp) {
    if (rangeCache.containsKey(hyp.sourceCoverage)) {
      return rangeCache.get(hyp.sourceCoverage);
    }
    List<Range> rangeList = Generics.newLinkedList();
    int firstCoverageGap = hyp.sourceCoverage.nextClearBit(0);
    for (int startPos = firstCoverageGap; startPos < sourceLength; startPos++) {
      int endPosMax = hyp.sourceCoverage.nextSetBit(startPos);

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
    rangeCache.put(hyp.sourceCoverage, rangeList);
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
  public Hypothesis<TK, FV> remove() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Hypothesis<TK, FV> removeWorst() {
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
