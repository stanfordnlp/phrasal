package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.util.Generics;

/**
 * Maps source words to a word class.
 * 
 * @author Spence Green
 *
 */
public final class SourceClassMap extends AbstractWordClassMap {

  private static SourceClassMap instance;

  private SourceClassMap() {
    wordToClass = Generics.newHashMap();
  }

  public static SourceClassMap getInstance() {
    if (instance == null) {
      instance = new SourceClassMap();
    }
    return instance;
  }
}
