package edu.stanford.nlp.mt.decoder.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;
import edu.stanford.nlp.mt.util.MurmurHash2;

/**
 * A simple a-star based Lattice decoder
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <S>
 */
public class StateLatticeDecoder<S extends State<S>> implements
    Iterator<List<S>>, Iterable<List<S>> {

  private final PriorityQueue<CompositeState> agenda;
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
    
    // Initialize the agenda with list of goal nodes
    for (S goalState : goalStates) {
      assert goalState != null;
      CompositeState newComposite = new CompositeState(goalState);
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
      final S currentState = best.states.get(i);
      final List<S> recombinedStates = recombinationHistory.recombinations(currentState);
      for (S recombinedState : recombinedStates) {
        CompositeState newComposite = new CompositeState(best, recombinedState, i);
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
    public final List<S> states;
    private final double score;
    private final int hashCode;

    @Override
    public int hashCode() {
      return hashCode;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      else if ( ! (o instanceof StateLatticeDecoder.CompositeState)) return false;
      else {
        CompositeState oCS = (CompositeState) o;
        return score == oCS.score && // comparing "score" speeds up equals()
            states.equals(oCS.states);
      }
    }

    @SuppressWarnings("unchecked")
    public CompositeState(S goalState) {
      final int length = goalState.depth() + 1;
      State<S>[] stateArr = new State[length];
      int[] hashArr = new int[length];
      State<S> state = goalState;
      for (int i = length-1; i >= 0 && state != null; state = state.parent(), --i) {
        stateArr[i] = state;
        hashArr[i] = state.hashCode();
      }
      states = (List<S>) Arrays.asList(stateArr);
      score = goalState.partialScore();
      hashCode = MurmurHash2.hash32(hashArr, hashArr.length, 1);
    }
    
    /**
     * This method is counter-intuitive. In the case of multiple recombinations along
     * a single path, the parent pointers are invalid. So we need to sum transition costs
     * into each node on the lattice path.
     * 
     * @return
     */
    private double scorePath() {
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
      final int newPrefixLength = varState.depth() + 1;
      final int length = original.states.size() + newPrefixLength - varPosition - 1;
      State<S>[] stateArr = new State[length];
      int[] hashArr = new int[length];
      State<S> newState = varState;
      for (int i = newPrefixLength - 1; i >= 0 && newState != null; newState = newState.parent(), --i) {
        stateArr[i] = newState;
        hashArr[i] = newState.hashCode();
      }
      for (int i = varPosition + 1, sz = original.states.size(), j = newPrefixLength; i < sz; i++, ++j) {
        assert j < stateArr.length;
        stateArr[j] = original.states.get(i);
        hashArr[j] = stateArr[j].hashCode();
      }
      states = (List<S>) Arrays.asList(stateArr);
      hashCode = MurmurHash2.hash32(hashArr, hashArr.length, 1);
      score = scorePath();
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
