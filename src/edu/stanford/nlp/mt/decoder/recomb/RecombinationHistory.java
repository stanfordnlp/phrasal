package edu.stanford.nlp.mt.decoder.recomb;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.decoder.util.State;

/**
 * 
 * @author danielcer
 * 
 * @param <S>
 */
public class RecombinationHistory<S extends State<S>> {

  private final Map<S, List<S>> historyMap = new HashMap<>(3000);

  /**
   * Log a recombination decision.
   * 
   * @param retained The derivation that survived.
   * @param discarded The derivation that was discarded.
   */
  public void log(S retained, S discarded) {
    if (discarded == null) {
      return;
    }
    List<S> retainedList = historyMap.get(retained);
    if (retainedList == null) {
      retainedList = new LinkedList<S>();
      historyMap.put(retained, retainedList);
    }
    List<S> discardedList = historyMap.get(discarded);
    if (discardedList != null) {
      historyMap.remove(discarded);
      retainedList.addAll(discardedList);
    }
  }

  /**
   * 
   */
  public void remove(S pruned) {
    historyMap.remove(pruned);
  }

  /**
   * 
   */
  public List<S> recombinations(State<S> retainedState) {
    return historyMap.getOrDefault(retainedState, Collections.emptyList());
  }
}
