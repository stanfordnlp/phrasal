package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;

/**
 * A simple a-star based Lattice decoder
 * 
 * @author danielcer
 * 
 * @param <S>
 */
public class StateLatticeDecoder<S extends State<S>> implements
    Iterator<List<S>>, Iterable<List<S>> {

  private final PriorityQueue<CompositeState> agenda;
  private final Set<CompositeState> dupCheckSet;
  private final RecombinationHistory<S> recombinationHistory;

  /**
   * Constructor.
   * 
   * @param goalStates
   * @param recombinationHistory
   */
  public StateLatticeDecoder(List<S> goalStates, RecombinationHistory<S> recombinationHistory) {
    this.recombinationHistory = recombinationHistory;
    agenda = new PriorityQueue<>(2000);
    dupCheckSet = new HashSet<>(2000);
    
    // initialize score deltas
    for (S goalState : goalStates) {
      assert goalState != null;
      CompositeState newComposite = new CompositeState(goalState);
      dupCheckSet.add(newComposite); // not actually necessary right now
      agenda.add(newComposite);
    }
  }

  @Override
  public boolean hasNext() {
    return !agenda.isEmpty();
  }

  @Override
  public List<S> next() {
    final CompositeState best = agenda.remove();
    for (int i = 0, sz = best.states.size(); i < sz; i++) {
      final List<S> recombinedStates = recombinationHistory.recombinations(best.states.get(i));
      for (S recombinedState : recombinedStates) {
        CompositeState newComposite = new CompositeState(best, recombinedState, i);
        if ( ! dupCheckSet.contains(newComposite)) {
          dupCheckSet.add(newComposite);
          agenda.add(newComposite);
        }
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

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
      CompositeState oCS = (CompositeState) o;
      return score == oCS.score && // comparing "score" speeds up equals()
          states.equals(oCS.states);
    }

    @SuppressWarnings("unchecked")
    public CompositeState(S goalState) {
      int length = goalState.depth() + 1;
      states = new ArrayList<S>(length);
      states.addAll(Collections.nCopies(length, (S) null));
      int pos = length - 1;
      for (State<S> state = goalState; state != null; state = state.parent(), --pos)
        states.set(pos, (S) state);
      score = goalState.partialScore();
      hashCode = states.hashCode();
    }

    private double computeListCost() {
      double cost = 0.0;
      for (State<S> state : states) {
        State<S> parent = state.parent();
        double parentScore = (parent == null ? 0 : parent.partialScore());
        cost += state.partialScore() - parentScore;
      }
      return cost;
    }

    @SuppressWarnings("unchecked")
    public CompositeState(CompositeState original, S varState, int varPosition) {
      int newPrefixLength = varState.depth() + 1;
      int length = original.states.size() + newPrefixLength - varPosition - 1;
      states = new ArrayList<S>(length);
      states.addAll(Collections.nCopies(newPrefixLength, (S) null));
      int pos = newPrefixLength - 1;
      for (State<S> state = varState; state != null; state = state.parent(), --pos)
        states.set(pos, (S) state);
      int originalStatesLength = original.states.size();
      for (int i = varPosition + 1; i < originalStatesLength; i++) {
        states.add(original.states.get(i));
      }
      score = computeListCost();
      hashCode = states.hashCode();
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
