package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.stanford.nlp.mt.tools.NISTTokenizer;


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
   * @return the list of Sequences represented by the file
   */
  static public List<Sequence<IString>> tokenizeFile(String filename) {
    return tokenizeFile(filename, false);
  }
  
  /**
   * Convert a newline-delimited file to a list of Sequences, optionally
   * applying NIST tokenization.
   */
  static public List<Sequence<IString>> tokenizeFile(String filename, boolean doNIST) {
    List<Sequence<IString>> sequences = new ArrayList<>();
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    try {
      for (String line; (line = reader.readLine()) != null;) {
        if (doNIST) line = NISTTokenizer.tokenize(line);
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
   */
  static public Sequence<IString> tokenize(String str) {
    String[] strings = str.trim().split("\\s+");
    IString[] istrs = toIStringArray(strings);
    return new SimpleSequence<IString>(true, istrs);
  }
  
  /**
   * Convert String to IString.
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
   */
  public static Sequence<IString> toIStringSequence(List<String> seq) {
    IString[] istrs = toIStringArray(seq);
    return new SimpleSequence<IString>(true, istrs);
  }

  /**
   * Convert a collection of String to an IString array.
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
   */
  static public IString[] toIStringArray(int[] ids) {
    IString[] istrs = new IString[ids.length];
    for (int i = 0; i < istrs.length; i++) {
      istrs[i] = new IString(ids[i]);
    }
    return istrs;
  }

  /**
   * Get a sequence of IString from an array of IString indices.
   * 
   * @param indices
   * @return
   */
  public static Sequence<IString> toIStringSequence(int[] indices) {
    return toIStringSequence(indices, null);
  }
  
  /**
   * Get a sequence of IString from an array of IString indices.
   * 
   * @param indices
   * @param index
   * @return
   */
  public static Sequence<IString> toIStringSequence(int[] indices, Vocabulary index) {
    IString[] istringList = new IString[indices.length];
    for (int i = 0; i < indices.length; ++i) {
      istringList[i] = new IString(indices[i], index);
    }
    return new SimpleSequence<IString>(true, istringList);    
  }
}
