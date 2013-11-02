package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;

import edu.stanford.nlp.mt.lm.LanguageModel;

/**
 * 
 * @author danielcer
 * 
 */
public class Featurizers {
  private Featurizers() {
  }

  /**
   * 
   * @param <TK>
   * @param <FV>
   */
  @SuppressWarnings("unchecked")
  static public <TK, FV> List<LanguageModel<TK>> extractNGramLanguageModels(
      List<Featurizer<TK, FV>> featurizers) {
    int highestOrder = 0;
    List<LanguageModel<TK>> lgModels = new ArrayList<LanguageModel<TK>>(
        featurizers.size());

    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (!(featurizer instanceof NGramLanguageModelFeaturizer))
        continue;
      NGramLanguageModelFeaturizer lmFeaturizer = (NGramLanguageModelFeaturizer) featurizer;
      lgModels.add((LanguageModel<TK>)lmFeaturizer.lm);
      int order = lmFeaturizer.order();
      if (order > highestOrder) {
        highestOrder = order;
      }
    }

    return lgModels;
  }
}
