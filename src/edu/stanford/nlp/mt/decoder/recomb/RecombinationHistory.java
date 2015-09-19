package edu.stanford.nlp.mt.decoder.recomb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.decoder.util.State;

/**
 * 
 * @author danielcer
 * @author Spence Green
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
    if (discarded == null) return;
    
    List<S> retainedList = historyMap.get(retained);
    if (retainedList == null) {
      retainedList = new ArrayList<>();
      historyMap.put(retained, retainedList);
    }
    List<S> discardedList = historyMap.getOrDefault(discarded, Collections.emptyList());
    historyMap.remove(discarded);
    retainedList.addAll(discardedList);
    retainedList.add(discarded);
  }

  /**
   * 
   */
  public void remove(S pruned) { historyMap.remove(pruned); }
  
  /**
   * 
   */
  public List<S> recombinations(State<S> retainedState) {
    return historyMap.getOrDefault(retainedState, Collections.emptyList());
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    for (Map.Entry<S, List<S>> entry : historyMap.entrySet()) {
      if (sb.length() > 0) sb.append(nl);
      sb.append(entry.getKey().toString()).append("\t").append(entry.getValue());
    }
    return sb.toString();
  }
}
