package edu.stanford.nlp.mt.train;

import java.util.BitSet;
import java.util.Set;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.SortedSet;

import edu.stanford.nlp.mt.tm.FlatPhraseTable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Abstract class representing a set of word alignments for one sentence pair.
 * It defines both source-to-target and target-to-source word aligments, which
 * are not necessarily symmetrical (since words aligners such as GIZA do not
 * produce symmetrical word alignments).
 * 
 * @see GIZAWordAlignment
 * @see SymmetricalWordAlignment
 * 
 * @author Michel Galley
 */

public class AbstractWordAlignment implements WordAlignment {

  public static final String DEBUG_PROPERTY = "DebugWordAlignment";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public static final boolean KEEP_BAD_TOKENS = false;

  protected Integer id;

  Sequence<IString> f;
  Sequence<IString> e;
  SortedSet<Integer>[] f2e;
  SortedSet<Integer>[] e2f;

  AbstractWordAlignment() {
  }

  AbstractWordAlignment(Sequence<IString> f, Sequence<IString> e,
      SortedSet<Integer>[] f2e, SortedSet<Integer>[] e2f) {
    id = 0;
    this.f = f;
    this.e = e;
    this.f2e = f2e;
    this.e2f = e2f;
  }

  public void reverse() {
    Sequence<IString> tmpS = f;
    f = e;
    e = tmpS;
    SortedSet<Integer>[] tmpSS = f2e;
    f2e = e2f;
    e2f = tmpSS;
  }

  @Override
  public Integer getId() {
    return id;
  }

  @Override
  public Sequence<IString> f() {
    return f;
  }

  @Override
  public Sequence<IString> e() {
    return e;
  }

  public int fSize() {
    return f.size();
  }

  public int eSize() {
    return e.size();
  }

  @Override
  public SortedSet<Integer> f2e(int i) {
    return f2e[i];
  }

  @Override
  public SortedSet<Integer> e2f(int i) {
    return e2f[i];
  }

  @Override
  public int f2eSize(int i, int min, int max) {
    return _size(f2e[i], min, max);
  }

  @Override
  public int e2fSize(int i, int min, int max) {
    return _size(e2f[i], min, max);
  }

  private static int _size(SortedSet<Integer> al, int min, int max) {
    int count = 0;
    for (int el : al) {
      if (el > max)
        return count;
      if (el >= min)
        ++count;
    }
    return count;
  }

  static String toString(Set<Integer>[] align) {
    return toString(align, true);
  }

  static String toString(Set<Integer>[] align, boolean zeroIndexed) {
    final int o = zeroIndexed ? 0 : 1;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < align.length; ++i) {
      for (int j : align[i]) {
        if (sb.length() > 0) sb.append(" ");
        sb.append(i + o).append("-").append(j + o);
      }
    }
    return sb.toString();
  }

  /**
   * Filter training data by escaping special characters.
   * 
   * @param words
   * @return output sentence
   */
  static public String[] escape(String[] words) {
    for (int i = 0; i < words.length; ++i) {
      if (words[i].contains(FlatPhraseTable.FIELD_DELIM)) {
        words[i] = words[i].replace(FlatPhraseTable.FIELD_DELIM, ",");
      }
    }
    return words;
  }

  public boolean equals(Object o) {
    assert (o instanceof AbstractWordAlignment);
    AbstractWordAlignment wa = (AbstractWordAlignment) o;
    if (!f.equals(wa.f()) || !e.equals(wa.e()))
      return false;
    for (int i = 0; i < f.size(); ++i)
      if (!f2e[i].equals(wa.f2e[i]))
        return false;
    for (int i = 0; i < e.size(); ++i)
      if (!e2f[i].equals(wa.e2f[i]))
        return false;
    return true;
  }

  public int hashCode() {
    ArrayList<Integer> hs = new ArrayList<Integer>(2 + f2e.length + e2f.length);
    hs.add(e().hashCode());
    hs.add(f().hashCode());
    for (Set<Integer> af2e : f2e)
      hs.add(Arrays.hashCode(af2e.toArray()));
    for (Set<Integer> ae2f : e2f)
      hs.add(Arrays.hashCode(ae2f.toArray()));
    return hs.hashCode();
  }

  public double ratioFtoE() {
    assert (eSize() > 0);
    return fSize() * 1.0 / eSize();
  }

  public boolean isAdmissiblePhraseF(int i, int j) {
    boolean empty = true;
    for (int k = i; k <= j; ++k)
      for (int ei : f2e[k]) {
        empty = false;
        for (int fi : e2f[ei])
          if (fi < i && fi > j)
            return false;
      }
    return !empty;
  }

  /**
   * Initialize alignment using a matrix in LDC format (such as the ones used in
   * parallel treebanks. Convention: 1-indexed words, and index zero reseved for
   * unaligned words.
   * 
   * @param matrix
   *          alignment matrix
   */
  @SuppressWarnings("unchecked")
  public void init(int[][] matrix) {

    f2e = new TreeSet[matrix[0].length - 1];
    for (int i = 0; i < f2e.length; ++i)
      f2e[i] = new TreeSet<Integer>();

    e2f = new TreeSet[matrix.length - 1];
    for (int i = 0; i < e2f.length; ++i)
      e2f[i] = new TreeSet<Integer>();

    for (int i = 1; i < matrix.length; ++i)
      for (int j = 1; j < matrix[0].length; ++j)
        if (matrix[i][j] != 0) {
          e2f[i - 1].add(j - 1);
          f2e[j - 1].add(i - 1);
        }
  }

  @Override
  public BitSet unalignedF() {
    BitSet unaligned = new BitSet();
    for (int fi = 0; fi < f().size(); ++fi) {
      if (f2e(fi).isEmpty())
        unaligned.set(fi);
    }
    return unaligned;
  }

  @Override
  public BitSet unalignedE() {
    BitSet unaligned = new BitSet();
    for (int ei = 0; ei < e().size(); ++ei) {
      if (e2f(ei).isEmpty())
        unaligned.set(ei);
    }
    return unaligned;
  }

  public boolean isEmpty() {
    for (SortedSet<Integer> ss : f2e)
      if (!ss.isEmpty())
        return false;
    for (SortedSet<Integer> ss : e2f)
      if (!ss.isEmpty())
        return false;
    return true;
  }

}
