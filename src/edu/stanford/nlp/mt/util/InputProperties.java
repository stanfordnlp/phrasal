package edu.stanford.nlp.mt.util;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Generics;

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

  private final static Map<String,String> domainToRuleIndex = Generics.newHashMap();
  
  /**
   * Set the phrase table indicator index associated with a domain.
   */
  public static void setDomainIndex(String domain, String index) {
    domainToRuleIndex.put(domain, index);
  }
  
  @Override
  public Object put(InputProperty key, Object value) {
    if (key == InputProperty.Domain) {
      String domain = (String) value;
      if (domainToRuleIndex.containsKey(domain)) {
        String ruleIndex = domainToRuleIndex.get(domain);
        super.put(InputProperty.RuleFeatureIndex, ruleIndex);
      }
    }
    return super.put(key, value);
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
    List<InputProperties> propertiesList = Generics.newArrayList();
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
