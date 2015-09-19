package edu.stanford.nlp.mt.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Compact representation of a training example.
 * 
 * @author Spence Green
 *
 */
public class AlignedSentence implements Serializable {

  private static final long serialVersionUID = 1481297672562948109L;

  // 256-1, since 0 is used to indicate a null alignment in the compact
  // representation.
  public static final int MAX_SENTENCE_LENGTH = 256 - 1;
  public static final int MAX_FERTILITY = 4;
  
  public int[] source;
  public int[] target;

  protected int[] f2e;
  protected int[] e2f;
  
  /**
   * No-arg constructor for deserialization.
   */
  public AlignedSentence() {}
  
  /**
   * Constructor.
   * 
   * @param source
   * @param target
   * @param f2e
   * @param e2f
   */
  public AlignedSentence(int[] source, int[] target, 
      Set<Integer>[] f2e, Set<Integer>[] e2f) throws IllegalArgumentException {
    if (source.length > MAX_SENTENCE_LENGTH) throw new IllegalArgumentException();
    if (target.length > MAX_SENTENCE_LENGTH) throw new IllegalArgumentException();
    this.source = source;
    this.target = target;
    this.f2e = flatten(f2e);
    this.e2f = flatten(e2f);
  }

  /**
   * Compress a set of alignment links into an integer array.
   * 
   * @param algn
   * @return
   */
  private static int[] flatten(Set<Integer>[] algn) {
    int[] flatArr = new int[algn.length];
    for (int i = 0; i < flatArr.length; ++i) {
      if (algn[i] == null) continue;
      List<Integer> points = new ArrayList<>(algn[i]);
      int al = 0;
      for (int j = 0, sz = Math.min(MAX_FERTILITY, points.size()); 
          j < sz; ++j) {
        int pos = (points.get(j)+1) << (j*8);
        al |= pos;
      }
      flatArr[i] = al;
    }
    return flatArr;
  }
  
  public int[] f2e(int i) {
    if (i < 0 || i >= source.length) throw new IndexOutOfBoundsException();
    return expand(f2e[i]);
  }
  
  public int[] e2f(int i) {
    if (i < 0 || i >= target.length) throw new IndexOutOfBoundsException();
    return expand(e2f[i]);
  }
  
  public boolean isSourceUnaligned(int i) {
    if (i < 0 || i >= source.length) throw new IndexOutOfBoundsException();
    return f2e[i] == 0;
  }
  
  public boolean isTargetUnaligned(int i) {
    if (i < 0 || i >= target.length) throw new IndexOutOfBoundsException();
    return e2f[i] == 0;
  }
  
  public static int[] expand(int al) {
    if (al == 0) return new int[0];
    final int numLinks = ((31 - Integer.numberOfLeadingZeros(al)) / 8) + 1;
    final int[] links = new int[numLinks];
    for (int i = 0; i < numLinks; ++i) {
      int pos = i * 8;
      int mask = 0xff << pos;
      int lnk = (al & mask) >> pos;
      links[i] = lnk - 1;
    }
    return links;
  }
  
  public int sourceLength() { return source.length; }
  
  public int targetLength() { return target.length; }
  
  /**
   * Get the source sentence as an {@link Sequence} object.
   * 
   * @param index
   * @return
   */
  public Sequence<IString> getSource(Vocabulary index) { 
    return IStrings.toIStringSequence(source, index); 
  }

  /**
   * Get the target sentence as an {@link Sequence} object.
   * 
   * @param index
   * @return
   */
  public Sequence<IString> getTarget(Vocabulary index) { 
    return IStrings.toIStringSequence(target, index); 
  }
}