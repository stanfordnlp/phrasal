package mt.base;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;


/**
 * Implementation of IntegerArrayIndex as a trie. This trie implementation 
 * is optimized for space, by instanciating only one object: a hash table. 
 * Keys stored into the trie are arrays of int primitive types.
 *
 * @author Michel Galley
 */
public class TrieIntegerArrayIndex implements IntegerArrayIndex {

  private static final int IDX_NOSUCCESSOR = 0;
  private static final int IDX_ROOT = Integer.MAX_VALUE;
  private static final int INPUT_TERMINATOR = Integer.MAX_VALUE;
  
  private final Long2IntMap map; 
  // maps transitions to state. Each transition is a long, whose 32 first bits 
  // identify the current state, and 32 last bits identify an input symbol.

  private int
      lastTransitionalStateIdx = IDX_ROOT,
      lastAcceptingStateIdx = 0;

  TrieIntegerArrayIndex() {
    map = new Long2IntOpenHashMap();
    map.defaultReturnValue(IDX_NOSUCCESSOR);
    System.err.println("TrieIntegerArrayIndex: constructor.");
  }

  private long getTransition(int curState, int input) {
    return ((long)curState << 32) | input;
  }

  public int size() { return lastAcceptingStateIdx; }

  public int indexOf(int[] input) {
    return indexOf(input, false);
  }

  public int indexOf(int[] input, boolean add) {
    if(lastTransitionalStateIdx <= lastAcceptingStateIdx)
      throw new RuntimeException("Running out of state indices!");
    int curState = IDX_ROOT;
    // Transitional states:
    for(int i=0; i<input.length; ++i) {
      assert(input[i] != INPUT_TERMINATOR);
      long transition = getTransition(curState, input[i]);
      int nextState = map.get(transition);
      if(nextState == IDX_NOSUCCESSOR) {
        if(!add) return -1;
        nextState = --lastTransitionalStateIdx;
        map.put(transition, nextState);
      }
      curState = nextState;
    }
    // Accepting state:
    long transition = getTransition(curState, INPUT_TERMINATOR);
    int acceptingState = map.get(transition);
    if(acceptingState == IDX_NOSUCCESSOR) {
      if(!add) return -1;
      acceptingState = ++lastAcceptingStateIdx;
      map.put(transition, acceptingState);
    }
    return acceptingState;
  }

  public static void main(String[] args) {
    test(new TrieIntegerArrayIndex());
    test(new DynamicIntegerArrayIndex());
  }

  private static void test(IntegerArrayIndex index) { 
    System.out.println("idx(123): " + index.indexOf(new int[] {1, 2, 3}, true));
    System.out.println("idx(456): " + index.indexOf(new int[] {4, 5, 6}, true));
    System.out.println("idx(123): " + index.indexOf(new int[] {1, 2, 3}, true));
    System.out.println("idx(127): " + index.indexOf(new int[] {1, 2, 7}, true));
    System.out.println("idx(12): " + index.indexOf(new int[] {1, 2}, true));
  }

}
