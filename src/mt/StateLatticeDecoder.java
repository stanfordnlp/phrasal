package mt;

import java.util.*;

/**
 * A simple a-star based Lattice decoder 
 * 
 * @author danielcer
 *
 * @param <S>
 */
public class StateLatticeDecoder<S extends State<S>> implements Iterator<List<S>>, Iterable<List<S>> {
	static public final String DEBUG_OPT = "StateLatticeDecoderDebug";
	static public final int DEBUG = Integer.parseInt(System.getProperty(DEBUG_OPT, "0"));
	static public final int DEBUG_STATS  = 1;
	static public final int DEBUG_DETAIL = 2;
	
	PriorityQueue<CompositeState> agenda = new PriorityQueue<CompositeState>();
	Set<CompositeState> dupCheckSet = new HashSet<CompositeState>();
	
	
	/**
	 * 
	 * @param goalStates
	 * @param recombinatinHistory
	 */
	public StateLatticeDecoder(List<S> goalStates, RecombinationHistory<S> recombinationHistory, int requestLimit){	
		init(goalStates, recombinationHistory);
	}
	
	public StateLatticeDecoder(List<S> goalStates, RecombinationHistory<S> recombinationHistory, int requestLimit, boolean reverse) {
	//	this.reverse = reverse; XXX reverse is currently being ignored
		init(goalStates, recombinationHistory);
	}
	
	private RecombinationHistory<S> recombinationHistory;
	private void init(List<S> goalStates, RecombinationHistory<S> recombinationHistory) {
		this.recombinationHistory = recombinationHistory;
	    // initialize score deltas 
		for (S goalState : goalStates) {
			CompositeState newComposite  = new CompositeState(goalState);
			dupCheckSet.add(newComposite); // not actually necessary right now
			agenda.add(newComposite);
		}
	}
			
	@Override
	public boolean hasNext() {
		return !agenda.isEmpty();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<S> next() {
		CompositeState best = agenda.remove();
		
		// System.err.printf("returning state with score: %f\n", best.score);
		int statesLength = best.states.size();
		for (int i = 0; i < statesLength; i++) {
			List<S> recombinedStates = recombinationHistory.recombinations(best.states.get(i));
			if (recombinedStates == null) continue;
			for (S recombinedState : recombinedStates) {
				CompositeState newComposite = new CompositeState(best, recombinedState, i);
				if (dupCheckSet.contains(newComposite)) continue;
				dupCheckSet.add(newComposite);
				agenda.add(newComposite);
			}
		}
		return best.states;		
	}
	

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
	
	
	private class CompositeState implements Comparable<CompositeState> {
		final List<S> states;
		final double score;
		final int hashCode;
		
		@Override
		public int hashCode() {
			return hashCode; 
		}
		
		private int computeHashCode() {
			int code = 0;
			for (State<S> s : states) {
				code += 32*s.hashCode();
			}
			return code;
		}
		
		@Override 
		public boolean equals(Object o) {
			CompositeState oCS = (CompositeState)o;
			return states.equals(oCS.states);
		}
		
		
		@SuppressWarnings("unchecked")
		public CompositeState(S initialState) {
			
			int length = 0;
			for (State<S> state = initialState; state != null; state = state.parent()) length++;
			states = new ArrayList<S>(length);
			for (State<S> state = initialState; state != null; state = state.parent()) states.add((S)state);
			Collections.reverse(states);
			score = initialState.partialScore();
			hashCode = computeHashCode();
			// System.err.printf("cost from goal state: %e cost from computeListCost: %e\n", score, computeListCost());
		}
		
		private double computeListCost() {
			double cost = 0;
			for (State<S> state : states) {
				State<S> parent = state.parent();
				double parentScore = (parent == null ? 0 : parent.partialScore());
				cost += state.partialScore() - parentScore;
			}
			return cost;
		}
		
		@SuppressWarnings("unchecked")
		public CompositeState(CompositeState original, S varState, int varPosition) {
			int newPrefixLength = 0;
			for (State<S> state = varState; state != null; state = state.parent()) newPrefixLength++;
			
			states = new ArrayList<S>(original.states.size()+newPrefixLength-varPosition-1);
			for (State<S> state = varState; state != null; state = state.parent()) states.add((S)state);
			Collections.reverse(states);
			int originalStatesLength = original.states.size();
			for (int i = varPosition+1; i < originalStatesLength; i++) {
				states.add(original.states.get(i));
			}
			score = computeListCost();
			hashCode = computeHashCode();
		}
		
		
		public double score() {
			return score;
		}

		@Override
		public int compareTo(CompositeState o) {
			return (int)Math.signum(o.score()-score());
		}
		
		public String toString() {
			StringBuffer sbuf = new StringBuffer();
			for (State<S> state : states) {
				sbuf.append(state).append(",");
			}
			sbuf.append(String.format(" score: %.3f", score));
			return sbuf.toString();
		}
	}


	@Override
	public Iterator<List<S>> iterator() {
		return this;
	}
}
