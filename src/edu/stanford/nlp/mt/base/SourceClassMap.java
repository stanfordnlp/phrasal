package edu.stanford.nlp.mt.base;

import java.util.ArrayList;
import java.util.Map;

/**
 * Maps source words to a word class.
 * 
 * @author Spence Green
 *
 */
public final class SourceClassMap extends AbstractWordClassMap {

  private static SourceClassMap instance;

  private SourceClassMap() {
    // wordToClass = Generics.newHashMap();
    mapList = new ArrayList<Map<IString,IString>>(); // Thang Jan14
  }

  public static SourceClassMap getInstance() {
    if (instance == null) {
      instance = new SourceClassMap();
    }
    return instance;
  }
}
