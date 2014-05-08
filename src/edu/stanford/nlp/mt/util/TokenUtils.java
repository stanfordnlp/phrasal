package edu.stanford.nlp.mt.util;

import java.net.MalformedURLException;
import java.net.URL;

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
  public static final IString START_TOKEN = new IString("<s>");
  public static final IString END_TOKEN = new IString("</s>");
  public static final IString UNK_TOKEN = new IString("<unk>");
  public static final IString NULL_TOKEN = new IString("<<<null>>>");

  /**
   * Returns true if the string contains a character in the
   * range [0-9]. False otherwise.
   * 
   * @param token
   * @return
   */
  public static boolean hasDigit(String token) {
    int len = token.length();
    for (int i = 0; i < len; ++i) {
      char c = token.charAt(i);
      if (Character.isDigit(c)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Map all characters in the range [0-9] to 0 in the input
   * string.
   * 
   * @param token
   * @return
   */
  public static String normalizeDigits(String token) {
    StringBuilder sb = new StringBuilder(token.length());
    int len = token.length();
    for (int i = 0; i < len; ++i) {
      char c = token.charAt(i);
      sb.append(Character.isDigit(c) ? "0" : c);
    }
    return sb.toString();
  }

  /**
   * Returns true if the input token is a URL, and false otherwise.
   * 
   * @param token
   * @return
   */
  @SuppressWarnings("unused")
  public static boolean isURL(String token) {
    try {
      URL url = new URL(token);
      return true;
    } catch (MalformedURLException e) {
      return false;
    }
  }
  
  /**
   * Returns true if the token consists entirely of punctuation, and false
   * otherwise.
   * 
   * @param token
   * @return
   */
  public static boolean isPunctuation(String token) {
    int len = token.length();
    for (int i = 0; i < len; ++i) {
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
    int len = token.length();
    for (int i = 0; i < len; ++i) {
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
    int len = token.length();
    for (int i = 0; i < len; ++i) {
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
   * @param token
   * @return
   */
  public static boolean isNumericOrPunctuationOrSymbols(String token) {
    int len = token.length();
    for (int i = 0; i < len; ++i) {
      char c = token.charAt(i);
      if ( !(Character.isDigit(c) || Characters.isPunctuation(c) || Characters.isSymbol(c))) {
        return false;
      }
    }
    return true;
  }

  /** 
   * Returns true if all letter and number characters are ASCII
   * 
   * @param token
   * @return true/false all letter and number characters are ASCII 
   */
  public static boolean isASCII(String token) {
    int len = token.length();
    for (int i = 0; i < len; ++i) {
      char c = token.charAt(i);
      if (Character.isAlphabetic(c) && (int)c>>7 != 0) {
        return false;
      }
    }
    return true;
  }
}
