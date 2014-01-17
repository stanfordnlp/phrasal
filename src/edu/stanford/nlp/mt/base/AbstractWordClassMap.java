package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.util.Generics;

/**
 * Converts words to word classes. Backed by a map.
 * 
 * @author Spence Green
 *
 */
public abstract class AbstractWordClassMap {

  public static final String DELIMITER = "~";
  protected static IString DEFAULT_UNK_CLASS = new IString("<<unk>>");

  //protected Map<IString,List<IString>> wordToClass;
  protected List<Map<IString,IString>> mapList;
  protected int numMappings = 0;

  // Thang Jan14: load a separate map per class file
  protected void loadClassFile(String filename) {
    Map<IString, IString> wordToClass = Generics.newHashMap();;
    
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    try {
      // Load the mapping from file
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length == 2) {
          wordToClass.put(new IString(fields[0]), new IString(fields[1]));
        } else {
          System.err.printf("%s: Discarding line %s%n", this.getClass().getName(), line);
        }
      }
      reader.close();

      // Setup the unknown word class
      if (!wordToClass.containsKey(TokenUtils.UNK_TOKEN)) {
        System.err.printf("%s: WARNING Class map does not specify an <unk> encoding: %s%n",
            this.getClass().getName(), filename);
        wordToClass.put(TokenUtils.UNK_TOKEN, DEFAULT_UNK_CLASS);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    
    mapList.add(wordToClass);
  }

  /**
   * Load the class map from file.
   * 
   * @param filename
   */
  public void load(String filename) {
    loadClassFile(filename);
    ++numMappings;
  }

  /**
   * Map the input word to a word class.
   * 
   * @param word
   * @return
   */
  public IString get(IString word) {
    List<IString> classList = getList(word); // Thang Jan14
    
    return numMappings == 1 ? classList.get(0) : new IString(Sentence.listToString(classList, true, DELIMITER));
  }
  
  // Thang Jan14
  public int getNumMappings() {
    return numMappings;
  }
  
  /**
   * Map the input word to a list of word classes.
   * @param word
   * @param mapId
   * @return
   */
  public List<IString> getList(IString word) {
    String wordStr = word.toString();
    if (TokenUtils.hasDigit(wordStr)) {
      word = new IString(TokenUtils.normalizeDigits(wordStr));
    }
    List<IString> classList = new ArrayList<IString>(); 
    
    // Thang Jan14: go through each individual map
    for (int i = 0; i < numMappings; i++) {
      Map<IString, IString> wordToClass = mapList.get(i);
      classList.add(wordToClass.containsKey(word) ? wordToClass.get(word) 
          : wordToClass.get(TokenUtils.UNK_TOKEN));
    }
    return classList;
  }
}

//protected void loadClassFile(String filename) {
//  LineNumberReader reader = IOTools.getReaderFromFile(filename);
//  try {
//    // Load the mapping from file
//    for (String line; (line = reader.readLine()) != null;) {
//      String[] fields = line.trim().split("\\s+");
//      if (fields.length == 2) {
//        IString word = new IString(fields[0]);
//        IString wordClass = new IString(fields[1]);
//        if ( ! wordToClass.containsKey(word)) {
//          wordToClass.put(word, newMappingList());
//        } 
//        wordToClass.get(word).add(wordClass);
//      } else {
//        System.err.printf("%s: Discarding line %s%n", this.getClass().getName(), line);
//      }
//    }
//    reader.close();
//
//    // Setup the unknown word class
//    if (! (wordToClass.containsKey(TokenUtils.UNK_TOKEN) && wordToClass.get(TokenUtils.UNK_TOKEN).size() == numMappings+1)) {
//      System.err.printf("%s: WARNING Class map does not specify an <unk> encoding: %s%n",
//          this.getClass().getName(), filename);
//      if ( ! wordToClass.containsKey(TokenUtils.UNK_TOKEN)) {
//        wordToClass.put(TokenUtils.UNK_TOKEN, new ArrayList<IString>());
//      }
//      wordToClass.get(TokenUtils.UNK_TOKEN).add(DEFAULT_UNK_CLASS);
//    }
//  } catch (IOException e) {
//    throw new RuntimeException(e);
//  }
//}
//
//private List<IString> newMappingList() {
//  return numMappings == 0 ? new ArrayList<IString>() : 
//    new ArrayList<IString>(wordToClass.get(TokenUtils.UNK_TOKEN).subList(0, numMappings));
//}

