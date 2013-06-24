package edu.stanford.nlp.mt.decoder.efeat;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.CacheableFeatureValue;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.decoder.feat.AlignmentFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;

/**
 * Adds features to the MT system based on the types of punctuation
 * produced in the target side.
 *
 *@author John Bauer
 */
public class TargetSidePunctuationFeaturizer implements AlignmentFeaturizer,
IsolatedPhraseFeaturizer<IString, String> {

  /**
   * All features will start with this prefix
   */
  public static final String FEATURE_NAME = "TgtPunc";

  public static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}+");

  private final boolean addLexicalFeatures;
  private final boolean addDifferenceCounts;
  private final boolean addCommaFeatures;

  public TargetSidePunctuationFeaturizer() {
    addLexicalFeatures = true;
    addDifferenceCounts = false;
    addCommaFeatures = false;
  }

  public TargetSidePunctuationFeaturizer(String...args) {
    addLexicalFeatures = args.length > 0 ? Boolean.parseBoolean(args[0]) : false;
    addDifferenceCounts = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
    addCommaFeatures = args.length > 2 ? Boolean.parseBoolean(args[2]) : false;
  }

  @Override
  public void initialize(Index<String> featureIndex) {
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = new LinkedList<FeatureValue<String>>();
    PhraseAlignment alignment = f.option.abstractOption.alignment;
    int nTargetSidePunctuationChars = 0;
    int nTgtTokens = f.targetPhrase.size();
    int nInsertedCommas = 0;
    int nCommaContentAlignments = 0;

    // Iterate over the target
    for (int i = 0; i < nTgtTokens; ++i) {
      String word = f.targetPhrase.get(i).toString();
      if (PUNCT_PATTERN.matcher(word).matches()) {
        if (addLexicalFeatures) {
          features.add(new CacheableFeatureValue<String>(FEATURE_NAME + "." + word.charAt(0), 1.0));
          features.add(new CacheableFeatureValue<String>(FEATURE_NAME, 1.0));
        }
        if (addCommaFeatures && word.equals(",")) {
          int[] srcIndices = alignment.t2s(i);
          if (srcIndices == null || srcIndices.length == 0) {
            ++nInsertedCommas;
          } else {
            for (int j : srcIndices) {
              if ( ! PUNCT_PATTERN.matcher(f.sourcePhrase.get(j).toString()).matches()) {
                ++nCommaContentAlignments;
              }
            }
          }
        }
        ++nTargetSidePunctuationChars;
      }
    }

    // Features for:
    //   , --> NULL
    //   , --> content word
    if (addCommaFeatures) {
      if (nInsertedCommas > 0) {
        features.add(new CacheableFeatureValue<String>(FEATURE_NAME + ".comma.ins", nInsertedCommas));
      }
      if (nCommaContentAlignments > 0) {
        features.add(new CacheableFeatureValue<String>(FEATURE_NAME + ".comma.content", nCommaContentAlignments));
      }
    }

    // Iterate over the source
    if (addDifferenceCounts) {
      int nSourceSidePunctuationChars = 0;
      for (IString sourceWord : f.sourcePhrase) {
        String word = sourceWord.toString();
        if (PUNCT_PATTERN.matcher(word).matches()) {
          ++nSourceSidePunctuationChars;
        }
      }

      int diff = nTargetSidePunctuationChars - nSourceSidePunctuationChars;
      if (diff > 0) {
        features.add(new CacheableFeatureValue<String>(FEATURE_NAME + ".ins", diff));
      } else if (diff < 0) {
        features.add(new CacheableFeatureValue<String>(FEATURE_NAME + ".del", -1 * diff));
      }
    }

    return features;
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return null;
  }
}
