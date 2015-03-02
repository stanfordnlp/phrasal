package edu.stanford.nlp.mt.decoder.feat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.mt.tm.FlatPhraseTable;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.decoder.feat.base.LexicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.LinearFutureCostFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.PhrasePenaltyFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TranslationModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.UnknownWordFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.WordPenaltyFeaturizer;

/**
 * Convenience functions for the sparse feature templates.
 * 
 * @author Spence Green
 *
 */
public final class FeatureUtils {

  private static final String TRUE = String.valueOf(true);
  
  private FeatureUtils() {}
  
  // Baseline dense configuration from edu.stanford.nlp.mt.decoder.feat.base
  // Extended phrase table, hierarchical reordering, one language model 
  public static final Set<String> BASELINE_DENSE_FEATURES;
  static {
    Set<String> features = new HashSet<>();
    features.add(NGramLanguageModelFeaturizer.DEFAULT_FEATURE_NAME);
    features.add(LinearFutureCostFeaturizer.FEATURE_NAME);
    features.add(WordPenaltyFeaturizer.FEATURE_NAME);
    features.add(UnknownWordFeaturizer.FEATURE_NAME);
    features.add(PhrasePenaltyFeaturizer.FEATURE_NAME);
    
    // Lexical reordering scores
    features.add(LexicalReorderingFeaturizer.FEATURE_PREFIX + ":discontinuous2WithNext"); 
    features.add(LexicalReorderingFeaturizer.FEATURE_PREFIX + ":discontinuous2WithPrevious");
    features.add(LexicalReorderingFeaturizer.FEATURE_PREFIX + ":discontinuousWithNext");
    features.add(LexicalReorderingFeaturizer.FEATURE_PREFIX + ":discontinuousWithPrevious");
    features.add(LexicalReorderingFeaturizer.FEATURE_PREFIX + ":monotoneWithNext");
    features.add(LexicalReorderingFeaturizer.FEATURE_PREFIX + ":monotoneWithPrevious");
    features.add(LexicalReorderingFeaturizer.FEATURE_PREFIX + ":swapWithNext");
    features.add(LexicalReorderingFeaturizer.FEATURE_PREFIX + ":swapWithPrevious");
    
    // 6 translation model scores described in Green et al. (2013).
    for (int i = 0; i < 6; ++i) {
      String fName = String.format("%s:%s.%d", TranslationModelFeaturizer.FEATURE_PREFIX,
          FlatPhraseTable.DEFAULT_FEATURE_PREFIX, i);
      features.add(fName);
    }
    BASELINE_DENSE_FEATURES = Collections.unmodifiableSet(features);
  }
  
  /**
   * Wrap a single feature value for return from a feature template.
   * 
   * @param feature
   * @return
   */
  public static <FV> List<FeatureValue<FV>> wrapFeature(FeatureValue<FV> feature) {
    List<FeatureValue<FV>> features = new ArrayList<>(1);
    features.add(feature);
    return features;
  }
  
  /**
   * Convert a set of input arguments to a properties file.
   * 
   * @param args
   * @return
   */
  public static Properties argsToProperties(String[] args) {
    Properties props = new Properties();
    for (String arg : args) {
      String[] fields = arg.split("=");
      if (fields.length == 1) {
        props.put(fields[0], TRUE);
      } else if (fields.length == 2) {
        props.put(fields[0], fields[1]);
      } else {
        System.err.printf("%s: Discarding invalid parameter %s%n", FeatureUtils.class.getName(), arg);
      }
    }
    return props;
  }  
}
