package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.util.Generics;

/**
 * Maps target words to a word class.
 * 
 * @author Spence Green
 *
 */
public final class TargetClassMap extends AbstractWordClassMap {

  private static TargetClassMap instance;

  private TargetClassMap() {
    wordToClass = Generics.newHashMap();
  }

  public static TargetClassMap getInstance() {
    if (instance == null) {
      instance = new TargetClassMap();
    }
    return instance;
  }
}
