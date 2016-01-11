package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
  
  private static final Logger logger = LogManager.getLogger(RuleGrid.class.getName());
  
  private static final int DEFAULT_RULE_QUERY_LIMIT = Integer.MAX_VALUE;
  
  private final List<ConcreteRule<TK,FV>>[] grid;
  private final int sequenceLength;
  private final BitSet isSorted;
  private CoverageSet coverage;
  private final int size;
  private int ruleQueryLimit;
  private boolean isSourceGrid = true;
  Map<TK,List<Integer>> wordToPosition = null;
  Sequence<TK> prefix = null;
  int maxTargetLength = 0;
  int maxSourceLength = 0;
  
  /**
   * Constructor.
   * 
   * @param ruleList
   * @param source
   */
  public RuleGrid(List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source) {
    this(ruleList, source, DEFAULT_RULE_QUERY_LIMIT);
  }
  
  /**
   * Constructor.
   * 
   * @param ruleList
   * @param source
   * @param ruleQueryLimit
   */
  @SuppressWarnings("unchecked")
  public RuleGrid(List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source, int ruleQueryLimit) {
    sequenceLength = source.size();
    isSorted = new BitSet();
    this.ruleQueryLimit = ruleQueryLimit < 0 ? Integer.MAX_VALUE : ruleQueryLimit;
    this.size = ruleList.size();
    // Sacrificing memory for speed. This array will be sparse due to the maximum
    // phrase length.
    grid = new List[sequenceLength * sequenceLength];
    coverage = new CoverageSet(sequenceLength);
    for (ConcreteRule<TK,FV> rule : ruleList) addSrcEntry(rule);
  }
  
  
  /**
   * Constructor for prefix decoding rule grid. 
   * The rules will be organized by target position.
   * 
   * @param ruleList
   * @param sequence
   * @param ruleQueryLimit
   * @param prefixGrid
   */
  @SuppressWarnings("unchecked")
  public RuleGrid(List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source, Sequence<TK> prefix, int ruleQueryLimit) {
    isSourceGrid = false;
    sequenceLength = prefix.size();
    this.prefix = prefix;
    isSorted = new BitSet();
    this.ruleQueryLimit = ruleQueryLimit < 0 ? Integer.MAX_VALUE : ruleQueryLimit;
    // Sacrificing memory for speed. This array will be sparse due to the maximum
    // phrase length.
    grid = new List[sequenceLength * sequenceLength];
    coverage = new CoverageSet(sequenceLength);

    // Make prefix word type -> position
    wordToPosition = new HashMap<>();
    for (int i = 0, limit = prefix.size(); i < limit; ++i) {
      TK token = prefix.get(i);
      List<Integer> positionList = wordToPosition.get(token);
      if (positionList == null) {
        positionList = new ArrayList<>();
        wordToPosition.put(token, positionList);
      }
      positionList.add(i);
    }
    
    // Find the prefix rules
    List<ConcreteRule<TK, FV>> filteredRuleList = new ArrayList<>(ruleList.size()/20);
    for (ConcreteRule<TK,FV> rule : ruleList) {
      if(prefix.contains(rule.abstractRule.target))
        filteredRuleList.add(rule);
    }
    
    // todo: sort based on additional prefix-isolation scores 
    //Collections.sort(filteredRuleList);
    
    this.size = filteredRuleList.size();
    for (ConcreteRule<TK,FV> rule : ruleList) addTgtEntry(rule, true);
    logger.info("# prefix rules: {}/{}", this.size, ruleList.size());
  }  
  
 
  public void setRuleQueryLimit(int l) { ruleQueryLimit = l < 0 ? Integer.MAX_VALUE : l; }
  
  /**
   * Add a new entry to the rule table.
   * 
   * @param rule
   */
  public void addEntry(ConcreteRule<TK,FV> rule) {
    if(isSourceGrid) addSrcEntry(rule);
    else addTgtEntry(rule, true);
  }
  
  
  /**
   * Add a new entry to the rule table organized by source position.
   * 
   * @param rule
   */
  private void addSrcEntry(ConcreteRule<TK,FV> rule) {
    int startPos = rule.sourcePosition;
    int endPos = startPos + rule.abstractRule.source.size() - 1;
    // Sanity checks
    assert startPos <= endPos : String.format("Illegal span: [%d,%d]", startPos, endPos);
    assert endPos < sequenceLength : String.format("End index out of bounds: [%d,%d] >= %d", startPos, endPos, sequenceLength);
    
    int targetLength = rule.abstractRule.target.size();
    int sourceLength = rule.abstractRule.source.size();
    if(targetLength > maxTargetLength) maxTargetLength = targetLength;
    if(sourceLength > maxSourceLength) maxSourceLength = sourceLength;

    int offset = getIndex(startPos, endPos);
    if (grid[offset] == null) grid[offset] = new ArrayList<>();
    grid[offset].add(rule);
    isSorted.clear(offset);
    coverage.or(rule.sourceCoverage);
  }
  
  
  /**
   * Add a new entry to the rule table organized by target position.
   * 
   * @param rule
   */
  public void addTgtEntry(ConcreteRule<TK,FV> rule, boolean allowStraddle) {
    if (rule.abstractRule.target.size() == 0) return; // Source deletion rule
    
    List<Integer> matches = findTgtMatches(rule.abstractRule.target);
    
    for (int startPos : matches) {
      //System.err.println("add match for start pos " + startPos + ": " + rule);
      int targetLength = rule.abstractRule.target.size();
      int sourceLength = rule.abstractRule.source.size();
      if(targetLength > maxTargetLength) maxTargetLength = targetLength;
      if(sourceLength > maxSourceLength) maxSourceLength = sourceLength;
      
      if(!allowStraddle && startPos + targetLength > prefix.size()) continue;
      // We want to include phrases that straddle the boundary and store them under (startPos, prefix.size() - 1)
      int endPos = Math.min(prefix.size() - 1, startPos + targetLength - 1);
      // Sanity checks
      assert startPos <= endPos : String.format("Illegal span: [%d,%d]", startPos, endPos);
      assert endPos < sequenceLength : String.format("End index out of bounds: [%d,%d] >= %d", startPos, endPos, sequenceLength);
      int offset = getIndex(startPos, endPos);
      if (grid[offset] == null) grid[offset] = new ArrayList<>();
      grid[offset].add(rule);
      isSorted.clear(offset);
      
      coverage.set(startPos, endPos + 1);
    }
  }
  
  /**
   * Find all matching positions for target phrase.
   * Targets can match past the end of the prefix.
   * 
   * @param wordToPosition
   * @param target
   * @return
   */
  private List<Integer> findTgtMatches(Sequence<TK> targetPhrase) {
    return wordToPosition.getOrDefault(targetPhrase.get(0), Collections.emptyList()).stream().filter(pIdx -> {
      for (int i = 0, sz = targetPhrase.size(), psz = prefix.size(); i < sz && pIdx+i < psz; ++i) {
        if ( ! targetPhrase.get(i).equals(prefix.get(pIdx+i))) {
          return false;
        }
      }
      return true;
    }).collect(Collectors.toList());
  }
  
  /**
   * True if the grid completely covers the source input. Otherwise, false.
   * @return
   */
  public boolean isCoverageComplete() { return coverage.cardinality() == sequenceLength; }
  
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
  public int gridDimension() { return sequenceLength; }
  
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
      if (grid[offset] != null) {
        Collections.sort(grid[offset]);
        if (grid[offset].size() > ruleQueryLimit) grid[offset] = grid[offset].subList(0, ruleQueryLimit);
      }
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
    return startPos * sequenceLength + endPos;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    for (int i = 0; i < sequenceLength; ++i) {
      for (int j = i; j < sequenceLength; ++j) {
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
        if ( ! setup) {
          // First call. Set the pointers
          setCoverageId();
          setup = true;          
        }
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
  
  public boolean isSourceGrid() {
    return isSourceGrid;
  }
  
  public boolean isPrefixGrid() {
    return !isSourceGrid;
  }
  
  
  public int maxTargetLength() {
    return maxTargetLength;
  }
  
  public int maxSourceLength() {
    return maxSourceLength;
  }
}
