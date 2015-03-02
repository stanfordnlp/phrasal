package edu.stanford.nlp.mt.util;

import java.util.HashMap;

/**
 * Maps target words to a word class.
 * 
 * @author Spence Green
 *
 */
public final class TargetClassMap extends AbstractWordClassMap {

  private static TargetClassMap instance;

  private TargetClassMap() {
    wordToClass = new HashMap<>();
  }

  public static TargetClassMap getInstance() {
    if (instance == null) {
      instance = new TargetClassMap();
    }
    return instance;
  }
}
