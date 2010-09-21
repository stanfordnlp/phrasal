package edu.stanford.nlp.mt.train;

import java.util.*;
import java.io.IOException;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;

/**
 * An alignment template: a source-language phrase (f), a target-language phrase
 * (c), and word alignment.
 * 
 * @author Michel Galley
 */
public class AlignmentTemplate {

  public static final String DEBUG_PROPERTY = "DebugAlignmentTemplate";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public static final String DELIM = " ||| ";

  // phrases:
  Sequence<IString> f;
  Sequence<IString> e;

  // alignments:
  int[] align; // compact representation stored in AlignmentTables
  Set<Integer>[] f2e; // not used in lazy constructors
  Set<Integer>[] e2f; // not used in lazy constructors
  final Set<Short> alTable = new TreeSet<Short>(); // temporary table

  int key = -1;
  int fKey = -1;
  int eKey = -1;
  int aKey = -1;

  public AlignmentTemplate() {
  }

  /**
   * Read alignment template from strings (moses/pharaoh format).
   */
  public AlignmentTemplate(String fStr, String eStr, String aStr, boolean lazy)
      throws IOException {
    init(fStr, eStr, aStr, lazy);
  }

  /**
   * Create the alignment template from its internal representation in
   * AlignmentTemplates.
   */
  public AlignmentTemplate(int[] f, int[] e, int[] align, boolean lazy) {
    init(f, e, align, lazy);
  }

  void init(int[] fArray, int[] eArray, int[] align, boolean lazy) {
    reset();
    f = new SimpleSequence<IString>(true, IStrings.toIStringArray(fArray));
    e = new SimpleSequence<IString>(true, IStrings.toIStringArray(eArray));
    this.align = align;
    if (!lazy)
      initAlignmentArrays();
  }

  void init(String fStr, String eStr, String aStr, boolean lazy)
      throws IOException {
    reset();
    f = new SimpleSequence<IString>(true, IStrings.toIStringArray(fStr
        .split("\\s+")));
    e = new SimpleSequence<IString>(true, IStrings.toIStringArray(eStr
        .split("\\s+")));
    String[] aligns = aStr.split("\\s+");
    if (!lazy)
      allocAlignmentArrays();
    for (int fIndex = 0; fIndex < aligns.length; ++fIndex) {
      assert (fIndex >= 0 && fIndex < f.size());
      String align = aligns[fIndex];
      if (!align.startsWith("(") || !align.endsWith(")"))
        throw new IOException("Wrong alignment format: " + align);
      for (String eIndexStr : align.substring(1, align.length() - 1).split(",")) {
        if (eIndexStr.length() == 0)
          continue;
        int eIndex = Integer.parseInt(eIndexStr);
        assert (eIndex >= 0 && eIndex < e.size());
        alTable.add(alignmentToNumber((byte) eIndex, (byte) fIndex));
        if (!lazy) {
          f2e[fIndex].add(eIndex);
          e2f[eIndex].add(fIndex);
        }
      }
    }
  }

  void reset() {
    alTable.clear();
    f = null;
    e = null;
    f2e = null;
    e2f = null;
    key = -1;
    fKey = -1;
    eKey = -1;
    aKey = -1;
  }

  @SuppressWarnings("unchecked")
  void allocAlignmentArrays() {
    f2e = new TreeSet[f.size()];
    e2f = new TreeSet[e.size()];
    for (int i = 0; i < f2e.length; ++i)
      f2e[i] = new TreeSet<Integer>();
    for (int i = 0; i < e2f.length; ++i)
      e2f[i] = new TreeSet<Integer>();
  }

  void initAlignmentArrays() {
    if (align == null)
      return;
    allocAlignmentArrays();
    for (int anAlign : align) {
      int fIndex = numberToAlignmentF(anAlign);
      int eIndex = numberToAlignmentE(anAlign);
      assert (fIndex < f.size());
      assert (eIndex < e.size());
      f2e[fIndex].add(eIndex);
      e2f[eIndex].add(fIndex);
    }
    if (DEBUG) {
      System.err.println("Reconstructed alignment template: " + toString());
      System.err.println("String representation: " + Arrays.toString(align));
    }
  }

  public String toString(boolean withAlign) {
    StringBuilder buf = new StringBuilder();
    buf.append(f.toString()).append(DELIM).append(e.toString());
    if (withAlign)
      addAlignmentString(buf);
    return buf.toString();
  }

  public void addAlignmentString(StringBuilder buf) {
    buf.append(DELIM);
    if (f2e != null) {
      for (int i = 0; i < f2e.length; ++i) {
        if (i > 0)
          buf.append(" ");
        buf.append("(").append(alignmentToString(f2e[i])).append(")");
      }
    }
    buf.append(DELIM);
    if (e2f != null) {
      for (int i = 0; i < e2f.length; ++i) {
        if (i > 0)
          buf.append(" ");
        buf.append("(").append(alignmentToString(e2f[i])).append(")");
      }
    }
  }

  static String alignmentToString(Set<Integer> alSet) {
    StringBuilder buf = new StringBuilder();
    int i = -1;
    for (int al : alSet) {
      if (++i > 0)
        buf.append(",");
      buf.append(al);
    }
    return buf.toString();
  }

  static String alignmentToString(int[] align) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < align.length; ++i) {
      if (i > 0)
        buf.append(" ");
      buf.append(numberToAlignmentF(align[i]));
      buf.append("-");
      buf.append(numberToAlignmentE(align[i]));
    }
    return buf.toString();
  }

  public Sequence<IString> f() {
    return f;
  }

  public Sequence<IString> e() {
    return e;
  }

  public Set<Integer> f2e(int i) {
    return f2e[i];
  }

  public Set<Integer> e2f(int i) {
    return e2f[i];
  }

  public Set<Integer>[] f2e() {
    return f2e;
  }

  public Set<Integer>[] e2f() {
    return e2f;
  }

  public int[] getCompactAlignment() {
    return align;
  }

  static short alignmentToNumber(byte fIndex, byte eIndex) {
    return (short) ((fIndex << 8) + eIndex);
  }

  static byte numberToAlignmentE(int x) {
    return (byte) ((x >> 8) & 0xff);
  }

  static byte numberToAlignmentF(int x) {
    return (byte) (x & 0xff);
  }

  public int getKey() {
    return key;
  }

  public int getEKey() {
    return eKey;
  }

  public int getFKey() {
    return fKey;
  }

  public int getAKey() {
    return aKey;
  }

  public void setKey(int k) {
    key = k;
  }

  public void setEKey(int k) {
    eKey = k;
  }

  public void setFKey(int k) {
    fKey = k;
  }

  public void setAKey(int k) {
    aKey = k;
  }
}
