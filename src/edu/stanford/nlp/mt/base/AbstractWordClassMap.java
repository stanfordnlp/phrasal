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
  
  private static final String DELIMITER = "~";
  protected static IString DEFAULT_UNK_CLASS = new IString("<<unk>>");
  protected static List<IString> DEFAULT_UNK_MAPPING = Generics.newArrayList(1);
  static {
    DEFAULT_UNK_MAPPING.add(DEFAULT_UNK_CLASS);
  }
  
  protected Map<IString,List<IString>> wordToClass;
  protected int numMappings = 0;
  
  protected void loadClassFile(String filename) {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    try {
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length == 2) {
          IString word = new IString(fields[0]);
          IString wordClass = new IString(fields[1]);
          if ( ! wordToClass.containsKey(word)) {
            wordToClass.put(word, newMappingList());
          } 
          wordToClass.get(word).add(wordClass);
        } else {
          System.err.printf("%s: Discarding line %s%n", this.getClass().getName(), line);
        }
      }
      reader.close();
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private List<IString> newMappingList() {
    return numMappings == 0 ? new ArrayList<IString>() : 
      new ArrayList<IString>(wordToClass.get(TokenUtils.UNK_TOKEN).subList(0, numMappings));
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
    String wordStr = word.toString();
    if (TokenUtils.hasDigit(wordStr)) {
      word = new IString(TokenUtils.normalizeDigits(wordStr));
    }
    List<IString> classList = null;
    if (wordToClass.containsKey(word)) {
      classList = wordToClass.get(word);
    } else if (wordToClass.containsKey(TokenUtils.UNK_TOKEN)) {
      classList = wordToClass.get(TokenUtils.UNK_TOKEN);
    } else {
      System.err.printf("%s: WARNING Class map does not specify an <unk> encoding for unknown word (%s)%n", 
          this.getClass().getName(), word.toString());
      classList = DEFAULT_UNK_MAPPING;
    }
    return new IString(Sentence.listToString(classList, true, DELIMITER));
  }
}
