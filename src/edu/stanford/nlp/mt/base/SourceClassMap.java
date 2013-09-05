package edu.stanford.nlp.mt.base;

import java.util.Map;

/**
 * Maps source words to a word class.
 * 
 * @author Spence Green
 *
 */
public class SourceClassMap extends AbstractWordClassMap {

  private static Map<IString,IString> wordToClass;
  
  private SourceClassMap() {}
  
  public static void load(String filename) {
    wordToClass = loadClassFile(filename);
  }
  
  public static IString get(IString word) {
    return wordToClass.containsKey(word) ? wordToClass.get(word) : UNK_CLASS;
  }
  
  public static boolean isLoaded() { return wordToClass != null; }
}
