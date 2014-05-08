package edu.stanford.nlp.mt.decoder.feat;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.mt.pt.FlatPhraseTable;
import edu.stanford.nlp.mt.decoder.feat.base.LinearFutureCostFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TranslationModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.UnknownWordFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.WordPenaltyFeaturizer;
import edu.stanford.nlp.util.Generics;

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
    Set<String> features = Generics.newHashSet();
    features.add(NGramLanguageModelFeaturizer.DEFAULT_FEATURE_NAME);
    features.add(LinearFutureCostFeaturizer.FEATURE_NAME);
    features.add(WordPenaltyFeaturizer.FEATURE_NAME);
    features.add(UnknownWordFeaturizer.FEATURE_NAME);
    
    // Lexical reordering scores
    features.add("LexR:discontinuous2WithNext"); 
    features.add("LexR:discontinuous2WithPrevious");
    features.add("LexR:discontinuousWithNext");
    features.add("LexR:discontinuousWithPrevious");
    features.add("LexR:monotoneWithNext");
    features.add("LexR:monotoneWithPrevious");
    features.add("LexR:swapWithNext");
    features.add("LexR:swapWithPrevious");
    
    // 7 translation model scores described in Green et al. (2013).
    for (int i = 0; i < 7; ++i) {
      String fName = String.format("%s:%s.%d", TranslationModelFeaturizer.FEATURE_PREFIX,
          FlatPhraseTable.FEATURE_PREFIX, i);
      features.add(fName);
    }
    BASELINE_DENSE_FEATURES = Collections.unmodifiableSet(features);
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
