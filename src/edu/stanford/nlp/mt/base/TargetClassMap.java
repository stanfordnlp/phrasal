package edu.stanford.nlp.mt.base;

import java.util.Map;

/**
 * Maps target words to a word class.
 * 
 * @author Spence Green
 *
 */
public class TargetClassMap extends AbstractWordClassMap {
  
  private static Map<IString,IString> wordToClass;
  public static boolean MAP_NUMBERS;
  
  private static IString UNK_CLASS = new IString("##UnK##");
  
  private TargetClassMap() {}
  
  /**
   * Load the class map from file.
   * 
   * @param filename
   */
  public static void load(String filename) {
    wordToClass = loadClassFile(filename);
  }
  
  /**
   * Map the input word to a word class.
   * 
   * @param word
   * @return
   */
  public static IString get(IString word) {
    if (MAP_NUMBERS && TokenUtils.isNumbersOrPunctuation(word.toString())) {
      word = TokenUtils.NUMBER_TOKEN;
    }
    return wordToClass.containsKey(word) ? wordToClass.get(word) : UNK_CLASS;
  }
  
  public static void setUnknownClass(String className) { UNK_CLASS = new IString(className); }
  
  public static boolean isLoaded() { return wordToClass != null; }
}
