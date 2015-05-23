package edu.stanford.nlp.mt.util;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specify properties of the segment input. The string format is
 * key/value pairs (separated by <code>KEY_VALUE_DELIMITER</code>) separated
 * by <code>PAIR_DELIMITER</code>.
 * 
 * @author Spence Green
 *
 */
public class InputProperties extends HashMap<InputProperty, Object> {

  private static final long serialVersionUID = -7201590022690929226L;

  public static final String KEY_VALUE_DELIMITER = "=";

  public static final String PAIR_DELIMITER = " ";
  
  public static final String LIST_DELIMITER = ",";

  /**
   * Constructor.
   */
  public InputProperties() {
    super();
  }

  /**
   * Copy constructor.
   * 
   * @param inputProperties
   */
  public InputProperties(InputProperties inputProperties) {
    super(inputProperties);
  }

  /**
   * Parse a string into an <code>InputProperties</code> object.
   */
  public static InputProperties fromString(String propsString) {
    InputProperties inputProperties = new InputProperties();
    if (propsString != null && propsString.trim().length() > 0) {
      for (String keyValue : propsString.split(PAIR_DELIMITER)) {
        String[] fields = keyValue.trim().split(KEY_VALUE_DELIMITER);
        if (fields.length != 2) {
          throw new RuntimeException("File format error: " + keyValue + " " + String.valueOf(fields.length));
        }
        //we want to support a list of domains
        if(InputProperty.valueOf(fields[0]) == InputProperty.Domain)
          inputProperties.put(InputProperty.valueOf(fields[0]), fields[1].split(LIST_DELIMITER));
        else
          inputProperties.put(InputProperty.valueOf(fields[0]), fields[1]);
      }
    }
    return inputProperties;
  }

  /**
   * Parse an input file into an InputProperties file. 
   */
  public static List<InputProperties> parse(File file) {
    LineNumberReader reader = IOTools.getReaderFromFile(file);
    List<InputProperties> propertiesList = new ArrayList<>();
    try {
      for(String line; (line = reader.readLine()) != null;) {
        InputProperties inputProperties = fromString(line);
        propertiesList.add(inputProperties);
      }
      reader.close();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return propertiesList;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<InputProperty, Object> entry : entrySet()) {
      if (sb.length() > 0) sb.append(PAIR_DELIMITER);
      sb.append(entry.getKey().toString()).append(KEY_VALUE_DELIMITER).append(entry.getValue().toString());
    }
    return sb.toString();
  }
}
