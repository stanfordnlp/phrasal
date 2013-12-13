package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Generics;

/**
 * Converts words to word classes. Backed by a map.
 * 
 * @author Spence Green
 *
 */
public abstract class AbstractWordClassMap {
  
  protected static IString DEFAULT_UNK_CLASS = new IString("<<unk>>");
  protected static List<IString> DEFAULT_UNK_MAPPING = Generics.newArrayList(1);
  static {
    DEFAULT_UNK_MAPPING.add(DEFAULT_UNK_CLASS);
  }
  
  protected static void loadClassFile(Map<IString, List<IString>> wordToClass, 
      String filename) {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    try {
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length == 2) {
          IString word = new IString(fields[0]);
          IString wordClass = new IString(fields[1]);
          if (wordToClass.containsKey(word)) {
            wordToClass.put(word, new ArrayList<IString>());
          } 
          wordToClass.get(word).add(wordClass);
        } else {
          System.err.printf("%s: Discarding line %s%n", AbstractWordClassMap.class.getName(), line);
        }
      }
      reader.close();
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
