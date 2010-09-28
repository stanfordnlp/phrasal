package edu.stanford.nlp.mt.syntax.ghkm;

import edu.stanford.nlp.mt.base.IString;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class StringNumberer {

  Map<String, Integer> map = new WeakHashMap<String, Integer>();

  public int getId(String s) {
    Integer n = map.get(s);
    if (n != null)
      return n;
    synchronized (IString.class) {
      n = new IString(s).getId();
    }
    map.put(s, n);
    return n;
  }

}
