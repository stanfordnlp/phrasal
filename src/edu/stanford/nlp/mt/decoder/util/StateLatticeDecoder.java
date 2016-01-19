package edu.stanford.nlp.mt.decoder.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;

/**
 * A simple a-star based lattice decoder.
 * 
 * TODO(spenceg) The underlying agenda becomes enormous.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <S>
 */
public class StateLatticeDecoder<S extends State<S>> implements
    Iterator<List<S>>, Iterable<List<S>> {

  // Set empirically assuming n-best size of 200, the standard value for
  // tuning.
  private static final int DEFAULT_INITIAL_CAPACITY = 25000;
  
  private final PriorityQueue<CompositeState> agenda;
  private final RecombinationHistory<S> recombinationHistory;
  public int maxAgendaSize = 0; 

  private int stateCounter = 0;
  private boolean expandedFirstItem = false;
  
  /**
   * Constructor.
   * 
   * @param goalStates
   * @param recombinationHistory
   */
  public StateLatticeDecoder(List<S> goalStates, RecombinationHistory<S> recombinationHistory) {
    this.recombinationHistory = recombinationHistory;
    agenda = new PriorityQueue<>(DEFAULT_INITIAL_CAPACITY);
    
    // Initialize the agenda with list of goal nodes
    for (S goalState : goalStates) {
//      System.err.printf("put %d%n", stateCounter);
      agenda.add(new CompositeState(goalState, stateCounter++));
    }
    
    // TODO(spenceg) Initialize the agenda here so that we can remove expandedFirstItem.
    
  }

  @Override
  public boolean hasNext() {
    return ! agenda.isEmpty();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<S> next() {
    final CompositeState best = agenda.poll();
    best.extractPath(); // Lazily expand the best path
//    System.err.printf("get %d%n", best.id);
    final int sz = expandedFirstItem ? Math.min(best.varPosition+1, best.states.length) : best.states.length;
    expandedFirstItem = true;
    for (int i = 0; i < sz; i++) {
      // Undo recombinations along the Viterbi path.
      final S currentState = (S) best.states[i];
      final List<S> recombinedStates = recombinationHistory.recombinations(currentState);
      for (S recombinedState : recombinedStates) {
//        System.err.printf(" put %d (%d)%n", stateCounter, i);
        CompositeState newComposite = new CompositeState(best, recombinedState, i, stateCounter++);
        agenda.add(newComposite);
      }
    }

    // Bookkeeping
    if (agenda.size() > maxAgendaSize) maxAgendaSize = agenda.size();
    return (List<S>) Arrays.asList(best.states);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private class CompositeState implements Comparable<CompositeState> {
    public State<S>[] states;
    private double score;

    // For lazy path expansion
    private CompositeState original;
    private State<S> varState;
    private int varPosition;
    
    public int id;

    @SuppressWarnings("unchecked")
    public CompositeState(S goalState, int id) {
      Objects.requireNonNull(goalState);
      this.id = id;
      
      // Expand goal states immediately.
      final int length = goalState.depth() + 1;
      states = new State[length];
      State<S> state = goalState;
      for (int i = length-1; i >= 0 && state != null; state = state.parent(), --i) {
        states[i] = state;
      }
      score = goalState.partialScore();
      original = null;
    }
    
    /**
     * This method is counter-intuitive. In the case of multiple recombinations along
     * a single path, the parent pointers are invalid. So we need to sum transition costs
     * into each node on the lattice path.
     * 
     * @return
     */
//    private double scorePath() {
//      double cost = 0.0;
//      for (State<S> state : states) {
//        State<S> parent = state.parent();
//        double parentScore = (parent == null ? 0 : parent.partialScore());
//        cost += state.partialScore() - parentScore;
//      }
//      return cost;
//    }

    @SuppressWarnings("unchecked")
    public void extractPath() {
      if (states != null) return;
      final int newPrefixLength = varState.depth() + 1;
      final int length = original.states.length + newPrefixLength - varPosition - 1;
      states = new State[length];
      State<S> newState = varState;
      for (int i = newPrefixLength - 1; i >= 0 && newState != null; newState = newState.parent(), --i) {
        states[i] = newState;
      }
      for (int i = varPosition + 1, sz = original.states.length, j = newPrefixLength; i < sz; i++, ++j) {
        assert j < states.length;
        states[j] = original.states[i];
      }
    }
    
    public CompositeState(CompositeState original, State<S> varState, int varPosition, int id) {
      Objects.requireNonNull(original);
      Objects.requireNonNull(varState);
      this.id = id;
      
      this.varState = varState;
      this.original = original;
      this.varPosition = varPosition;

      State<S> childState = varState;
      
      // Walk backwards
      double cost = 0.0;
      while(childState.parent() != null) {
        State<S> parent = childState.parent();
        cost += childState.partialScore() - (parent == null ? 0.0 : parent.partialScore());
        childState = parent;
      }
      
      // Walk forwards
      for (int i = varPosition + 1, sz = original.states.length; i < sz; i++) {
        childState = original.states[i];
        State<S> parent = childState.parent();
        cost += childState.partialScore() - (parent == null ? 0.0 : parent.partialScore());
      }
      score = cost;     
    }

    public double score() {
      return score;
    }

    @Override
    public int compareTo(CompositeState o) {
      return (int) Math.signum(o.score() - score());
    }

    @Override
    public String toString() {
      if (states == null) extractPath();
      StringBuilder sbuf = new StringBuilder();
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
