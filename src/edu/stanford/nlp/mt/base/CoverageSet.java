package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.util.IntPair;

import java.util.BitSet;
import java.util.Iterator;

/**
 * util.BitSet with a faster clone operation, a more readable toString()
 * result (e.g., {1,3-6} instead of {1,3,4,5,6}), and the ability
 * to iterate through bits set to true. Note: The BitSet iterator
 * doesn't allow removal.
 * 
 * @author danielcer
 * @author Michel Galley
 *
 */
public class CoverageSet extends BitSet implements Iterable<Integer> {

	private static final long serialVersionUID = 1L;

	public CoverageSet(int size) {
		super(size);
	}
	
	public CoverageSet() {
		super();
	}
	
	@Override
	public CoverageSet clone() {
		CoverageSet c = new CoverageSet(this.size());
		c.or(this);
		return c;
	}

  public boolean isContiguous() {
    return cardinality() == (length() - nextSetBit(0));
  }

  public static boolean cross(CoverageSet c1, CoverageSet c2) {
    int c1S = c1.nextSetBit(0); int c1E = c1.length()-1;
    int c2S = c2.nextSetBit(0); int c2E = c2.length()-1;
    return (c1S < c2S && c2S < c1E) || (c1S < c2E && c2E < c1E);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    int end=0;
    while (true) {
      int start = nextSetBit(end);
      if (start < 0)
        break;
      end = nextClearBit(start)-1;
      if (sb.length() > 1)
        sb.append(",");
      sb.append(start);
      if (end > start)
        sb.append("-").append(end);
      ++end;
    }
    sb.append("}");
    return sb.toString();
  }

  @Override
  public Iterator<Integer> iterator() {
    return new Iterator<Integer>() {

      int idx = nextSetBit(0);

      @Override
      public boolean hasNext() {
        return idx >= 0;
      }

      @Override
      public Integer next() {
        int ret = idx;
        idx = nextSetBit(idx+1);
        return ret;
      }

      @Override public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public Iterator<IntPair> getSegmentIterator() {
    return new Iterator<IntPair>() {

      int idx = nextSetBit(0);

      @Override
      public boolean hasNext() {
        return idx >= 0;
      }

      @Override
      public IntPair next() {
        int startIdx = idx;
        int endIdx = nextClearBit(idx);
        this.idx = nextSetBit(endIdx);
        return new IntPair(startIdx,endIdx-1);
      }

      @Override public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

}