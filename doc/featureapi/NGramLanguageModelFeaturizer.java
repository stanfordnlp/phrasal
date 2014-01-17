public class NGramLanguageModelFeaturizer extends
  DerivationFeaturizer<IString, String> {

  @Override
  public List<FeatureValue<String>> featurize(
    Featurizable<IString, String> f) {

    double lmScore = getScore(startPos, limit, f.targetPrefix);

    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, lmScore));

    return features;
  }
}
