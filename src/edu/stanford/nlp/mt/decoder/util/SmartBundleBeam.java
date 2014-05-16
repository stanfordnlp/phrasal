package edu.stanford.nlp.mt.decoder.util;

import java.util.List;

import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.util.Generics;


public class SmartBundleBeam<TK, FV> extends BundleBeam<TK, FV> {

  
  private List<Range> contiguousRanges;

  private static int MAX_PHRASE_LEN = 5;
  
  
  public SmartBundleBeam(int capacity,
      RecombinationFilter<Derivation<TK, FV>> filter,
      RuleGrid<TK, FV> optionGrid,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory,
      List<Integer> permutationSequence, int coverageCardinality) {

      super(capacity, filter, optionGrid, recombinationHistory, -1, coverageCardinality);
      
      
      this.contiguousRanges = Generics.newLinkedList();
      int permutationLen = permutationSequence.size();
      for (int i = 0; i < permutationLen; i++) {
        for (int j = 0; j < MAX_PHRASE_LEN; j++) {
          int end = i + j + 1 > permutationLen ? permutationLen : i + j + 1;
          List<Integer> subsequence = permutationSequence.subList(i, end);
          if (isContiguousSequence(subsequence)) 
            this.contiguousRanges.add(new Range(i, Math.min(i + j, this.sourceLength - 1)));
        }
      }
      
  }
  
  
  private boolean isContiguousSequence(List<Integer> seq) {
    int len = seq.size();
    if (len < 1) return true;
    int min = Integer.MAX_VALUE;
    int max = -1;
    for (int elem : seq) {
      if (elem > max)
        max = elem;
      if (elem < min)
        min = elem;
    }
    if (len == (max - min + 1))
      return true;
    else
      return false;
  }
  
  @Override
  protected List<Range> ranges(CoverageSet sourceCoverage) {
    
    int len = sourceCoverage.length();
    
    List<Range> ranges = Generics.newLinkedList();
    
    for (Range range : this.contiguousRanges) {
      int start = range.start;
      int end = range.end;
      int nextSetBit = start < len ? sourceCoverage.nextSetBit(start) : -1;
      if (nextSetBit < 0 || nextSetBit > end) {
        ranges.add(range);
      }

      //if (range.size() == MAX_PHRASE_LEN) 
      //  break;
    }
    
    return ranges;
    
  }
}
