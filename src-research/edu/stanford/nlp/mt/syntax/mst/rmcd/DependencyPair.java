package edu.stanford.nlp.mt.syntax.mst.rmcd;

import java.util.Arrays;

/**
 * @author Michel Galley
 */
public class DependencyPair {

  // TODO: add support for bilingual features

  private static final Object nil = new Object();

  final DependencyInstance inst;
  final int i, j;
  final boolean attR;

  int hash;

  public DependencyPair(DependencyInstance inst, int i, int j, boolean attR) {
    assert (i < j);
    this.inst = inst;
    this.i = i;
    this.j = j;
    this.attR = attR;
  }

  @Override
  public boolean equals(Object o) {
    assert (o instanceof DependencyPair);
    DependencyPair d1 = this, d2 = (DependencyPair) o;
    DependencyInstance i1 = d1.inst, i2 = d2.inst;

    // Attachment side is the same:
    if (d1.attR != d2.attR)
      return false;

    // Distance is the same:
    if (DependencyPipe.distBin(d1.j - d1.i) != DependencyPipe.distBin(d2.j
        - d2.i))
      return false;
    int last1 = i1.length() - 1;
    int last2 = i2.length() - 1;
    if (d1.i == 0 ^ d2.i == 0)
      return false;
    if (d1.j == last1 ^ d2.j == last2)
      return false;

    // Word pair is the same:
    if (!i1.getForm(d1.i).equals(i2.getForm(d2.i)))
      return false; // w[i]
    if (!i1.getForm(d1.j).equals(i2.getForm(d2.j)))
      return false; // w[j]

    // POS pair is the same:
    if (!i1.getPOSTag(d1.i).equals(i2.getPOSTag(d2.i)))
      return false; // t[i]
    if (!i1.getPOSTag(d1.j).equals(i2.getPOSTag(d2.j)))
      return false; // t[j]

    // Contextual tags i-1,i+1 and j-1,j+1 are the same:
    if (d1.i > 0 && !i1.getPOSTag(d1.i - 1).equals(i2.getPOSTag(d2.i - 1)))
      return false; // t[i-1]
    if (d1.j - d1.i > 1) {
      if (!i1.getPOSTag(d1.i + 1).equals(i2.getPOSTag(d2.i + 1)))
        return false; // t[i+1]
      if (!i1.getPOSTag(d1.j - 1).equals(i2.getPOSTag(d2.j - 1)))
        return false; // t[j-1]
    }
    if (d1.j < last1 && !i1.getPOSTag(d1.j + 1).equals(i2.getPOSTag(d2.j + 1)))
      return false; // t[j-1]

    // Set of tags from i+2 to j-2 (except those ignored by window size):
    if (j - i > 1) {
      String[] cpos1 = i1.inBetweenPOS(d1.i, d1.j, true), cpos2 = i2
          .inBetweenPOS(d2.i, d2.j, true);
      if (!Arrays.equals(cpos1, cpos2))
        return false;
      String[] pos1 = i1.inBetweenPOS(d1.i, d1.j, false), pos2 = i2
          .inBetweenPOS(d2.i, d2.j, false);
      if (!Arrays.equals(pos1, pos2))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    if (hash == 0) {
      Object[] vals = new Object[] { inst.getForm(i), // w[i]
          inst.getForm(j), // w[j]
          inst.getPOSTag(i), // t[i]
          inst.getPOSTag(j), // t[j]
          i > 0 ? inst.getPOSTag(i - 1) : nil, // t[i-1]
          i + 1 < j ? inst.getPOSTag(i + 1) : nil, // t[i+1]
          i + 1 < j ? inst.getPOSTag(j - 1) : nil, // t[j-1]
          j + 1 < inst.length() ? inst.getPOSTag(j + 1) : nil, // t[j+1]
          Arrays.hashCode(inst.inBetweenPOS(i, j, true)), // in-between tags
          Arrays.hashCode(inst.inBetweenPOS(i, j, false)), // in-between ctags
          DependencyPipe.distBin(j - i), // distance
          attR // side
      };
      hash = Arrays.hashCode(vals);
    }
    return hash;
  }
}
