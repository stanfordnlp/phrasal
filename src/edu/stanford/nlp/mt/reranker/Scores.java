package edu.stanford.nlp.mt.reranker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author cer (daniel.cer@colorado.edu)
 */

public class Scores {
  public static final String N_THRESH_PROP = "nThresh";
  public static final String DEFAULT_N_THRESH = null;
  Map<Integer, Map<Integer, Double>> scoreMap = new HashMap<Integer, Map<Integer, Double>>();

  public static Scores load(String filename) throws IOException {
    return load(filename, Integer.MAX_VALUE);
  }

  public static Scores load(String filename, int max) throws IOException {
    if (max == -1)
      max = Integer.MAX_VALUE;

    String n = System.getProperty(N_THRESH_PROP, DEFAULT_N_THRESH);
    int nThresh = Integer.MAX_VALUE;
    if (n != null)
      nThresh = Integer.parseInt(n);
    System.err.println("Max N = " + nThresh);
    BufferedReader breader = new BufferedReader(new FileReader(filename));
    int lineNum = 1;
    Scores scores = new Scores();
    for (String line; (line = breader.readLine()) != null; lineNum++) {
      line = line.replaceFirst("#.*$", "").replaceAll("\\s*$", "");
      if (line.equals(""))
        continue;
      String[] fields = line.split("\\s");
      if (fields.length != 2) {
        throw new RuntimeException(String.format(
            "Error on line %d: only on score maybe given per line\n"
                + "Offending line: '%s'\n", lineNum, line));
      }
      String[] sIdPair = fields[0].split(",");
      int dataPt = Integer.parseInt(sIdPair[0]);
      int hypId = Integer.parseInt(sIdPair[1]);
      if (dataPt > max)
        break;
      if (hypId >= nThresh)
        continue;
      double score = Double.parseDouble(fields[1]);
      if (!scores.scoreMap.containsKey(dataPt)) {
        scores.scoreMap.put(dataPt, new HashMap<Integer, Double>());
      }
      scores.scoreMap.get(dataPt).put(hypId, score);
    }
    return scores;
  }

  public double getScore(int dataPt, int hypId) {
    return scoreMap.get(dataPt).get(hypId);
  }

  public SortedSet<Integer> getDataPointIndices() {
    return new TreeSet<Integer>(scoreMap.keySet());
  }

  public SortedSet<Integer> getHypothesisIndices(int dataPt) {
    return new TreeSet<Integer>(scoreMap.get(dataPt).keySet());
  }
}
