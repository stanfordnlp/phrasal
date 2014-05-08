package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.*;


/**
 * Utility functions for working with {@link IString}s.
 * 
 * @author danielcer
 * @author mgalley
 * @author Spence Green
 * 
 */
public final class IStrings {

  private IStrings() {}

  /**
   * Convert a newline-delimited file to a list of Sequences.
   * 
   * @param filename
   * @return the list of Sequences represented by the file
   */
  static public List<Sequence<IString>> tokenizeFile(String filename) {
    List<Sequence<IString>> sequences = new ArrayList<Sequence<IString>>();
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    try {
      for (String line; (line = reader.readLine()) != null;) {
        sequences.add(IStrings.tokenize(line));
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return sequences;
  }

  /**
   * Apply whitespace tokenization then convert to a Sequence
   * of IString objects.
   * 
   * @param str
   * @return
   */
  static public Sequence<IString> tokenize(String str) {
    String[] strings = str.trim().split("\\s+");
    IString[] istrs = toIStringArray(strings);
    return new SimpleSequence<IString>(true, istrs);
  }
  
  /**
   * Convert String to IString.
   * 
   * @param strings
   * @return
   */
  static public IString[] toIStringArray(String[] strings) {
    IString[] istrs = new IString[strings.length];
    for (int i = 0; i < istrs.length; i++) {
      istrs[i] = new IString(strings[i]);
    }
    return istrs;
  }
  
  /**
   * Convert a List of String to a Sequence.
   * 
   * @param seq
   * @return
   */
  public static Sequence<IString> toIStringSequence(List<String> seq) {
    IString[] istrs = toIStringArray(seq);
    return new SimpleSequence<IString>(true, istrs);
  }

  /**
   * Convert a collection of String to an IString array.
   * 
   * @param strings
   * @return
   */
  static public IString[] toIStringArray(Collection<String> strings) {
    IString[] istrs = new IString[strings.size()];
    int i = 0;
    for (String str : strings) {
      istrs[i++] = new IString(str);
    }
    return istrs;
  }

  /**
   * Convert an array of IString to an array of the underlying ids.
   * 
   * @param strings
   * @return
   */
  static public int[] toIntArray(IString[] strings) {
    int[] intArray = new int[strings.length];
    for (int i = 0; i < strings.length; i++) {
      intArray[i] = strings[i].id;
    }
    return intArray;
  }

  /**
   * Convert an array of ids to IString.
   * 
   * @param ids
   * @return
   */
  static public IString[] toIStringArray(int[] ids) {
    IString[] istrs = new IString[ids.length];
    for (int i = 0; i < istrs.length; i++) {
      istrs[i] = new IString(ids[i]);
    }
    return istrs;
  }

  /**
   * Convert an array of ids to an array of String.
   * 
   * @param ids
   * @return
   */
  static public String[] toStringArray(int[] ids) {
    String[] strs = new String[ids.length];
    for (int i = 0; i < strs.length; i++) {
      strs[i] = IString.getString(ids[i]);
    }
    return strs;
  }
}
