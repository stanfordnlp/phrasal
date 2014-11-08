package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.util.Generics;

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
public class RuleGrid<TK,FV> {
  private final List<ConcreteRule<TK,FV>>[] grid;
  private final int sourceLength;
  private final BitSet isSorted;
  private final boolean doLazySorting;
  private final boolean completeCoverage;
  
  /**
   * Constructor.
   * 
   * @param source
   */
  public RuleGrid(List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source) {
    this(ruleList, source, false);
  }

  @SuppressWarnings("unchecked")
  public RuleGrid(List<ConcreteRule<TK,FV>> ruleList, 
      Sequence<TK> source, boolean doLazySorting) {
    sourceLength = source.size();
    isSorted = new BitSet();
    this.doLazySorting = doLazySorting;
    // Sacrificing memory for speed. This array will be sparse due to the maximum
    // phrase length.
    grid = new List[sourceLength * sourceLength];
    CoverageSet coverage = new CoverageSet();
    for (ConcreteRule<TK,FV> rule : ruleList) {
      coverage.or(rule.sourceCoverage);
      int startPos = rule.sourcePosition;
      int endPos = startPos + rule.abstractRule.source.size() - 1;
      // Sanity checks
      assert startPos <= endPos : String.format("Illegal span: [%d,%d]", startPos, endPos);
      assert endPos < sourceLength : String.format("End index out of bounds: [%d,%d] >= %d", startPos, endPos, sourceLength);
      
      int offset = getIndex(startPos, endPos);
      if (grid[offset] == null) grid[offset] = Generics.newArrayList();
      grid[offset].add(rule);
    }
    this.completeCoverage = (coverage.cardinality() == sourceLength);
  }

  /**
   * True if the grid completely covers the source input. Otherwise, false.
   * @return
   */
  public boolean isCoverageComplete() { return completeCoverage; }
  
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
    final int offset = getIndex(startPos, endPos);
    if (offset < 0 || offset >= grid.length) {
      throw new IllegalArgumentException("Span is out-of-bounds");
    }
    if (grid[offset] != null && doLazySorting && ! isSorted.get(offset)) {
      Collections.sort(grid[offset]);
    }
    return grid[offset] == null ? new ArrayList<ConcreteRule<TK,FV>>(1) : grid[offset];
  }

  /**
	 * 
	 */
  private int getIndex(int startPos, int endPos) {
    return startPos * sourceLength + endPos;
  }
}
