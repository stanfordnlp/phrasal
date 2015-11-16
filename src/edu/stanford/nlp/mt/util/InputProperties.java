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
        // Type conversions on InputProperty objects
        InputProperty inputProperty = InputProperty.valueOf(fields[0]);
        String value = fields[1];
        if(inputProperty == InputProperty.Domain) {
          inputProperties.put(inputProperty, value.split(LIST_DELIMITER));
        
        } else if (inputProperty == InputProperty.PrefixLengths) {
          String[] strings = value.split(LIST_DELIMITER);
          int[] values = new int[strings.length];
          
          for(int i = 0; i < values.length; ++i)
            values[i] = Integer.valueOf(strings[i]);
          
          inputProperties.put(inputProperty, values);
            
        } else if (inputProperty == InputProperty.TargetPrefix) {
          inputProperties.put(inputProperty, Boolean.valueOf(value));
          
        } else if (inputProperty == InputProperty.DistortionLimit) {
          inputProperties.put(inputProperty, Integer.valueOf(value));
        
        } else if (inputProperty == InputProperty.IsValid) {
          inputProperties.put(inputProperty, Boolean.valueOf(value));
          
        } else if (inputProperty == InputProperty.RuleFeatureIndex) {
          inputProperties.put(inputProperty, Integer.valueOf(value));
          
        } else if (inputProperty == InputProperty.WordAlignment) {
          inputProperties.put(inputProperty, Boolean.valueOf(value));
          
        } else {
          // Leave as a string
          inputProperties.put(inputProperty, value);
        }
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
