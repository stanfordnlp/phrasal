package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Computes sentence-level "IDF" for an input file.
 * 
 * @author Spence Green
 *
 */
public class ComputeBitextIDF {

  public static final String UNK_TOKEN = "$$UNK$$";
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length > 0) {
      System.err.printf("Usage: java %s < files > idf-file%n", ComputeBitextIDF.class.getName());
      System.exit(-1);
    }

    Counter<String> documentsPerTerm = new ClassicCounter<String>(1000000);
    LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in));
    double nDocuments = 0.0;
    try {
      for (String line; (line = reader.readLine()) != null;) {
        String[] tokens = line.trim().split("\\s+");
        Set<String> seen = new HashSet<String>(tokens.length);
        for (String token : tokens) {
          if ( ! seen.contains(token)) {
            seen.add(token);
            documentsPerTerm.incrementCount(token);
          }
        }
      }
      nDocuments = reader.getLineNumber();
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Output the idfs
    System.err.printf("Bitext contains %d sentences and %d word types%n", (int) nDocuments, documentsPerTerm.keySet().size());
    for (String wordType : documentsPerTerm.keySet()) {
      double count = documentsPerTerm.getCount(wordType);
      System.out.printf("%s\t%f%n", wordType, Math.log(nDocuments / count));
    }
    System.out.printf("%s\t%f%n", UNK_TOKEN, Math.log(nDocuments / 1.0));
  }
}
