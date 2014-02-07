package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.util.Generics;

/**
 * Specify properties of the segment input.
 * 
 * @author Spence Green
 *
 */
public class InputProperties extends HashMap<InputProperty, Object> {

  private static final long serialVersionUID = -7201590022690929226L;

  public static final String KEY_VALUE_DELIMITER = "=";

  public static final String PAIR_DELIMITER = "\t";

  /**
   * Parse an input file into an InputProperties file. The input file
   * format is a line delimited set of key/value pairs which are separated
   * by a tab.
   * 
   * @param filename
   * @return
   */
  public static List<InputProperties> parse(String filename) {
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    List<InputProperties> propertiesList = Generics.newArrayList();
    final String delim = Pattern.quote(PAIR_DELIMITER);
    try {
      for(String line; (line = reader.readLine()) != null;) {
        InputProperties inputProperties = new InputProperties();
        for (String keyValue : line.trim().split(delim)) {
          String[] fields = keyValue.split(KEY_VALUE_DELIMITER);
          if (fields.length != 2) {
            throw new RuntimeException("File format error: " + keyValue);
          }
          inputProperties.put(InputProperty.valueOf(fields[0]), fields[1]);
        }
        propertiesList.add(inputProperties);
      }
      reader.close();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return propertiesList;
  }
}
