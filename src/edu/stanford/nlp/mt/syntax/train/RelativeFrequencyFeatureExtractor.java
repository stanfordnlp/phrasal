package edu.stanford.nlp.mt.syntax.train;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Properties;

/**
 * Relative frequencies of GHKM rules, and their LHS and RHS. No smoothing.
 * 
 * @author Michel Galley (mgalley@cs.stanford.edu)
 */
public class RelativeFrequencyFeatureExtractor extends AbstractFeatureExtractor {

  static public final String RHS_NORM_COUNTS_OPT = "rhsNormCounts";
  boolean rhsNormCounts = false;

  final IntArrayList 
      rootCounts = new IntArrayList(), ruleCounts = new IntArrayList(),
      rhsCounts = new IntArrayList(), lhsCounts = new IntArrayList();

  @Override
	public void init(RuleIndex ruleIndex, Properties prop) {
    super.init(ruleIndex, prop);
    rhsNormCounts = Boolean.parseBoolean(prop.getProperty(RHS_NORM_COUNTS_OPT,"false"));
  }

  @Override
	public void extractFeatures(RuleIndex.RuleId rId) {
    addCountToIntArray(ruleCounts, rId.ruleId);
    addCountToIntArray(lhsCounts, rId.lhsId);
    addCountToIntArray(rootCounts, rId.rootId);
    if (rhsNormCounts)
      addCountToIntArray(rhsCounts, rId.rhsId);
  }

  @Override
	public double[] score(RuleIndex.RuleId rId) {

    double ruleCount = ruleCounts.getInt(rId.ruleId);
    // p(rule | root), p(rule | lhs), p(rule | rhs):
    double p_rule_root = ruleCount/rootCounts.getInt(rId.rootId);
    double p_rule_lhs = ruleCount/lhsCounts.getInt(rId.lhsId);
    if (rhsNormCounts) {
      double p_rule_rhs = ruleCount/rhsCounts.getInt(rId.rhsId);
      return new double[] { p_rule_root, p_rule_lhs, p_rule_rhs };
    }
    return new double[] { p_rule_root, p_rule_lhs };
  }
}
