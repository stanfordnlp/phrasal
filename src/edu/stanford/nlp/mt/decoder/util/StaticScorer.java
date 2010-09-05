package edu.stanford.nlp.mt.decoder.util;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.mt.base.DenseFeatureValueCollection;
import edu.stanford.nlp.mt.base.FeatureValue;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.OAIndex;
import edu.stanford.nlp.math.ArrayMath;

/**
 * @author danielcer
 * 
 */
public class StaticScorer implements Scorer<String> {

   final public OAIndex<String> featureIndex;
   final protected double[] weights;
   final private boolean sharedFeatureIndex;

   /**
    * @throws IOException
    * @throws ClassNotFoundException 
    */
   @SuppressWarnings("unchecked")
   public StaticScorer(String filename) throws IOException, ClassNotFoundException {
      this.sharedFeatureIndex = false;
      this.featureIndex = new OAIndex<String>();
      Map<Integer, Double> wts = new HashMap<Integer, Double>();
      if (filename.endsWith(".binwts")) {
         ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
               filename));
         Counter<String> weightCounter = (Counter<String>) ois.readObject();
         ois.close();
         for (String wtKey : weightCounter.keySet()) {
            wts.put(featureIndex.indexOf(wtKey, true),
                  weightCounter.getCount(wtKey));
         }
      } else {

         BufferedReader reader = new BufferedReader(new FileReader(filename));
         for (String line; (line = reader.readLine()) != null;) {
            String[] fields = line.split("\\s+");
            wts.put(featureIndex.indexOf(fields[0], true),
                  Double.valueOf(fields[1]));
         }
         reader.close();
      }

      weights = new double[featureIndex.boundOnMaxIndex()];
      for (Map.Entry<Integer, Double> e : wts.entrySet()) {
         weights[e.getKey()] = e.getValue();
      }
   }

   /**
	 * 
	 */
   public StaticScorer(Map<String, Double> featureWts) {
      this.sharedFeatureIndex = false;
      featureIndex = new OAIndex<String>();
      for (String key : featureWts.keySet()) {
         featureIndex.indexOf(key, true);
         // System.err.printf("---inserting: '%s' index: %d\n", key,
         // featureIndex.indexOf(key));
      }

      weights = new double[featureIndex.boundOnMaxIndex()];

      for (Map.Entry<String, Double> stringDoubleEntry : featureWts.entrySet()) {
         weights[featureIndex.indexOf(stringDoubleEntry.getKey())] = stringDoubleEntry.getValue(); // .doubleValue();
      }
   }

   public StaticScorer(Counter<String> featureWts) {
      this.sharedFeatureIndex = false;
      featureIndex = new OAIndex<String>();
      for (String key : featureWts.keySet()) {
         featureIndex.indexOf(key, true);
         // System.err.printf("---inserting: '%s' index: %d\n", key,
         // featureIndex.indexOf(key));
      }

      weights = new double[featureIndex.size()];
      // weights = new double[featureIndex.boundOnMaxIndex()];

      for (String key : featureWts.keySet()) {
         weights[featureIndex.indexOf(key)] = featureWts.getCount(key);
      }
   }

   public StaticScorer(Counter<String> featureWts, OAIndex<String> featureIndex) {
      this.sharedFeatureIndex = true;
      this.featureIndex = featureIndex;
      for (String key : featureWts.keySet()) {
         featureIndex.indexOf(key, true);
         // System.err.printf("---inserting: '%s' index: %d\n", key,
         // featureIndex.indexOf(key));
      }

      weights = new double[featureIndex.size()];
      // weights = new double[featureIndex.boundOnMaxIndex()];

      for (String key : featureWts.keySet()) {
         weights[featureIndex.indexOf(key)] = featureWts.getCount(key);
      }
   }

   @Override
   public double getIncrementalScore(Collection<FeatureValue<String>> features) {
      if (sharedFeatureIndex && features instanceof DenseFeatureValueCollection)
         return getIncrementalScoreInnerProduct((DenseFeatureValueCollection<String>) features);
      return getIncrementalScoreHash(features);
   }

   private double getIncrementalScoreInnerProduct(
         DenseFeatureValueCollection<String> fva) {
      return ArrayMath.innerProduct(fva.toDoubleArray(), weights);
   }

   private double getIncrementalScoreHash(
         Collection<FeatureValue<String>> features) {
      double score = 0;

      for (FeatureValue<String> feature : features) {
         int index = featureIndex.indexOf(feature.name);
         if (index >= 0 && index < weights.length) {
            score += weights[index] * feature.value;
         }
      }

      return score;
   }

   @SuppressWarnings("unchecked,unused")
   public double getIncrementalScoreNoisy(List features) {
      double score = 0;

      for (Object o : features) {
         FeatureValue<String> feature = (FeatureValue<String>) o;
         int index = featureIndex.indexOf(feature.name);
         System.out.printf("feature: %s index: %d\n", feature.name, index);
         if (index >= 0) {
            score += weights[index] * feature.value;
            System.out.printf("\tvalue: %f contrib: %f\n", feature.value,
                  weights[index] * feature.value);
         }
      }

      return score;
   }

   @SuppressWarnings("unused")
   public static void saveWeights(String filename) {
      throw new UnsupportedOperationException();
   }

   @SuppressWarnings("unused")
   public boolean hasNonZeroWeight(String featureName) {
      int idx = featureIndex.indexOf(featureName);
      return idx >= 0 && weights[idx] == weights[idx] && weights[idx] != 0;
   }

   @SuppressWarnings("unused")
   public static boolean randomizeTag() {
      // TODO Auto-generated method stub
      return false;
   }

   @SuppressWarnings("unused")
   public void setRandomizeTag(boolean randomizeTag) {
      // TODO Auto-generated method stub
   }

   @SuppressWarnings("unused")
   public void setWeightMultipliers(double manualWeightMul,
         double classifierWeightMul) {
      // TODO Auto-generated method stub

   }

   @SuppressWarnings("unused")
   public void displayWeights() {
      // TODO Auto-generated method stub

   }

}
