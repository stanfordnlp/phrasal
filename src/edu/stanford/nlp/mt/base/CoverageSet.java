package edu.stanford.nlp.mt.base;

import java.util.BitSet;

/**
 * util.BitSet with a faster clone operation.
 * 
 * @author danielcer
 *
 */
public class CoverageSet extends BitSet {
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

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    int end=0;
    for (;;) {
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

  public static boolean areContiguous(CoverageSet cs1, CoverageSet cs2) {
    int min1 = cs1.nextSetBit(0); int max1 = cs1.length();
    int min2 = cs2.nextSetBit(0); int max2 = cs2.length();

    // Check if there is a gap between cs1 and cs2. If so, return false:
    if (max2 < min1 || max1 < min2) return false;

    int min = Math.min(min1, min2);
    int max = Math.max(max1, max2);
    for (int i=min+1; i<max; ++i) {
      if (!cs1.get(i) && !cs2.get(i))
        return false;
    }
    return true;
  }

}