package edu.stanford.nlp.mt.mkcls;

import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Generics;

public class GoogleObjectiveFunction {

  private double objValue = 0.0;
  
  private final ClustererInput input;
  private final Map<IString, Integer> localWordToClass;
  private final Counter<Integer> localClassCount;
  private final TwoDimensionalCounter<Integer, NgramHistory> localClassHistoryCount;
  
  public GoogleObjectiveFunction(ClustererInput input) {
    // Setup delta data structures
    this.input = input;
    localWordToClass = Generics.newHashMap(input.vocab.size());
    localClassCount = new ClassicCounter<Integer>(input.classCount.keySet().size());
    localClassHistoryCount = new TwoDimensionalCounter<Integer,NgramHistory>();
    for (IString word : input.vocab) {
      int classId = input.wordToClass.get(word);
      localWordToClass.put(word, classId);
      localClassCount.incrementCount(classId);
      Counter<NgramHistory> historyCount = 
          new ClassicCounter<NgramHistory>(input.classHistoryCount.getCounter(classId));
      localClassHistoryCount.setCounter(classId, historyCount);
    }

    // Compute initial objective function value
    // First summation
    for (Integer classId : input.classHistoryCount.firstKeySet()) {
      Counter<NgramHistory> historyCount = input.classHistoryCount.getCounter(classId);
      for (NgramHistory history : historyCount.keySet()) {
        double count = historyCount.getCount(history);
        objValue += count * Math.log(count);
      }
    }
    // Second summation
    for (Integer classId : input.classCount.keySet()) {
      double count = input.classCount.getCount(classId);
      objValue -= count * Math.log(count);
    }
  }

  public ClustererOutput cluster() {
    Set<Integer> wordClasses = input.classCount.keySet();
    for (IString word : input.vocab) {
      final Integer currentClass = localWordToClass.get(word);
      Integer argMax = currentClass;
      double maxObjectiveValue = objValue;
      final Counter<NgramHistory> historiesForWord = input.historyCount.getCounter(word);
      
      // Remove the word from the local data structures
      double reducedObjValue = objectiveAfterRemoving(word, currentClass);
      assert reducedObjValue < objValue;
      localClassCount.decrementCount(currentClass);
      
      // Compute objective value under tentative moves
      for (Integer classId : wordClasses) {
        if (classId == currentClass) continue;
        double objDelta = 0.0;
        // TODO(spenceg): This is not quite right. Need to tie the histories
        // to the class
        Counter<NgramHistory> classHistory = input.classHistoryCount.getCounter(classId);
        for (NgramHistory history : historiesForWord.keySet()) {
          double count = historiesForWord.getCount(history);
          objDelta += count * Math.log(count);
        }
        double classCount = localClassCount.getCount(classId) + 1;
        objDelta -= classCount * Math.log(classCount);
        if (reducedObjValue + objDelta > maxObjectiveValue) {
          argMax = classId;
          maxObjectiveValue = reducedObjValue + objDelta;
        }
      }
      // Final move
      if (argMax == currentClass) {
        localClassCount.incrementCount(currentClass);
      } else {
        move(word, currentClass, argMax);
      }
    }
    return new ClustererOutput(localWordToClass, localClassCount, localClassHistoryCount);
  }

  /**
   * Explicitly update the local data structures.
   * 
   * @param word
   * @param fromClass
   * @param toClass
   */
  private void move(IString word, Integer fromClass, Integer toClass) {
    // TODO Auto-generated method stub
    
  }

  /**
   * Implicitly decrement local data structures and return objective data function
   * value.
   * 
   * @param word
   * @param currentClass 
   * @return
   */
  private double objectiveAfterRemoving(IString word, Integer currentClass) {
    double reducedObjective = objValue;
    // TODO(spenceg) This is not quite right. Need to tie to the histories
    // to the class
    final Counter<NgramHistory> historiesForWord = input.historyCount.getCounter(word);
    for (NgramHistory history : historiesForWord.keySet()) {
      double count = historiesForWord.getCount(history);
      // Remove original term
      reducedObjective -= count * Math.log(count);
      --count;
      if (count > 0) {
        // Add updated term
        reducedObjective += count * Math.log(count);
      }
    }
    double classCount = localClassCount.getCount(currentClass);
    // Remove original term
    reducedObjective += classCount * Math.log(classCount);
    --classCount;
    if (classCount > 0) {
      // Add updated term
      reducedObjective -= classCount * Math.log(classCount);
    }
    return reducedObjective;
  }
}
