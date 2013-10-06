package edu.stanford.nlp.mt.wordcls;

import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

public class ClustererInput {
  
  public final List<IString> vocab;
  public final Counter<IString> wordCount;
  public final TwoDimensionalCounter<IString, NgramHistory> historyCount;
  
  public final Map<IString, Integer> wordToClass;
  public final Counter<Integer> classCount;
  public final TwoDimensionalCounter<Integer, NgramHistory> classHistoryCount;
  
  public ClustererInput(List<IString> inputVocab, Counter<IString> wordCount,
      TwoDimensionalCounter<IString, NgramHistory> historyCount, Map<IString, Integer> inWordToClass,
      Counter<Integer> inClassCount,
      TwoDimensionalCounter<Integer, NgramHistory> inClassHistoryCount) {
    this.vocab = inputVocab;
    this.wordCount = wordCount;
    this.historyCount = historyCount;
    this.wordToClass = inWordToClass;
    this.classCount = inClassCount;
    this.classHistoryCount = inClassHistoryCount;
  }
}
