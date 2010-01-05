package edu.stanford.nlp.mt.base;

/**
 * @author Michel Galley
 */
public interface IntegerArrayIndex {

  public int size();

  public int[] get(int idx);

  public int indexOf(int[] key);

	public int indexOf(int[] key, boolean add);
}
