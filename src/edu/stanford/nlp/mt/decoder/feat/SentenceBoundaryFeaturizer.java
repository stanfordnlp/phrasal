package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.util.Index;

/**
 * Assign large cost to phrase if sentence boundaries are not at the right
 * place.
 *
 * @author Michel Galley
 */
public class SentenceBoundaryFeaturizer implements
    CombinationFeaturizer<IString, String>,
    RuleFeaturizer<IString, String> {
  public static final String FEATURE_NAME = "SentenceBoundary";
  public static final double BAD_SENTENCE_BOUNDARY_PENALTY = -100.0;

  private static final IString startToken = new IString("<s>");
  private static final IString endToken = new IString("</s>");

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {

    double cost = BAD_SENTENCE_BOUNDARY_PENALTY;
    double totalCost = 0.0;

    for (int i = 0; i < f.targetPhrase.size(); ++i) {
      if (f.targetPhrase.get(i).id == startToken.id) {
        if (i == 0 && f.targetPosition == 0)
          totalCost -= cost; // reward hypothesis if starts with <s>
        else
          totalCost += cost; // bad: <s> in the middle of the sentence
      }
      if (f.targetPhrase.get(i).id == endToken.id) {
        if (i + 1 == f.targetPhrase.size() && f.done)
          totalCost -= cost; // reward hypothesis if ends with </s>
        else
          totalCost += cost; // bad: </s> in the middle of the sentence
      }
    }

    return new FeatureValue<String>(FEATURE_NAME, totalCost);
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return featurize(f);
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public void reset() {
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {
  }
}
