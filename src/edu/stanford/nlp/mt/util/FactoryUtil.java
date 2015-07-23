package edu.stanford.nlp.mt.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilities for factories.
 * 
 * @author danielcer
 * 
 */
public final class FactoryUtil {

  private static final String DELIMITER = ":";
  
  // Static class
  private FactoryUtil() {}

  /**
   * Make key/value pairs.
   * 
   * @param label
   * @param value
   * @return
   */
  public static String makePair(String label, String value) {
    return String.format("%s%s%s", label, DELIMITER, value);
  }

  /**
   * Extract key/value pairs.
   * 
   * @param specs
   * @return
   */
  public static Map<String, String> getParamPairs(String[] specs) {
    Map<String, String> paramPairs = new HashMap<>();
    for (String spec : specs) {
      String[] fields = spec.split(DELIMITER, 2);
      String key = null, value = null;
      if (fields.length == 1) {
        key = fields[0];
        value = "";
      } else if (fields.length == 2) {
        key = fields[0];
        value = fields[1];
      }
      paramPairs.put(key, value);
    }
    return paramPairs;
  }
}
