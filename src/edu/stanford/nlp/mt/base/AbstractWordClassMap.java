package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Map;

import edu.stanford.nlp.util.Generics;

/**
 * Converts words to word classes. Backed by a map.
 * 
 * @author Spence Green
 *
 */
public abstract class AbstractWordClassMap {

  public static final IString UNK_CLASS = new IString("##UnK##");
  
  protected AbstractWordClassMap() {}
  
  protected static Map<IString,IString> loadClassFile(String filename) {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    Map<IString,IString> map = Generics.newHashMap();
    try {
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.trim().split("\\s+");
        if (fields.length == 2) {
          map.put(new IString(fields[0]), new IString(fields[1]));
        } else {
          System.err.printf("%s: Discarding line %s%n", AbstractWordClassMap.class.getName(), line);
        }
      }
      reader.close();
      return map;
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
