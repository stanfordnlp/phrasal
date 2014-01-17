package edu.stanford.nlp.mt.base;

import java.util.ArrayList;
import java.util.Map;

/**
 * Maps target words to a word class.
 * 
 * @author Spence Green
 *
 */
public final class TargetClassMap extends AbstractWordClassMap {

  private static TargetClassMap instance;

  private TargetClassMap() {
    // wordToClasses = Generics.newHashMap();
    mapList = new ArrayList<Map<IString,IString>>(); // Thang Jan14
  }

  public static TargetClassMap getInstance() {
    if (instance == null) {
      instance = new TargetClassMap();
    }
    return instance;
  }
}
