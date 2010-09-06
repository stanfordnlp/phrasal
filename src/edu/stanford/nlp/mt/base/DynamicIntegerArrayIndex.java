package edu.stanford.nlp.mt.base;

import java.util.*;

/**
 * 
 * @author danielcer
 *
 */
public class DynamicIntegerArrayIndex implements Iterable<int[]>, IntegerArrayIndex {

	  public static final long serialVersionUID = 127L;
	    
	  static final int INIT_SZ = 1<<10;
	  static final double MAX_LOAD = 0.60;
	  
	  protected int[][] keys;
    protected int[] values;
    protected int mask;
	  protected int[] hashCodes;
	  protected int[] reverseIndex;
	  protected int maxIndex;
    protected int load;
    protected boolean locked = false;

	  public static final DynamicIntegerArrayIndex CommonDynamiIntegerArrayIndex = new DynamicIntegerArrayIndex();
	  
	  public DynamicIntegerArrayIndex() { 
	    keys = new int[INIT_SZ][];
	    values = new int[INIT_SZ];
	    hashCodes = new int[INIT_SZ];
	    reverseIndex = new int[INIT_SZ];
      Arrays.fill(reverseIndex, -1);
	    mask = INIT_SZ - 1;
	  }

	  protected static int supplementalHash(int h) {
	      // use the same supplemental hash function used by HashMap
	      return ((h << 7) - h + (h >>> 9) + (h >>> 17));
	  }

    @Override
    public void lock() {
      System.err.printf("%s locked.\n", this);
      this.locked = true;
    }

	  private int findPos(int[] e) {
	    int hashCode = supplementalHash(Arrays.hashCode(e));    
	    int idealIdx = hashCode & mask;
	    
	    for (int i = 0, idx = idealIdx; i < keys.length; i++, idx++) {
		    if (idx >= keys.length) idx = 0;
		    if (keys[idx] == null) return -idx-1;
		    if (hashCodes[idx] != hashCode) continue;
		    if (Arrays.equals(keys[idx], e)) return idx;
	    }        
	    return -keys.length-1;
	  }

	  @Override
    public int[] get(int idx) {
      if (locked)
        return get_unsync(idx);
      synchronized (this) {
        return get_unsync(idx);
      }
    }

    @SuppressWarnings("unchecked")
	  private int[] get_unsync(int idx) {
	      int pos = reverseIndex[idx];
	      if (pos == -1) return null;
	      return keys[pos];
	  }
	  
	  protected void sizeUp() {
	    int newSize = keys.length<<1;
	    mask = newSize-1;
	    //System.err.printf("size up to: %d\n", newSize);
	    int[][] oldKeys = keys; int[] oldValues = values; int[] oldHashCodes = hashCodes;
	    keys = new int[newSize][]; values = new int[newSize];
	    reverseIndex = new int[newSize]; Arrays.fill(reverseIndex, -1);
	    hashCodes = new int[newSize];
	    for (int i = 0; i < oldKeys.length; i++) { if (oldKeys[i]==null) continue;
	      int pos = -findPos(oldKeys[i])-1;
	      keys[pos] = oldKeys[i]; values[pos] = oldValues[i];
	      reverseIndex[values[pos]] = pos;
	      hashCodes[pos] = oldHashCodes[i];      
	    }
	  } 
	  
    @SuppressWarnings("unused")
    private int getSearchOffset(int pos, int[] key) {
	      int idealIdx = supplementalHash(Arrays.hashCode(key)) & mask;      
	      int distance;
	      if (idealIdx < pos) {
	  	distance = pos + keys.length - idealIdx;
	      } else {
	  	distance = pos - idealIdx;
	      }
	      return distance;
	  }
	  
	  private int add(int key[], int pos, boolean sharedRep) {
	    if ((load++)/(double)keys.length > MAX_LOAD) { 
	      sizeUp();
	      pos = -findPos(key)-1;
	    }
	    if (!sharedRep) {
	    	keys[pos] = Arrays.copyOf(key, key.length); values[pos] = maxIndex++;
	    } else {
	    	keys[pos] = CommonDynamiIntegerArrayIndex.get(CommonDynamiIntegerArrayIndex.indexOf(key, true)); values[pos] = maxIndex++;
	    }
	    reverseIndex[values[pos]] = pos;
	    hashCodes[pos] = supplementalHash(Arrays.hashCode(key));
	    return maxIndex-1;
	  }

    @Override
    public int indexOf(int[] key) {
      if (locked)
        return indexOf_unsync(key);
      synchronized (this) {
        return indexOf_unsync(key);
      }
    }

    private int indexOf_unsync(int[] key) {
	    int pos = findPos(key);
	    if (pos < 0) return -1;
	    return values[pos];
	  }

    /*
	  public boolean contains(int[] key) {
	    int pos = findPos(key);
      return pos >= 0;
    }
    */

    /*
    @SuppressWarnings("unused")
    public synchronized int commonRepIndexOf(int[] key, boolean add) {//s
      int pos = findPos(key);
      if (pos >= 0) return values[pos];
      if (!add) return -1;
      return add(key, -pos-1,true);
    }
    */

    @Override
    public int indexOf(int[] key, boolean add) {
      if (locked) {
        //if (add)
        //  throw new UnsupportedOperationException("Can't add key; index is locked.");
        return indexOf_unsync(key, false);
      }
      synchronized (this) {
        return indexOf_unsync(key, add);
      }
    }

    private int indexOf_unsync(int[] key, boolean add) {
      int pos = findPos(key);
      if (pos >= 0) return values[pos];
      if (!add) return -1;
      return add(key, -pos-1, false);
    }

    @Override
    public int size() {
      return load;
    }

    @Override
    public Iterator<int[]> iterator() {
      return new Itr();
    }

    private class Itr implements Iterator<int[]> {

      int cursor = 0;

      @Override
      public boolean hasNext() {
        return cursor < size();
      }

      @Override
      public int[] next() {
         return get(cursor++);
      }

      @Override
      public void remove() {
         throw new UnsupportedOperationException();
      }
      
    }
}
