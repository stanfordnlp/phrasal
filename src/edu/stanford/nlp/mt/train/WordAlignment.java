package edu.stanford.nlp.mt.train;

import java.util.BitSet;
import java.util.SortedSet;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;

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

  @SuppressWarnings("unused")
  public BitSet unalignedF();
  @SuppressWarnings("unused")
  public BitSet unalignedE();

  public SortedSet<Integer> f2e(int i);
  public SortedSet<Integer> e2f(int i);

  @SuppressWarnings("unused")
  public int f2eSize(int i, int min, int max);
  @SuppressWarnings("unused")
  public int e2fSize(int i, int min, int max);

}
