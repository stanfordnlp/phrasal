package mt.decoder.recomb;

import java.util.*;

import mt.decoder.util.State;

/**
 * 
 * @author danielcer
 *
 * @param <S>
 */
public class RecombinationHistory<S extends State<S>> {

  private final HashMap<S,List<S>> historyMap = new HashMap<S,List<S>>();

  private RecombinationFilter<S> secondaryFilter;

  /**
   * This filter does not affect beam search, but only which discarded hypotheses
   * should be logged. Setting here a filter that enables more combinations (e.g.,
   * TranslationIdentityRecombinationFilter instead of the Moses default)
   * enables more diverse n-best lists, while not affecting the quality of the search
   * for the one-best.
   */
  public void setSecondaryFilter(RecombinationFilter<S> f) {
    this.secondaryFilter = f;
  }

  /**
	 * 
	 * @param retained
	 * @param discarded
	 */
	public void log(S retained, S discarded) {
    if (discarded == null) {
			return;
		}
    List<S> discardedList = historyMap.get(discarded);
		List<S> retainedList  = historyMap.get(retained);
		if (retainedList == null) {
			retainedList = new LinkedList<S>();
			synchronized(historyMap) {
			historyMap.put(retained, retainedList);
			}
		}
		if (discardedList != null) {
			historyMap.remove(discarded);
			retainedList.addAll(discardedList);
		}
    if(secondaryFilter == null || !secondaryFilter.combinable(retained,discarded))
      retainedList.add(discarded);
  }
	
	/**
	 * 
	 * @param pruned
	 */
	public void remove(S pruned) {
		historyMap.remove(pruned);
	}
	
	/**
	 * 
	 * @param retainedState
	 * @return
	 */
	public List<S> recombinations(State<S> retainedState) {
		return historyMap.get(retainedState);
	}
}
