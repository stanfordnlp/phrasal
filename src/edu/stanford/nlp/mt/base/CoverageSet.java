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

  public boolean isContiguous() {
    return cardinality() == (length() - nextSetBit(0));
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

}