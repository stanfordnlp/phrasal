package mt.syntax.train;

import java.util.Properties;

/**
 * Collect statistics (e.g., relative frequences estimation, Markovization) on GHKM rules.
 * 
 * @author Michel Galley
 */
public interface FeatureExtractor {

  public void init(RuleIndex ruleIndex, Properties prop);
  
  /**
   * Extract features for given abstract rule (we know nothing about its context).
   * This function may do nothing, and the given FeatureExtractor may rely solely 
   * on {@link extractFeatures(RuleInstace)} to extract features.
   */
  public void extractFeatures(Rule r, int ruleId, int rootId, int lhsId, int rhsId);

  /**
   * Extract features for given rule instance (each symbol of the rule links to the 
   * AlignmentTreeNode from which it was extracted).
   * This function may do nothing, and the given FeatureExtractor may rely solely 
   * on {@link extractFeatures(Rule,int,int,int,int)} to extract features.
   */
  public void extractFeatures(RuleInstance r);

  /**
   * Score given abstact rule (we know nothing about its context). This function
   * may simply return null.
   */
  public double[] score(Rule r, int ruleId, int rootId, int lhsId, int rhsId);

  /**
   * Score given rule instance (each symbol of the rule links to the 
   * AlignmentTreeNode from which it was extracted). This function may simply 
   * return null.
   */
  public double[] score(RuleInstance r);

  public void save(String prefixName);
}
