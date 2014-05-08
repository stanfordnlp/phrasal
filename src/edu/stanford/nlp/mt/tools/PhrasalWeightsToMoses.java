package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.decoder.feat.base.LinearFutureCostFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.UnknownWordFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.WordPenaltyFeaturizer;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;

/**
 * 
 * @author Spence Green
 *
 */
public class PhrasalWeightsToMoses {

  /**
   * @param args
   * @throws IOException 
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.printf("Usage: java %s file_binwts%n", PhrasalWeightsToMoses.class.getName());
      System.exit(-1);
    }
    Counter<String> weightVector = IOTools.readWeights(args[0]);
    System.out.println("[weight]");
    Map<String,Double> tmScores = Generics.newHashMap();
    for (String fname : weightVector.keySet()) {
      double value = weightVector.getCount(fname);
      if (fname.equals(UnknownWordFeaturizer.FEATURE_NAME)) {
        System.out.printf("UnknownWordPenalty0= %f%n", value);
      } else if (fname.equals(WordPenaltyFeaturizer.FEATURE_NAME)) {
        System.out.printf("WordPenalty0= %f%n", value);
      } else if (fname.equals(LinearFutureCostFeaturizer.FEATURE_NAME)) {
        System.out.printf("Distortion0= %f%n", value);
      } else if (fname.equals(NGramLanguageModelFeaturizer.DEFAULT_FEATURE_NAME)) {
        System.out.printf("LM0= %f%n", value);
      } else if (fname.startsWith("TM")){
        tmScores.put(fname, value);
      }
    }
    List<String> keys = Generics.newArrayList(tmScores.keySet());
    Collections.sort(keys);
    System.out.print("TranslationModel0=");
    for (String fname : keys) {
      System.out.printf(" %f", tmScores.get(fname));
    }
    System.out.println();
  }
}
