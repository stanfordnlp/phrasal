package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;

/**
 * Adds features to the MT system based on the types of punctuation
 * produced in the target side.
 *
 *@author John Bauer
 */
public class TargetSidePunctuationFeaturizer implements IncrementalFeaturizer<IString, String> {

  /**
   * All features will start with this prefix
   */
  public static final String FEATURE_NAME = "PUNCT";

  public static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}+");

  public TargetSidePunctuationFeaturizer() {
  }

  @Override
  public void reset() {}

  /**
   * Initialize on a new translation.  Nothing to do here, actually.
   */
  @Override
  public void initialize(List<ConcreteTranslationOption<IString, String>> options,
                         Sequence<IString> foreign, Index<String> featureIndex) {
  }

  /**
   * We care about the features produced by the list of words, so
   * listFeaturize returns results and featurize does not.
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  /**
   * Return a set of features for the tagged sentence.
   * Each feature will be of the form PUNCT-&lt;char&gt; or
   * PUNCT-char, literally the text "PUNCT-char" to indicate that a
   * punctuation was found.  Each word that is entirely punctuation
   * leads to PUNCT-char feature and a feature that is
   * PUNCT-&lt;char&gt;, where the char chosen is the first
   * punctuation character in the word as a crude form of equivalence
   * classing.
   */  
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newArrayList();

    for (IString targetWord : f.foreignSentence) {
      String word = targetWord.toString();
      if (PUNCT_PATTERN.matcher(word).matches()) {
        features.add(new FeatureValue<String>(FEATURE_NAME + "." + word.charAt(0), 1.0));
        features.add(new FeatureValue<String>(FEATURE_NAME, 1.0));
      }
    }

    return features;
  }
}
