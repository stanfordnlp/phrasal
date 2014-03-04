package edu.stanford.nlp.mt.decoder.feat;

import java.util.Properties;
import java.util.Set;

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
  public static final Set<String> BASELINE_DENSE_FEATURES = Generics.newHashSet();
  static {
    BASELINE_DENSE_FEATURES.add("LM");
    BASELINE_DENSE_FEATURES.add("LexR:discontinuous2WithNext"); 
    BASELINE_DENSE_FEATURES.add("LexR:discontinuous2WithPrevious");
    BASELINE_DENSE_FEATURES.add("LexR:discontinuousWithNext");
    BASELINE_DENSE_FEATURES.add("LexR:discontinuousWithPrevious");
    BASELINE_DENSE_FEATURES.add("LexR:monotoneWithNext");
    BASELINE_DENSE_FEATURES.add("LexR:monotoneWithPrevious");
    BASELINE_DENSE_FEATURES.add("LexR:swapWithNext");
    BASELINE_DENSE_FEATURES.add("LexR:swapWithPrevious");
    BASELINE_DENSE_FEATURES.add("LinearDistortion");
    BASELINE_DENSE_FEATURES.add("TM:FPT.0");
    BASELINE_DENSE_FEATURES.add("TM:FPT.1");
    BASELINE_DENSE_FEATURES.add("TM:FPT.2");
    BASELINE_DENSE_FEATURES.add("TM:FPT.3");
    BASELINE_DENSE_FEATURES.add("TM:FPT.4");
    BASELINE_DENSE_FEATURES.add("TM:FPT.5");
    BASELINE_DENSE_FEATURES.add("TM:FPT.6");
    BASELINE_DENSE_FEATURES.add("WordPenalty");
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
