package edu.stanford.nlp.mt.base;

import java.util.*;

/**
 * 
 * @author danielcer
 * 
 */
public class FactoryUtil {

  /**
	 * 
	 */
  public static Map<String, String> getParamPairs(String[] specs) {
    Map<String, String> paramPairs = new HashMap<String, String>();

    for (int i = 1; i < specs.length; i++) {
      String[] fields = specs[i].split(":", 2);
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
