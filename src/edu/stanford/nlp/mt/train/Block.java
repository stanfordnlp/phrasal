package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.util.*;

/**
 * Same as IntQuadruple, but with method names that
 * reflect a two-coordinate system.
 *
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class Block extends IntQuadruple {
  
	private static final long serialVersionUID = 1L;
	
	public Block() { super(); }
  public Block(int f1, int f2, int e1, int e2) { super(f1,f2,e1,e2); }
  public int f1() { return get(0); }
  public int f2() { return get(1); }
  public int e1() { return get(2); }
  public int e2() { return get(3); }
}
