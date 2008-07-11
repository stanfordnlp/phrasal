package mt.base;

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
	  
	  int[][] keys; int[] values; int mask;
	  int[] hashCodes;
	  int[] reverseIndex;
	  int maxIndex; int load;

	  public static final DynamicIntegerArrayIndex CommonDynamiIntegerArrayIndex = new DynamicIntegerArrayIndex();
	  
	  public DynamicIntegerArrayIndex() { 
		  init(); 
	  } 
	  
	  private void init() {
	    keys = new int[INIT_SZ][];
	    values = new int[INIT_SZ];
	    hashCodes = new int[INIT_SZ];
	    reverseIndex = new int[INIT_SZ]; Arrays.fill(reverseIndex, -1);
	    mask = INIT_SZ - 1;
	  }

	  int supplementalHash(int h) {
	      // use the same supplemental hash function used by HashMap
	      return ((h << 7) - h + (h >>> 9) + (h >>> 17));
	  }	  
	  
	  private int findPos(int[] e, boolean add) { 
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
	  
	  @SuppressWarnings("unchecked")
	  public int[] get(int idx) {
	      int pos = reverseIndex[idx];
	      if (pos == -1) return null;
	      return keys[pos];
	  }
	  
	  void sizeUp() {      
	    int newSize = keys.length<<1;
	    mask = newSize-1;
	    //System.err.printf("size up to: %d\n", newSize);
	    int[][] oldKeys = keys; int[] oldValues = values; int[] oldHashCodes = hashCodes;
	    keys = new int[newSize][]; values = new int[newSize];
	    reverseIndex = new int[newSize]; Arrays.fill(reverseIndex, -1);
	    hashCodes = new int[newSize];
	    for (int i = 0; i < oldKeys.length; i++) { if (oldKeys[i]==null) continue;
	      int pos = -findPos(oldKeys[i], true)-1; 
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
	  
	  int add(int key[], int pos, boolean sharedRep) {
	    if ((load++)/(double)keys.length > MAX_LOAD) { 
	      sizeUp();
	      pos = -findPos(key, true)-1;
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
	  	  
	  public int indexOf(int[] key) { 
	      int pos = findPos(key, false);
	      if (pos < 0) return -1;
	      return values[pos]; 
	  }
	  
	  public boolean contains(int[] key) {
	      int pos = findPos(key, false);
	      if (pos < 0) return false;
	      return true;
	  }
	  
	  public int commonRepIndexOf(int[] key, boolean add) {
	  	int pos = findPos(key, add);
	    if (pos >= 0) return values[pos];
	    if (!add) return -1;
	    int insert = add(key, -pos-1,true);

	    return insert;
	  }

	  public int indexOf(int[] key, boolean add) {
	    int pos = findPos(key, add);
	    if (pos >= 0) return values[pos];
	    if (!add) return -1;
	    //System.out.printf("adding: %s %d\n", key, -pos-1);
	    return add(key, -pos-1, false); /*
	    if (pos != sanityIndex.indexOf(key, true)) {
		System.err.printf("%d != %d", pos, sanityIndex.indexOf(key));
		System.exit(-1);
	    } */    
	  } 
	  
	  public int size() {
	    return load;
	  }

    public Iterator<int[]> iterator() {
      return new Itr();
    }

    private class Itr implements Iterator<int[]> {

      int cursor = 0;

      public boolean hasNext() {
        return cursor < size();
      }

      public int[] next() {
         return get(cursor++);
      }

      public void remove() {
         throw new UnsupportedOperationException();
      }
      
    }
}
