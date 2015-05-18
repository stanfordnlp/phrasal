package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IOTools.SerializationMode;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Convert old weight serialization format to new format.
 * 
 * @author Spence Green
 *
 */
public class ConvertWeights {

  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.printf("Usage: java %s old_wts%n", ConvertWeights.class.getName());
      System.exit(-1);
    }
    String filename = args[0];
    Counter<String> oldWeights = IOTools.deserialize(filename, ClassicCounter.class, 
        SerializationMode.DEFAULT);
    Path oldFilename = Paths.get(filename + ".old");
    try {
      Files.move(Paths.get(filename), oldFilename);
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
    }
    IOTools.writeWeights(filename, oldWeights);
    System.out.printf("Converted %s to new format (old file moved to %s)%n",
        filename, oldFilename.toString());
  }
}
