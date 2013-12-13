package edu.stanford.nlp.mt.base;

import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Generics;

/**
 * Maps target words to a word class.
 * 
 * @author Spence Green
 *
 */
public final class TargetClassMap extends AbstractWordClassMap {
  
  private Map<IString,List<IString>> wordToClass;
  
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
  
  /**
   * Load the class map from file.
   * 
   * @param filename
   */
  public void load(String filename) {
    loadClassFile(wordToClass, filename);
  }
  
  /**
   * Map the input word to a word class.
   * 
   * @param word
   * @return
   */
  public List<IString> get(IString word) {
    if (TokenUtils.isNumbersWithPunctuation(word.toString())) {
      word = TokenUtils.NUMBER_TOKEN;
    } 
    if (wordToClass.containsKey(word)) {
      return wordToClass.get(word);
    } else if (wordToClass.containsKey(TokenUtils.UNK_TOKEN)) {
      return wordToClass.get(TokenUtils.UNK_TOKEN);
    } else {
      System.err.printf("%s: WARNING Class map does not specify an <unk> encoding (%s)%n", word.toString());
      return DEFAULT_UNK_MAPPING;
    }
  }
}
