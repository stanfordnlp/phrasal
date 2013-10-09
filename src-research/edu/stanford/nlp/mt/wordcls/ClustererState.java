package edu.stanford.nlp.mt.wordcls;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

public class ClustererState implements Serializable {
  
  private static final long serialVersionUID = 6116063336985767319L;

  // Subset to cluster
  public final List<IString> vocabularySubset;
  
  // Gross statistics from the data
  public final Counter<IString> wordCount;
  public final TwoDimensionalCounter<IString, NgramHistory> historyCount;
  
  // Current state of the clustering
  public final Map<IString, Integer> wordToClass;
  public final Counter<Integer> classCount;
  public final TwoDimensionalCounter<Integer, NgramHistory> classHistoryCount;
  
  public final int numClasses;
  public final double currentObjectiveValue;
  
  public ClustererState(List<IString> vocabularySubset, Counter<IString> wordCount,
      TwoDimensionalCounter<IString, NgramHistory> historyCount, Map<IString, Integer> inWordToClass,
      Counter<Integer> inClassCount,
      TwoDimensionalCounter<Integer, NgramHistory> inClassHistoryCount, int numClasses, 
      double currentObjectiveValue) {
    this.vocabularySubset = vocabularySubset;
    this.wordCount = wordCount;
    this.historyCount = historyCount;
    this.wordToClass = inWordToClass;
    this.classCount = inClassCount;
    this.classHistoryCount = inClassHistoryCount;
    this.numClasses = numClasses;
    this.currentObjectiveValue = currentObjectiveValue;
  }
}
