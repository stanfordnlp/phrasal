public interface RuleFeaturizer<TK, FV> extends Featurizer<TK,FV> {
  
  /**
   * This call is made *before* decoding with a rule.
   * Do any setup here.
   * 
   * @param featureIndex
   */
  void initialize(Index<String> featureIndex);
  
  /**
   * Extract and return features for <code>f.rule</code>.
   * 
   * @return a list of features or null.
   */
  List<FeatureValue<FV>> ruleFeaturize(Featurizable<TK, FV> f);
}
