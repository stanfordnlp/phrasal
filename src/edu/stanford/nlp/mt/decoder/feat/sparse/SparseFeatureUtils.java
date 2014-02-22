package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Properties;

/**
 * Convenience functions for the sparse feature templates.
 * 
 * @author Spence Green
 *
 */
public final class SparseFeatureUtils {

  private static final String TRUE = String.valueOf(true);
  
  private SparseFeatureUtils() {}
  
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
        System.err.printf("%s: Discarding invalid parameter %s%n", SparseFeatureUtils.class.getName(), arg);
      }
    }
    return props;
  }  
}
