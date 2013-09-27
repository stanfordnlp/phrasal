public interface DerivationFeaturizer<TK, FV>
  extends Featurizer<TK,FV> {
  
  /**
   * This call is made *before* decoding a new input begins.
   */
  void initialize(int sourceInputId,
                  List<ConcreteRule<TK,FV>> ruleList,
		  Sequence<TK> source,
		  Index<String> featureIndex);

  /**
   * Extract and return a list of features. If features overlap
   * in the list, their values will be added.
   * 
   * @return a list of features or null.
   */
  List<FeatureValue<FV>> featurize(Featurizable<TK, FV> f);
}
