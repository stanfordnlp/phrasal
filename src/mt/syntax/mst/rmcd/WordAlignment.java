package mt.syntax.mst.rmcd;

import mt.base.IString;

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
  public int[] f2e(int i);
  public int[] e2f(int i);
}
