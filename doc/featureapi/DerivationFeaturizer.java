public abstract class DerivationFeaturizer<TK, FV>
  implements Featurizer<TK,FV> {

  /**
   * This call is made *before* decoding a new input begins.
   * 
   * @param sourceInputId
   * @param ruleList
   * @param source
   */
  public abstract void initialize(int sourceInputId,
      List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source);
  
  /**
   * Extract and return a list of features. If features overlap
   * in the list, their values will be added.
   * 
   * @return a list of features or null.
   */
  List<FeatureValue<FV>> featurize(Featurizable<TK, FV> f);
}
