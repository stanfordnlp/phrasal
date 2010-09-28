package edu.stanford.nlp.mt.syntax.mst.rmcd;

import java.util.TreeSet;
import java.util.Set;
import java.io.*;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;

import edu.stanford.nlp.util.ArrayUtils;

public class SymmetricalWordAlignment extends AbstractWordAlignment {

  public SymmetricalWordAlignment(String[] fStr, String[] eStr, String aStr)
      throws IOException {
    init(fStr, eStr, aStr);
  }

  @SuppressWarnings("unchecked")
  public void init(String[] fStr, String[] eStr, String aStr)
      throws IOException {
    f = new SimpleSequence<IString>(true,
        IStrings.toIStringArray(preproc(fStr)));
    e = new SimpleSequence<IString>(true,
        IStrings.toIStringArray(preproc(eStr)));
    initAlignment();
    if (aStr == null) {
      System.err.println("Warning: empty line.");
      return;
    }
    // Better to keep root unaligned:
    // f2e[0].add(0);
    // e2f[0].add(0);
    Set<Integer>[] f2e = new TreeSet[f.size()];
    Set<Integer>[] e2f = new TreeSet[e.size()];
    for (int i = 0; i < f2e.length; ++i)
      f2e[i] = new TreeSet<Integer>();
    for (int i = 0; i < e2f.length; ++i)
      e2f[i] = new TreeSet<Integer>();

    for (String al : aStr.split("\\s+")) {
      String[] els = al.split("-");
      if (els.length == 2) {
        int fpos = Integer.parseInt(els[0]);
        int epos = Integer.parseInt(els[1]);
        if (0 > fpos || fpos >= f.size())
          throw new IOException("f has index out of bounds (fsize=" + f.size()
              + ",esize=" + e.size() + ") : " + fpos);
        if (0 > epos || epos >= e.size())
          throw new IOException("e has index out of bounds (esize=" + e.size()
              + ",fsize=" + f.size() + ") : " + epos);
        f2e[fpos + 1].add(epos + 1);
        e2f[epos + 1].add(fpos + 1);
      } else {
        System.err.println("Warning: bad alignment token: " + al);
      }
    }
    for (int i = 0; i < f2e.length; ++i)
      this.f2e[i] = ArrayUtils.toPrimitive(f2e[i].toArray(new Integer[f2e[i]
          .size()]));
    for (int i = 0; i < e2f.length; ++i)
      this.e2f[i] = ArrayUtils.toPrimitive(e2f[i].toArray(new Integer[e2f[i]
          .size()]));
  }

  @SuppressWarnings("unchecked")
  private void initAlignment() {
    f2e = new int[f.size()][];
    e2f = new int[e.size()][];
  }
}
