package mt.base;

import it.unimi.dsi.fastutil.longs.*;
import edu.stanford.nlp.util.IString;


/**
 * Implementation of IntegerArrayIndex as a trie. This trie implementation 
 * is optimized for space, by instanciating only one object: an open-address hash table. 
 * Keys stored into the trie are arrays of int primitive types.
 *
 * @author Michel Galley
 */
public class TrieIntegerArrayIndex implements IntegerArrayIndex, IntegerArrayRawIndex {

  private static final int GROWTH_FACTOR = 4; // slow growth, we want to save space

  private static final int IDX_ROOT = 0;
  private static final int IDX_NOSUCCESSOR = Integer.MIN_VALUE;

  private final Long2IntOpenHashMap map; 
  // maps transitions to next state. Each transition is a long, whose 32 first bits 
  // identify an input symbol, and 32 last bits identify the current state.

  private int lastStateIdx = IDX_ROOT;

  TrieIntegerArrayIndex() {
    this(-1);
  }

  TrieIntegerArrayIndex(int sz) {
    if(sz > 0)
      map = new Long2IntOpenHashMap(sz);
    else
      map = new Long2IntOpenHashMap();
    map.growthFactor(GROWTH_FACTOR); 
    map.defaultReturnValue(IDX_NOSUCCESSOR);
    System.err.println("TrieIntegerArrayIndex: constructor.");
  }

  int supplementalHash(int h) {
    // use the same supplemental hash function used by HashMap
    return ((h << 7) - h + (h >>> 9) + (h >>> 17));
  }

  private synchronized long getTransition(int curState, int input) {
    // Perform some bit manipulations because Long's hashCode is not particularly clever.
    int input2 = supplementalHash(input);
    int curState2 = supplementalHash(curState);
    return (((long)input2) << 32) | (((long)curState2) & 0xffffffffL);
  }

  public int size() { return lastStateIdx+1; }

  public void printInfo() {
    System.err.printf("Number of states: %d\n", size());
    System.err.printf("Map size:%d\n", map.size());
  }

  public int indexOf(int[] input) {
    return indexOf(input, false);
  }

  public synchronized int indexOf(int[] input, boolean add) {
    int curState = IDX_ROOT;
    for(int i=0; i<input.length; ++i) {
      long transition = getTransition(curState, input[i]);
      int nextState = map.get(transition);
      if(nextState == IDX_NOSUCCESSOR) {
        if(!add) return -1;
        if(lastStateIdx == Integer.MAX_VALUE)
          throw new RuntimeException("Running out of state indices!");
        nextState = ++lastStateIdx;
        map.put(transition, nextState);
      }
      curState = nextState;
    }
    return curState;
  }

  public void rehash() {
    map.rehash();
  }

  public int getIndex(int[] array) {
    return indexOf(array,false);
  }

  public int insertIntoIndex(int[] array) {
    return indexOf(array,true);
  }

  public static void main(String[] args) {
    TrieIntegerArrayIndex idx = new TrieIntegerArrayIndex();
    test(idx);
    idx.printInfo();
    //test(new TrieIntegerArrayIndex());
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
