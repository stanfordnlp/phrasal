package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashSet;
import java.util.Set;

import java.util.Arrays;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

public class CompareWeights {
  // TODO find a good permanent home for this method
  @SuppressWarnings("unchecked")
  static public Counter<String> readWeights(String filename)
      throws ClassNotFoundException, IOException {
    Counter<String> wts;

    if (filename.endsWith(".binwts")) {
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
          filename));
      wts = (Counter<String>) ois.readObject();
      ois.close();
    } else {
      wts = new ClassicCounter<String>();
      BufferedReader reader = new BufferedReader(new FileReader(filename));
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.split("\\s+");
        wts.setCount(fields[0], Double.valueOf(fields[1]));
      }
      reader.close();
    }
    return wts;
  }

  enum CompareType {
    MAX_COOR_ABS, COSINE, SUM_SQUARE_ERROR
  };

  static public void usage() {
    System.err
        .println("Usage:\n\tjava ...CompareWeights [-cosine|-sse] wts1 wts2\n\n\tDefault: Returns maximum absolute difference in a single weight value");
  }

  static public void main(String[] args) throws Exception {
    CompareType compareType = CompareType.MAX_COOR_ABS;

    if (args.length == 0) {
      usage();
      System.exit(-1);
    }

    if ("-cosine".equals(args[0])) {
      compareType = CompareType.COSINE;
      args = (String[]) Arrays.copyOfRange(args, 1, args.length);
    } else if ("-sse".equals(args[0])) {
      compareType = CompareType.SUM_SQUARE_ERROR;
      args = (String[]) Arrays.copyOfRange(args, 1, args.length);
    }

    if (args.length != 2) {
      usage();
      System.exit(-1);
    }

    Set<String> allWeights = new HashSet<String>();
    Counter<String> wts1 = readWeights(args[0]);
    Counter<String> wts2 = readWeights(args[1]);
    allWeights.addAll(wts1.keySet());
    allWeights.addAll(wts2.keySet());

    if (compareType == CompareType.MAX_COOR_ABS) {
      double maxDiff = Double.NEGATIVE_INFINITY;
      for (String wt : allWeights) {
        double absDiff = Math.abs(wts1.getCount(wt) - wts2.getCount(wt));
        if (absDiff > maxDiff)
          maxDiff = absDiff;
      }
      System.out.println(maxDiff);
    } else if (compareType == CompareType.COSINE) {
      double dotProd = Counters.cosine(wts1, wts2);
      System.out.println(dotProd);
    } else if (compareType == CompareType.SUM_SQUARE_ERROR) {
      double sse = 0;
      for (String wt : allWeights) {
        double diff = wts1.getCount(wt) - wts2.getCount(wt);
        sse += diff * diff;
      }
      System.out.println(sse);
    }
  }
}
