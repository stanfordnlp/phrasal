package edu.stanford.nlp.mt.wordcls;

import java.util.Map;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

/**
 * Delta data structures for updates to one-sided clusterer
 * class state.
 * 
 * @author Spence Green
 *
 */
public class PartialStateUpdate {

  // Class assignments for the input vocabulary
  public final Map<IString, Integer> wordToClass;
  
  // Deltas from the current clustering state
  public final Counter<Integer> deltaClassCount;
  public final TwoDimensionalCounter<Integer, NgramHistory> deltaClassHistoryCount;
  
  public PartialStateUpdate(Map<IString, Integer> wordToClass, Counter<Integer> classCount,
      TwoDimensionalCounter<Integer, NgramHistory> classHistoryCount) {
    this.wordToClass = wordToClass;
    this.deltaClassCount = classCount;
    this.deltaClassHistoryCount = classHistoryCount;
  }
}
