package edu.stanford.nlp.mt.syntax.ghkm;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Properties;

/**
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class AbstractFeatureExtractor implements FeatureExtractor {

  RuleIndex ruleIndex = null;
  Properties prop = null;

  public static final String DEBUG_PROPERTY = "DebugRuleFeatureExtractors";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(DEBUG_PROPERTY, "0"));

  public void init(RuleIndex ruleIndex, Properties prop) {
    this.ruleIndex = ruleIndex;
    this.prop = prop;
  }

  static void addCountToIntArray(final IntArrayList list, int idx) {
    if (idx < 0)
      return;
    synchronized (list) {
      while (idx >= list.size())
        list.add(0);
      int newCount = list.get(idx)+1;
      list.set(idx,newCount);
    }
    if (DEBUG_LEVEL >= 3)
      System.err.println("Increasing count idx="+idx+" in vector ("+list+").");
  }

  public void extractFeatures(RuleIndex.RuleId r) {}
  public void extractFeatures(RuleInstance r) {}
  public double[] score(RuleIndex.RuleId r)  { return null; }
  public double[] score(RuleInstance r) { return null; }
  public void save(String prefixName) {}
}
