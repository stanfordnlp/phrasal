package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Grid of ConcreteTranslationOptions (translation rules) for a given
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
  
  private final List<ConcreteTranslationOption<TK,FV>>[] grid;
  private final int sourceLength;
  private final BitSet isSorted;
  
  /**
   * Create an option grid from the source sentence and list of rules.
   * 
   * @param options
   * @param source
   */
  @SuppressWarnings("unchecked")
  public OptionGrid(List<ConcreteTranslationOption<TK,FV>> options,
      Sequence<TK> source) {
    sourceLength = source.size();
    isSorted = new BitSet();
    // Sacrificing memory for speed. This array will be sparse due to the maximum
    // phrase length.
    grid = new List[sourceLength * sourceLength];
    for (int startIdx = 0; startIdx < sourceLength; startIdx++) {
      for (int endIdx = startIdx; endIdx < sourceLength; endIdx++) {
        grid[getIndex(startIdx, endIdx)] = new ArrayList<ConcreteTranslationOption<TK,FV>>();
      }
    }
    for (ConcreteTranslationOption<TK,FV> opt : options) {
      int startPos = opt.sourcePosition;
      int endPos = opt.sourceCoverage.nextClearBit(opt.sourcePosition) - 1;
      grid[getIndex(startPos, endPos)].add(opt);
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
   * Sort the translation rules by score for this span.
   * 
   * @param startPos
   * @param endPos
   */
  public void sortRules(int startPos, int endPos) {
    int offset = getIndex(startPos, endPos);
    Collections.sort(grid[offset]);
    isSorted.set(offset);
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
  public List<ConcreteTranslationOption<TK,FV>> get(int startPos, int endPos) {
    return grid[getIndex(startPos, endPos)];
  }

  /**
	 * 
	 */
  private int getIndex(int startPos, int endPos) {
    return startPos * sourceLength + endPos;
  }
}
