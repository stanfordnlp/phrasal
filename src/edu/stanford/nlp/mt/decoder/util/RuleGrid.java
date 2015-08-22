package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.Sequence;

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
public class RuleGrid<TK,FV> implements Iterable<ConcreteRule<TK,FV>> {
  
  private final List<ConcreteRule<TK,FV>>[] grid;
  private final int sourceLength;
  private final BitSet isSorted;
  private boolean doLazySorting;
  private boolean completeCoverage;
  private CoverageSet incrementalCoverage;
  private int size = 0;
  
  /**
   * Constructor.
   * 
   * @param source
   */
  @SuppressWarnings("unchecked")
  public RuleGrid(List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source) {
    sourceLength = source.size();
    isSorted = new BitSet();
    this.doLazySorting = true;
    // Sacrificing memory for speed. This array will be sparse due to the maximum
    // phrase length.
    grid = new List[sourceLength * sourceLength];
    incrementalCoverage = new CoverageSet();
    ruleList.stream().forEach(rule -> addEntry(rule));
  }
  
  /**
   * Constructor for creating a RuleGrid incrementally.
   * 
   * IMPORTANT: if a RuleGrid is constructed incrementally, then it is assumed
   * that the calls to addEntry insert rules in sorted order.
   * 
   * @param source
   */
  @SuppressWarnings("unchecked")
  public RuleGrid(int sourceLength) {
    this.sourceLength = sourceLength;
    isSorted = new BitSet();
    isSorted.set(0, sourceLength);
    doLazySorting = false;
    grid = new List[sourceLength * sourceLength];
    completeCoverage = false;
    incrementalCoverage = new CoverageSet();
  }

  /**
   * Toggle lazy sorting of rules.
   * 
   * @param b
   */
  public void setLazySorting(boolean b) { 
    this.doLazySorting = b; 
    if (doLazySorting) isSorted.clear();
  }
  
  /**
   * Add a new entry to the rule table.
   * 
   * @param rule
   */
  public void addEntry(ConcreteRule<TK,FV> rule) {
    int startPos = rule.sourcePosition;
    int endPos = startPos + rule.abstractRule.source.size() - 1;
    // Sanity checks
    assert startPos <= endPos : String.format("Illegal span: [%d,%d]", startPos, endPos);
    assert endPos < sourceLength : String.format("End index out of bounds: [%d,%d] >= %d", startPos, endPos, sourceLength);
    
    int offset = getIndex(startPos, endPos);
    if (grid[offset] == null) grid[offset] = new ArrayList<>();
    grid[offset].add(rule);
    incrementalCoverage.or(rule.sourceCoverage);
    completeCoverage = (incrementalCoverage.cardinality() == sourceLength);
    ++size;
  }
  
  /**
   * True if the grid completely covers the source input. Otherwise, false.
   * @return
   */
  public boolean isCoverageComplete() { return completeCoverage; }

  /**
   * Return the number of unique converages in this grid.
   * 
   * @return
   */
  public int numberOfCoverages() { return grid.length; }
  
  /**
   * Return all rules associated with a coverage id.
   * 
   * @param i
   * @return
   */
  public List<ConcreteRule<TK,FV>> getRulesForCoverageId(int i) {
    if (i < 0 || i >= grid.length) throw new ArrayIndexOutOfBoundsException();
    return grid[i] == null ? Collections.emptyList() : grid[i];
  }
  
  /**
   * Return the number of rules in this grid.
   * 
   * @return
   */
  public int numRules() { return size; }
  
  /**
   * Remove a rule from the grid.
   * 
   * @param coverageId
   * @param ruleIndex
   * @return
   */
  public ConcreteRule<TK,FV> remove(int coverageId, int ruleIndex) {
    if (coverageId >= grid.length || grid[coverageId] == null || ruleIndex >= grid[coverageId].size()) {
      throw new IllegalArgumentException();
    }
    return grid[coverageId].remove(ruleIndex);
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
   * @param startInclusive Absolute left edge of the span.
   * @param endInclusive Absolute right edge of the span.
   * @return
   */
  public List<ConcreteRule<TK,FV>> get(int startInclusive, int endInclusive) {
    final int offset = getIndex(startInclusive, endInclusive);
    if (offset < 0 || offset >= grid.length) {
      throw new IllegalArgumentException("Span is out-of-bounds");
    }
    if (grid[offset] != null && doLazySorting && ! isSorted.get(offset)) {
      Collections.sort(grid[offset]);
      isSorted.set(offset);
    }
    return grid[offset] == null ? Collections.emptyList() : grid[offset];
  }

  /**
   * 
   * @param startPos
   * @param endPos
   * @return
   */
  private int getIndex(int startPos, int endPos) {
    return startPos * sourceLength + endPos;
  }

  @Override
  public Iterator<ConcreteRule<TK, FV>> iterator() {
    return new Iterator<ConcreteRule<TK,FV>>() {
      int coverageId = 0;
      int ruleId = 0;
      boolean setup = false;
      
      @Override
      public boolean hasNext() {
        if ( ! setup) {
          // First call. Set the pointers
          setCoverageId();
          setup = true;
        }
        return (coverageId < grid.length && ruleId < grid[coverageId].size());
      }

      @Override
      public ConcreteRule<TK, FV> next() {
        ConcreteRule<TK,FV> rule = grid[coverageId].get(ruleId++);
        if (ruleId >= grid[coverageId].size()) {
          ruleId = 0;
          ++coverageId;
          setCoverageId();
        }
        return rule;
      }
      
      private void setCoverageId() {
        for (; coverageId < grid.length; ++coverageId) {
          if (grid[coverageId] != null) {
            break;
          }
        }
      }
    };
  }
  
  @Override
  public int hashCode() {
    int hashCode = 0;
    for (ConcreteRule<TK,FV> rule : this) {
      hashCode += Double.hashCode(rule.isolationScore) ^ rule.abstractRule.source.hashCode() ^ rule.abstractRule.target.hashCode() ^ rule.sourceCoverage.hashCode();
    }
    return hashCode;
  }
}
