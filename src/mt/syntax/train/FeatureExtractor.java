package mt.syntax.train;

import java.util.Properties;

/**
 * Collect statistics (e.g., relative frequences estimation, Markovization) on GHKM rules.
 * 
 * @author Michel Galley
 */
public interface FeatureExtractor {

  public void init(RuleIndex ruleIndex, Properties prop);
  
  public void extractFeatures(Rule r, int ruleId, int rootId, int lhsId, int rhsId);

  public double[] score(Rule r, int ruleId, int rootId, int lhsId, int rhsId);

  //public void extractFeatures(RuleInstance r);

  //public double[] score(RuleInstance r);
}
