package mt.train;

import java.util.Set;

import edu.stanford.nlp.util.IString;

import mt.base.Sequence;

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
  public Set<Integer> f2e(int i);
  public Set<Integer> e2f(int i);
}
