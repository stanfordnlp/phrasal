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
}