package edu.stanford.nlp.mt.decoder.efeat;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.CacheableFeatureValue;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;

/**
 * Adds features to the MT system based on the types of punctuation
 * produced in the target side.
 *
 *@author John Bauer
 */
public class TargetSidePunctuationFeaturizer implements IsolatedPhraseFeaturizer<IString, String> {

  /**
   * All features will start with this prefix
   */
  public static final String FEATURE_NAME = "PUNCT";

  public static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}+");

  public TargetSidePunctuationFeaturizer() {
  }

  @Override
  public void initialize(Index<String> featureIndex) {
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = new LinkedList<FeatureValue<String>>();
    
    for (IString targetWord : f.translatedPhrase) {
      String word = targetWord.toString();
      if (PUNCT_PATTERN.matcher(word).matches()) {
        features.add(new CacheableFeatureValue<String>(FEATURE_NAME + "." + word.charAt(0), 1.0));
        features.add(new CacheableFeatureValue<String>(FEATURE_NAME, 1.0));
      }
    }

    return features;
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return null;
  }
}
