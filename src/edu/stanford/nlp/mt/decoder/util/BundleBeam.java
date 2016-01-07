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

  protected final RecombinationHash<Derivation<TK,FV>> recombinationHash;
  protected final int capacity;
  protected int recombined = 0;
  protected final int distortionLimit;
  protected final int sequenceLength;

  protected Map<Integer,List<HyperedgeBundle<TK,FV>>> bundles;
  protected final RecombinationHistory<Derivation<TK,FV>> recombinationHistory;
  protected final RuleGrid<TK, FV> optionGrid;
  protected final int coverageCardinality;
  protected final boolean isTargetCardinalityBeam; 
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
      RuleGrid<TK, FV> optionGrid, RecombinationHistory<Derivation<TK,FV>> recombinationHistory, 
      int distortionLimit, int coverageCardinality) {
    recombinationHash = new RecombinationHash<>(filter);
    this.capacity = capacity;
    this.recombinationHistory = recombinationHistory;
    this.optionGrid = optionGrid;
    this.sequenceLength = optionGrid.gridDimension();
    this.distortionLimit = distortionLimit;
    this.coverageCardinality = coverageCardinality;
    this.isTargetCardinalityBeam = true;
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
      RuleGrid<TK, FV> optionGrid, RecombinationHistory<Derivation<TK,FV>> recombinationHistory, 
      int distortionLimit, int coverageCardinality, boolean isTargetCardinalityBeam) {
    recombinationHash = new RecombinationHash<>(filter);
    this.capacity = capacity;
    this.recombinationHistory = recombinationHistory;
    this.optionGrid = optionGrid;
    this.sequenceLength = optionGrid.gridDimension();
    this.distortionLimit = distortionLimit;
    this.coverageCardinality = coverageCardinality;
    this.isTargetCardinalityBeam = isTargetCardinalityBeam;
  }
  
  

  @Override
  public Derivation<TK,FV> put(Derivation<TK,FV> derivation) {
    if (isTargetCardinalityBeam ? derivation.targetSequence.size() != coverageCardinality :
          derivation.sourceCoverage.cardinality() != coverageCardinality ) {
      throw new RuntimeException("Derivation cardinality does not match beam cardinality");
    }

    final Status status = recombinationHash.update(derivation);
    if (recombinationHistory != null) {
      recombinationHistory.log(recombinationHash.getLastBestOnQuery(),
          recombinationHash.getLastRedundant());
    }

    Derivation<TK,FV> recombinedDerivation = null;
    if (status == Status.COMBINABLE) {
      ++recombined;
      recombinedDerivation = derivation;

    } else if(status == Status.BETTER) {
      ++recombined;
      recombinedDerivation = recombinationHash.getLastRedundant();
    } 

    return recombinedDerivation;
  }

  /**
   * This method must be called once all hypotheses have been inserted into the beam.
   */
  private void groupBundles() {
    List<Derivation<TK,FV>> derivationList = recombinationHash.derivations();
    assert derivationList.size() <= capacity : String.format("Beam contents exceeds capacity: %d %d", derivationList.size(), capacity);
    
    if(!isTargetCardinalityBeam) {
      // Group hypotheses by source coverage
      final Map<CoverageSet,List<Derivation<TK,FV>>> coverageGroups = new HashMap<>(derivationList.size() / 2);
      for (Derivation<TK,FV> derivation : derivationList) {
        List<Derivation<TK,FV>> hypList = coverageGroups.get(derivation.sourceCoverage);
        if (hypList == null) {
          hypList = new ArrayList<>(32);
          coverageGroups.put(derivation.sourceCoverage, hypList);
        }
        hypList.add(derivation);
      }
  
      // Make hyperedge bundles
      bundles = new HashMap<>(2*coverageGroups.size());
      for (CoverageSet coverage : coverageGroups.keySet()) {
        final List<Derivation<TK,FV>> itemList = coverageGroups.get(coverage);
        Collections.sort(itemList);
        for (Range range : ranges(coverage)) {
          assert range.start <= range.end : "Invalid range";
          final List<ConcreteRule<TK,FV>> ruleList = optionGrid.get(range.start, range.end);
          if (ruleList.size() > 0) {
            final HyperedgeBundle<TK,FV> bundle = new HyperedgeBundle<>(itemList, ruleList);
            List<HyperedgeBundle<TK,FV>> bundleList = bundles.get(range.size());
            if (bundleList == null) {
              bundleList = new ArrayList<>();
              bundles.put(range.size(), bundleList);
            }
            bundleList.add(bundle);
          }
        }
      }
    }
    else { // i.e. isTargetCardinalityBeam == true
      Collections.sort(derivationList);
      int endPosMax = Math.min(sequenceLength, coverageCardinality + optionGrid.maxTargetLength);
      for(int endPos = coverageCardinality; endPos < endPosMax; ++endPos) {
        Range range = new Range(coverageCardinality, endPos);
        final List<ConcreteRule<TK,FV>> ruleList = optionGrid.get(range.start, range.end);
        if (ruleList.size() > 0) {
          final HyperedgeBundle<TK,FV> bundle = new HyperedgeBundle<>(derivationList, ruleList);
          List<HyperedgeBundle<TK,FV>> bundleList = bundles.get(range.size());
          if (bundleList == null) {
            bundleList = new ArrayList<>();
            bundles.put(range.size(), bundleList);
          }
          bundleList.add(bundle);
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
    return bundles.getOrDefault(rangeSize, Collections.emptyList());
  }
  
  /**
   * Reset this beam for search.
   */
  public void reset() {
    groupBundles();
  }

  @Override
  public Iterator<Derivation<TK, FV>> iterator() {
    List<Derivation<TK,FV>> derivations = recombinationHash.derivations();
    Collections.sort(derivations);
    return derivations.iterator();
  }

  protected List<Range> ranges(CoverageSet sourceCoverage) {
    List<Range> rangeList = new ArrayList<>();
    int firstCoverageGap = sourceCoverage.nextClearBit(0);
    for (int startPos = firstCoverageGap; startPos < sequenceLength; startPos++) {
      int endPosMax = sourceCoverage.nextSetBit(startPos);

      // Re-ordering constraint checks
      // Moses-style hard distortion limit
      if (endPosMax < 0) {
        // TODO(spenceg) weird Moses implementation that allows the first option
        // to cover any span in the source. See MultiBeamDecoder.
        //      if (distortionLimit >= 0 && startPos != firstCoverageGap) {
        if (distortionLimit >= 0) {
          endPosMax = Math.min(firstCoverageGap + distortionLimit + 1,
              sequenceLength);
        } else {
          endPosMax = sequenceLength;
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
  public String toString() {
    return String.format("cardinality: %d  size: %d  #bundles: %d  #recombined", coverageCardinality,
        size(), bundles == null ? 0 : bundles.size(), this.recombined);
  }
  
  /**
   * Return the top-k items on this beam.
   * 
   * @return
   */
  public String beamString(int k) {
    StringBuilder sb = new StringBuilder();
    final String nl = System.getProperty("line.separator");
    int i = 0;
    for (Derivation<TK,FV> h : this) {
      if (sb.length() > 0) sb.append(nl);
      sb.append(i++).append(" ").append(h.toString());
      if (i >= k) break;
    }
    return sb.toString();
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

  protected static class Range {
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
