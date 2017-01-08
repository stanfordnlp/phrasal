package edu.stanford.nlp.mt.train;

import java.util.BitSet;
import java.util.SortedSet;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Interface to word alignments for one sentence pair.
 * 
 * @see AbstractWordAlignment
 * 
 * @author Michel Galley
 */
public interface WordAlignment {

  public Integer getId();

  public Sequence<IString> f();

  public Sequence<IString> e();

  public BitSet unalignedF();

  public BitSet unalignedE();

  public SortedSet<Integer> f2e(int i);

  public SortedSet<Integer>[] f2e();
  
  public SortedSet<Integer> e2f(int i);

  public SortedSet<Integer>[] e2f();

  public int f2eSize(int i, int min, int max);

  public int e2fSize(int i, int min, int max);

}
