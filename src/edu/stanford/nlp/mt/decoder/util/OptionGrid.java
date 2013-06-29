package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Grid of ConcreteRules (translation rules) for a given
 * source sentence.
 * 
 * Optionally implements lazy sorting of translation rules according
 * to isolation scores.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 */
public class OptionGrid<TK,FV> {
  private final List<ConcreteRule<TK,FV>>[] grid;
  private final int sourceLength;
  private final BitSet isSorted;
  private final boolean doLazySorting;
  
  /**
   * Create an option grid from the source sentence and list of rules.
   * 
   * @param options
   * @param source
   */
  public OptionGrid(List<ConcreteRule<TK,FV>> options,
      Sequence<TK> source) {
    this(options, source, false);
  }

  @SuppressWarnings("unchecked")
  public OptionGrid(List<ConcreteRule<TK, FV>> ruleList,
      Sequence<TK> source, boolean doLazySorting) {
    sourceLength = source.size();
    isSorted = new BitSet();
    this.doLazySorting = doLazySorting;
    // Sacrificing memory for speed. This array will be sparse due to the maximum
    // phrase length.
    grid = new List[sourceLength * sourceLength];
    for (int startIdx = 0; startIdx < sourceLength; startIdx++) {
      for (int endIdx = startIdx; endIdx < sourceLength; endIdx++) {
        grid[getIndex(startIdx, endIdx)] = new ArrayList<ConcreteRule<TK,FV>>();
      }
    }
    for (ConcreteRule<TK,FV> rule : ruleList) {
      int startPos = rule.sourcePosition;
      int endPos = rule.sourceCoverage.nextClearBit(rule.sourcePosition) - 1;
      grid[getIndex(startPos, endPos)].add(rule);
    }
  }

  /**
   * True if this list of rules has been sorted, false otherwise.
   * 
   * @param startPos
   * @param endPos
   * @return
   */
  public boolean isSorted(int startPos, int endPos) {
    int offset = getIndex(startPos, endPos);
    return isSorted.get(offset);
  }
  
  /**
   * One dimension of the option grid. This corresponds to length of the source
   * sentence that corresponds to this option grid.
   * 
   * @return
   */
  public int gridDimension() { return sourceLength; }
  
  /**
   * Return rules by the given span.
   * 
   * @param startPos Absolute left edge of the span.
   * @param endPos Absolute right edge of the span.
   * @return
   */
  public List<ConcreteRule<TK,FV>> get(int startPos, int endPos) {
    int offset = getIndex(startPos, endPos);
    if (offset >= grid.length) throw new IllegalArgumentException("Coordinates are out-of-bounds");
    if (doLazySorting && ! isSorted.get(offset)) {
      Collections.sort(grid[offset]);
    }
    return grid[offset];
  }

  /**
	 * 
	 */
  private int getIndex(int startPos, int endPos) {
    return startPos * sourceLength + endPos;
  }
}
