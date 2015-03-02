package edu.stanford.nlp.mt.util;

import java.util.HashMap;

/**
 * Maps source words to a word class.
 * 
 * @author Spence Green
 *
 */
public final class SourceClassMap extends AbstractWordClassMap {

  private static SourceClassMap instance;

  private SourceClassMap() {
    wordToClass = new HashMap<>();
  }

  public static SourceClassMap getInstance() {
    if (instance == null) {
      instance = new SourceClassMap();
    }
    return instance;
  }
}
