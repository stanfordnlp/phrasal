package edu.stanford.nlp.mt.base;

import java.util.*;

/**
 * 
 * @author Michel Galley
 *
 */
public class DynamicByteArrayIndex implements Iterable<int[]>, IntegerArrayIndex {
  
  public static final long serialVersionUID = 126L;

  private static final boolean PRINT_SIZE_STATS = false;

  private static final int SZ_BITS = 3;
  private static final int SZ_INC  = Integer.SIZE / (1 << SZ_BITS);

  static final int INIT_SZ = 1<<10;
  static final double MAX_LOAD = 0.60;

  byte[][] keys; int[] values; int mask;
  int[] hashCodes;
  int[] reverseIndex;
  int maxIndex; int load;

  public static final DynamicByteArrayIndex CommonDynamiIntegerArrayIndex = new DynamicByteArrayIndex();

  public DynamicByteArrayIndex() {
    init();
  }

  private void init() {
    keys = new byte[INIT_SZ][];
    values = new int[INIT_SZ];
    hashCodes = new int[INIT_SZ];
    reverseIndex = new int[INIT_SZ]; Arrays.fill(reverseIndex, -1);
    mask = INIT_SZ - 1;
  }

  private int supplementalHash(int h) {
      // use the same supplemental hash function used by HashMap
      return ((h << 7) - h + (h >>> 9) + (h >>> 17));
  }

  private int findPos(byte[] e, boolean add) {
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
  public synchronized int[] get(int idx) {
    return decompress(getB(idx));
  }

  @SuppressWarnings("unchecked")
  private byte[] getB(int idx) {
      int pos = reverseIndex[idx];
      if (pos == -1) return null;
      return keys[pos];
  }

  private void sizeUp() {
    int newSize = keys.length<<1;
    mask = newSize-1;
    //System.err.printf("size up to: %d\n", newSize);
    byte[][] oldKeys = keys; int[] oldValues = values; int[] oldHashCodes = hashCodes;
    keys = new byte[newSize][]; values = new int[newSize];
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
  private int getSearchOffset(int pos, byte[] key) {
      int idealIdx = supplementalHash(Arrays.hashCode(key)) & mask;
      int distance;
      if (idealIdx < pos) {
    distance = pos + keys.length - idealIdx;
      } else {
    distance = pos - idealIdx;
      }
      return distance;
  }

  private int add(byte[] key, int pos, boolean sharedRep) {
    if ((load++)/(double)keys.length > MAX_LOAD) {
      sizeUp();
      pos = -findPos(key, true)-1;
    }
    if (!sharedRep) {
      keys[pos] = Arrays.copyOf(key, key.length); values[pos] = maxIndex++;
    } else {
      keys[pos] = CommonDynamiIntegerArrayIndex.getB(CommonDynamiIntegerArrayIndex.indexOf(key, true)); values[pos] = maxIndex++;
    }
    reverseIndex[values[pos]] = pos;
    hashCodes[pos] = supplementalHash(Arrays.hashCode(key));
    return maxIndex-1;
  }

  public synchronized int indexOf(int[] key) {
      int pos = findPos(compress(key), false);
      if (pos < 0) return -1;
      return values[pos];
  }

  private synchronized boolean contains(byte[] key) {
      int pos = findPos(key, false);
      if (pos < 0) return false;
      return true;
  }

  private synchronized int commonRepIndexOf(byte[] key, boolean add) {
    int pos = findPos(key, add);
    if (pos >= 0) return values[pos];
    if (!add) return -1;
    int insert = add(key, -pos-1,true);

    return insert;
  }


  public synchronized int indexOf(int[] key, boolean add) {
    return indexOf(compress(key), add);
  }

  private int indexOf(byte[] key, boolean add) {
    int pos = findPos(key, add);
    if (pos >= 0) return values[pos];
    if (!add) return -1;
    if (PRINT_SIZE_STATS) {
      System.err.printf("sz:\t%d\n", key.length);
      for (int k : key)
        System.err.printf("hob:\t%d\t%d\n", Integer.highestOneBit(k), k);
    }
    //System.out.printf("adding: %s %d\n", key, -pos-1);
    return add(key, -pos-1, false); /*
    if (pos != sanityIndex.indexOf(key, true)) {
  System.err.printf("%d != %d", pos, sanityIndex.indexOf(key));
  System.exit(-1);
    } */
  }

  public synchronized int size() {
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

	public static void main(String[] args) {
		int[] a = new int[args.length];
	  for (int i=0; i<args.length; ++i)
			a[i] = Integer.parseInt(args[i]);
	  byte[] c = compress(a);
		int[] d = decompress(c);
		System.out.printf("sz: %d -> %d array: %s\n", c.length*Integer.SIZE, d.length*Byte.SIZE, Arrays.toString(d));
	}

  private static byte[] compress(int[] in) {

		// Pre-compute # of bytes:
    int outSz = 0;
    for (int el : in) {
      int wordSz = sz(el+1, SZ_INC)*SZ_INC;
      outSz += SZ_BITS + wordSz;
    }
    outSz += Byte.SIZE - (outSz % Byte.SIZE);

		byte[] out = new byte[outSz/Byte.SIZE];
		int pos = 0;
		for (int el : in) {
			int wordSz = sz(el+1,SZ_INC);
			add(out, pos, SZ_BITS, wordSz); // add size
      pos += SZ_BITS;
			add(out, pos, wordSz*SZ_INC, el+1); // add compressed int
      pos += wordSz*SZ_INC;
		}

		return out;
	}

  static int[] decompress(byte[] in) {
    // Determine size:
    int idx=0, outSz=0;
    for (;;) {
      int wordSz = read(in, idx, SZ_BITS) * SZ_INC;
      idx += SZ_BITS;
      if (read(in, idx, wordSz) == 0)
        break;
      idx += wordSz;
      ++outSz;
    }

    // Write data to int[]:
    int[] out = new int[outSz];
    idx=0;
    for (int i=0; i<out.length; ++i) {
      int wordSz = read(in, idx, SZ_BITS) * SZ_INC;
      idx += SZ_BITS;
      out[i] = read(in, idx, wordSz)-1;
      idx += wordSz;
    }
    return out;
  }

  private static int read(byte[] a, int pos, int sz) {
    int byteIdx = pos / Byte.SIZE;
    int idx = pos % Byte.SIZE;
    int datum = 0;
    int outputIdx = 0;
    while (outputIdx < sz && byteIdx < a.length) {
      int shift = Byte.SIZE - idx;
      datum |= (((1<<shift)-1) & (a[byteIdx] >> idx)) << outputIdx;
      outputIdx += shift;
      idx = 0;
      ++byteIdx;
    }
    datum &= ((1<<sz)-1);
    return datum;
  }

	private static void add(byte[] a, int pos, int sz, int datum) {
		int byteIdx = pos / Byte.SIZE;
		int idx = pos % Byte.SIZE;
    int inputIdx = 0;
    do {
      a[byteIdx] |= datum << idx;
      int shift = Byte.SIZE - idx;
      datum = datum >> shift;
      inputIdx += shift;
      idx = 0;
      ++byteIdx;
    } while (inputIdx < sz);
	}
	
	private static int sz(int el, int step) {
		int s = 1;
		while ((el = el >> step) > 0) ++s;
		return s;
	}

}
