package edu.stanford.nlp.mt.decoder.util;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class OptionGrid<TK,FV> {
  private final List<ConcreteRule<TK,FV>>[] grid;
  private final int foreignSz;

  /**
	 * 
	 */
  @SuppressWarnings("unchecked")
  public OptionGrid(List<ConcreteRule<TK,FV>> options,
      Sequence<TK> foreign) {
    foreignSz = foreign.size();
    grid = new List[foreignSz * foreignSz];
    for (int startIdx = 0; startIdx < foreignSz; startIdx++) {
      for (int endIdx = startIdx; endIdx < foreignSz; endIdx++) {
        grid[getIndex(startIdx, endIdx)] = new LinkedList<ConcreteRule<TK,FV>>();
      }
    }
    for (ConcreteRule<TK,FV> opt : options) {
      int startPos = opt.sourcePosition;
      int endPos = opt.sourceCoverage.nextClearBit(opt.sourcePosition) - 1;
      grid[getIndex(startPos, endPos)].add(opt);
    }
  }

  /**
	 * 
	 */
  public List<ConcreteRule<TK,FV>> get(int startPos, int endPos) {
    return grid[getIndex(startPos, endPos)];
  }

  /**
	 * 
	 */
  private int getIndex(int startPos, int endPos) {
    return startPos * foreignSz + endPos;
  }
}
