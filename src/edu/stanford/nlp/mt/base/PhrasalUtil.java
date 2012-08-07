package edu.stanford.nlp.mt.base;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * 
 * @author danielcer
 *
 */
public class PhrasalUtil {
  
  private PhrasalUtil() {}
  
  @SuppressWarnings("unchecked")
  public static Counter<String> readWeights(String filename) throws IOException, ClassNotFoundException {
    Counter<String> weights = new ClassicCounter<String>();

    if (filename.endsWith(".binwts")) {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename));
        weights = (Counter<String>) ois.readObject();
        ois.close();
      } else {

        BufferedReader reader = new BufferedReader(new FileReader(filename));
        for (String line; (line = reader.readLine()) != null;) {
          String[] fields = line.split("\\s+");
          weights.incrementCount(fields[0], Double.parseDouble(fields[1]));
        }
        reader.close();
      }
    return weights;
  }
}
