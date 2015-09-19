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
  
  private static final int DEFAULT_RULE_QUERY_LIMIT = Integer.MAX_VALUE;
  
  private final List<ConcreteRule<TK,FV>>[] grid;
  private final int sourceLength;
  private final BitSet isSorted;
  private boolean completeCoverage;
  private CoverageSet coverage;
  private final int size;
  private final int ruleQueryLimit;
  
  /**
   * Constructor.
   * 
   * @param source
   */
  public RuleGrid(List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source) {
    this(ruleList, source, DEFAULT_RULE_QUERY_LIMIT);
  }
  
  /**
   * Constructor.
   * 
   * @param source
   */
  @SuppressWarnings("unchecked")
  public RuleGrid(List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source, int ruleQueryLimit) {
    sourceLength = source.size();
    isSorted = new BitSet();
    this.ruleQueryLimit = ruleQueryLimit;
    this.size = ruleList.size();
    // Sacrificing memory for speed. This array will be sparse due to the maximum
    // phrase length.
    grid = new List[sourceLength * sourceLength];
    coverage = new CoverageSet(sourceLength);
    for (ConcreteRule<TK,FV> rule : ruleList) addEntry(rule);
  }
  
  /**
   * Add a new entry to the rule table.
   * 
   * @param rule
   */
  private void addEntry(ConcreteRule<TK,FV> rule) {
    int startPos = rule.sourcePosition;
    int endPos = startPos + rule.abstractRule.source.size() - 1;
    // Sanity checks
    assert startPos <= endPos : String.format("Illegal span: [%d,%d]", startPos, endPos);
    assert endPos < sourceLength : String.format("End index out of bounds: [%d,%d] >= %d", startPos, endPos, sourceLength);
    
    int offset = getIndex(startPos, endPos);
    if (grid[offset] == null) grid[offset] = new ArrayList<>();
    grid[offset].add(rule);
    coverage.or(rule.sourceCoverage);
    completeCoverage = (coverage.cardinality() == sourceLength);
  }
  
  /**
   * True if the grid completely covers the source input. Otherwise, false.
   * @return
   */
  public boolean isCoverageComplete() { return completeCoverage; }
  
  /**
   * Get the source coverage.
   * 
   * @return
   */
  public CoverageSet getCoverage() { return coverage; }
  
  /**
   * Return the number of rules in this grid.
   * 
   * @return
   */
  public int size() { return size; }
  
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
      throw new ArrayIndexOutOfBoundsException("Span is out-of-bounds");
    }
    if (! isSorted.get(offset)) {
      if (grid[offset] == null) grid[offset] = Collections.emptyList();
      Collections.sort(grid[offset]);
      if (grid[offset].size() > ruleQueryLimit) grid[offset] = grid[offset].subList(0, ruleQueryLimit);
      isSorted.set(offset);
    } 
    return grid[offset];
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
  public String toString() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    for (int i = 0; i < sourceLength; ++i) {
      for (int j = i; j < sourceLength; ++j) {
        List<ConcreteRule<TK,FV>> rules = get(i, j);
        if (rules.size() > 0) {
          sb.append("## ").append(i).append("-").append(j).append(nl);
          rules.stream().forEach(r -> sb.append(r).append(nl));
        }
      }
    }
    return sb.toString();
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
