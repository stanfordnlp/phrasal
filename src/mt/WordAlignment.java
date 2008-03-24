package mt;

import java.util.Set;

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
