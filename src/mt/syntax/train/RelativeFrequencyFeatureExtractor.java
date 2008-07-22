package mt.syntax.train;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Properties;

/**
 * Relative frequences of GHKM rules, and their LHS and RHS. No smoothing.
 * 
 * @author Michel Galley
 */
public class RelativeFrequencyFeatureExtractor extends AbstractFeatureExtractor {

  static public final String RHS_NORM_COUNTS_OPT = "rhsNormCounts";
  boolean rhsNormCounts=false;

  final IntArrayList
          ruleCounts = new IntArrayList(),
          rootCounts = new IntArrayList(),
          lhsCounts = new IntArrayList(),
          rhsCounts = new IntArrayList();

  @Override
	public void init(RuleIndex ruleIndex, Properties prop) {
    super.init(ruleIndex, prop);
    rhsNormCounts = Boolean.parseBoolean(prop.getProperty(RHS_NORM_COUNTS_OPT,"false"));
  }

  @Override
	public void extractFeatures(Rule r, int ruleId, int rootId, int lhsId, int rhsId) {
    addCountToArray(ruleCounts, ruleId);
    addCountToArray(rootCounts, rootId);
    addCountToArray(lhsCounts, lhsId);
    if(rhsNormCounts)
      addCountToArray(rhsCounts, rhsId);
  }

  @Override
	public double[] score(Rule r, int ruleId, int rootId, int lhsId, int rhsId) {
    double ruleCount = ruleCounts.getInt(ruleId);
    // p(rule | root), p(rule | lhs), p(rule | rhs):
    double p_rule_root = ruleCount/rootCounts.getInt(rootId);
    double p_rule_lhs = ruleCount/lhsCounts.getInt(lhsId);
    if(rhsNormCounts) {
      double p_rule_rhs = ruleCount/rhsCounts.getInt(rhsId);
      return new double[] { p_rule_root, p_rule_lhs, p_rule_rhs };
    }
    return new double[] { p_rule_root, p_rule_lhs };
  }
}
