package edu.stanford.nlp.mt.wordcls;

import java.util.Map;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

public class ClustererOutput {

  public final Map<IString, Integer> wordToClass;
  public final Counter<Integer> classCount;
  public final TwoDimensionalCounter<Integer, NgramHistory> classHistoryCount;
  
  public ClustererOutput(Map<IString, Integer> wordToClass, Counter<Integer> classCount,
      TwoDimensionalCounter<Integer, NgramHistory> classHistoryCount) {
    this.wordToClass = wordToClass;
    this.classCount = classCount;
    this.classHistoryCount = classHistoryCount;
  }
}
