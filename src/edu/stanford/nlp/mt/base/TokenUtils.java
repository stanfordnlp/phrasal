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
  public static final IString NUMBER_TOKEN = new IString("#num#");
  public static final IString START_TOKEN = new IString("<s>");
  public static final IString END_TOKEN = new IString("</s>");
  public static final IString UNK_TOKEN = new IString("<unk>");
  public static final IString NULL_TOKEN = new IString("<<<null>>>");

  /**
   * Returns true if the token consists entirely of punctuation, and false
   * otherwise.
   * 
   * @param token
   * @return
   */
  public static boolean isPunctuation(String token) {
    for (int i = 0; i < token.length(); ++i) {
      char c = token.charAt(i);
      if ( ! Characters.isPunctuation(c)) {
        return false;
      }
    }
    return true;
  }
  
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
   * True if the input string contains at least one digit and 0 or more
   * punctuation characters. False otherwise.
   * 
   * @param token
   * @return
   */
  public static boolean isNumbersWithPunctuation(String token) {
    boolean hasDigit = false;
    for (int i = 0; i < token.length(); ++i) {
      char c = token.charAt(i);
      hasDigit = hasDigit || Character.isDigit(c);
      if ( ! (Character.isDigit(c) || Characters.isPunctuation(c))) {
        return false;
      }
    }
    return hasDigit;
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
