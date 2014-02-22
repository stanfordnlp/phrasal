package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.Sentence;

/**
 * Converts words to word classes. Backed by a map.
 * 
 * @author Spence Green
 *
 */
public abstract class AbstractWordClassMap {

  public static final String DELIMITER = "~";
  protected static IString DEFAULT_UNK_CLASS = new IString("<<unk>>");

  protected Map<IString,List<IString>> wordToClass;
  protected int numMappings = 0;

  protected void loadClassFile(String filename) {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    try {
      // Load the mapping from file
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

      // Setup the unknown word class
      if (! wordToClass.containsKey(TokenUtils.UNK_TOKEN)) {
        System.err.printf("%s: WARNING Class map does not specify an <unk> encoding: %s%n",
            this.getClass().getName(), filename);
        wordToClass.put(TokenUtils.UNK_TOKEN, new ArrayList<IString>());
        wordToClass.get(TokenUtils.UNK_TOKEN).add(DEFAULT_UNK_CLASS);
      }
      
      // Pad the word-to-class mapping since the keyset is the union of
      // all vocabularies
      for (IString word : wordToClass.keySet()) {
        if (wordToClass.get(word).size() != numMappings) {
          wordToClass.get(word).add(TokenUtils.UNK_TOKEN);
        }
      }
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private List<IString> newMappingList() {
    return numMappings == 1 ? new ArrayList<IString>() : 
      new ArrayList<IString>(wordToClass.get(TokenUtils.UNK_TOKEN).subList(0, numMappings-1));
  }

  /**
   * Load the class map from file.
   * 
   * @param filename
   */
  public void load(String filename) {
    ++numMappings;
    loadClassFile(filename);
  }
  
  /**
   * Return the number of loaded mappings.
   */
  public int getNumMappings() { return numMappings; }

  /**
   * Map the input word to a word class.
   * 
   * @param word
   * @return
   */
  public IString get(IString word) {
    List<IString> classList = getList(word);
    return numMappings == 1 ? classList.get(0) : new IString(Sentence.listToString(classList, true, DELIMITER));
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
   return wordToClass.containsKey(word) ? wordToClass.get(word) 
       : wordToClass.get(TokenUtils.UNK_TOKEN);
 }

}
