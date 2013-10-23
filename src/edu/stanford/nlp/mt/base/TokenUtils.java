package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.util.Characters;

/**
 * Utility functions for token-level classification and transformation.
 * 
 * @author Spence Green
 *
 */
public final class TokenUtils {

  /**
   * Various special tokens.
   */
  public static final IString NUMBER_TOKEN = new IString("#NUM#");
  public static final IString START_TOKEN = new IString("<s>");
  public static final IString END_TOKEN = new IString("</s>");
  public static final IString UNK_TOKEN = new IString("<unk>");

  /**
   * True if a token consists entirely of numbers and punctuation, and false
   * otherwise.
   * 
   * @param token
   * @return
   */
  public static boolean isNumbersOrPunctuation(String token) {
    for (int i = 0; i < token.length(); ++i) {
      char c = token.charAt(i);
      if ( ! (Character.isDigit(c) || Characters.isPunctuation(c))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if a string consists entirely of numbers, punctuation, 
   * and/or symbols.
   * 
   * @param word
   * @return
   */
  public static boolean isNumericOrPunctuationOrSymbols(String word) {
    int len = word.length();
    for (int i = 0; i < len; ++i) {
      char c = word.charAt(i);
      if ( !(Character.isDigit(c) || Characters.isPunctuation(c) || Characters.isSymbol(c))) {
        return false;
      }
    }
    return true;
  }

  /** 
   * Returns true if all letter and number characters are ASCII
   * 
   * @param word
   * @return true/false all letter and number characters are ASCII 
   */
  public static boolean isASCII(String word) {
    int len = word.length();
    for (int i = 0; i < len; ++i) {
      char c = word.charAt(i);
      if (Character.isAlphabetic(c) && (int)c>>7 != 0) {
        return false;
      }
    }
    return true;
  }
}
