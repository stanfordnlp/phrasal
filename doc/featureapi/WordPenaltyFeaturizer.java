public class WordPenaltyFeaturizer implements
    RuleFeaturizer<IString, String> {

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
           Featurizable<TK, String> f) {

    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>("WordPenalty",
					    f.targetPhrase.size()));
    return features;
  }

  @Override
  public void initialize(Index<String> featureIndex) {}
}
