package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.util.Pair;

/**
 * 
 * @author Sebastian Schuster
 *
 */

public class PreorderingAgreement extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString, String> {

  
  private static final String FEATURE_NAME = "POAGR";

  private static List<List<Integer>> preorderedPermutations = null;
  private List<Integer> preorderedPermutationSequence = null;
  
  
  
  public PreorderingAgreement(String... args) throws IOException {
    Properties options = FeatureUtils.argsToProperties(args);
    if (!options.containsKey("permutationsFile")) {
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR No permutations file was specified!");
    }

    String permutationsFilename = options.getProperty("permutationsFile");
    
    preorderedPermutations = parsePermutations(permutationsFilename);

   }
  
  
  public static List<List<Integer>> parsePermutations(String permutationsFilename) throws IOException {
    List<List<Integer>> permutations = new ArrayList<List<Integer>>();
    File permutationsFile = new File(permutationsFilename);
    BufferedReader reader = new BufferedReader(new FileReader(permutationsFile));
    String line = null;
    while ((line = reader.readLine()) != null) {
      permutations.add(parsePermutation(line));
    }
    reader.close();
    return permutations;
  }
  
  public static List<Integer> parsePermutation(String permutation) {
    
    //ArrayList<Pair<Integer, Integer>> wordOrder = new ArrayList<Pair<Integer, Integer>>();
    
    ArrayList<Integer> permutationSequence = new ArrayList<Integer>();
    String[] splits = permutation.split(" ");
    //int len = splits.length;
    //for (int i = 0; i < len; i++) {
      //wordOrder.add(Pair.makePair(Integer.parseInt(splits[i]), i + 1));
    //}
    
    //Collections.sort(wordOrder);
    for (String s: splits) {
      permutationSequence.add(Integer.parseInt(s) - 1);
    }
    return permutationSequence;
  }
  
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
    this.preorderedPermutationSequence = preorderedPermutations.get(sourceInputId);
  }
  
  private List<Integer> getPermutationSequence(Featurizable<IString, String> f) {
    ArrayList<Integer> permutationSequence = new ArrayList<Integer>();
    int first = f.sourcePosition;
    int last = f.sourcePosition + f.sourcePhrase.size() - 1;
    for (int i = first; i <= last; i++) {
        permutationSequence.add(i);
    }
    return permutationSequence;
  }
  

  
  //TODO: Fix this for new permutations
  private boolean isPermutationSequenceIdentical(List<Integer> prediction, List<Integer> reference) {
    int predLength = prediction.size();
    int predStart = prediction.get(0);
    List<Integer> sortedReference = new ArrayList<Integer>(reference.subList(predStart, predStart + predLength));
    Collections.sort(sortedReference);

    for (int i = 0; i < predLength; i++) {
      if (!prediction.get(i).equals(sortedReference.get(i)))
        return false;
    }
    return true;
  }
  
  //TODO: Fix this for new permutations
  private double pearsonCorrelationCoeff(List<Integer> prediction, List<Integer> reference) {
    int predLength = prediction.size();
    int refLength = reference.size();
    int predStart = prediction.get(0);
    
    if (refLength == 1) {
      return 1.0;
    }
    
    List<Integer> sortedReference = new ArrayList<Integer>(reference.subList(predStart, predStart + predLength));
    Collections.sort(sortedReference);
    double numerator = 0;
    double denominator = (Math.pow(refLength, 2) - 1) * refLength;
    
    for (int i = 0; i < predLength; i++) {
      numerator += (Math.pow(refLength, 2) - 1) - 6 * Math.pow(prediction.get(i) - sortedReference.get(i), 2);
    }
    return numerator / denominator;
  }
  
  //TODO: Walk back
  private void addDistanceCountFeatures (List<FeatureValue<String>> features, List<Integer> prediction, List<Integer> reference) {
    int predLength = prediction.size();
    int predStart = prediction.get(0);
    
    List<Integer> sortedReference = new ArrayList<Integer>(reference.subList(predStart, predStart + predLength));
    List<Integer> remainingPrediction = new ArrayList<Integer>();
    Collections.sort(sortedReference);
    for (int i = 0; i < predLength; i++) {
      boolean found = false;
      for (int j = 0; j < sortedReference.size(); j++) {
        if (prediction.get(i).equals(sortedReference.get(j))) {
          String fname = String.format("%s-DIFF.0", FEATURE_NAME);
          features.add(new FeatureValue<String>(fname, 1.0));
          sortedReference.remove(j);
          found = true;
          break;
        }
      }
      if (!found) {
        remainingPrediction.add(prediction.get(i));
      }
    }
    
    for (int i = 0; i < remainingPrediction.size(); i++) {
      int diff = Math.abs(remainingPrediction.get(i) - sortedReference.get(i));
      int bucket;
      if (diff < 2)
        bucket = 0;
      else if (diff < 4)
        bucket = 1;
      else if (diff < 6)
        bucket = 2;
      else if (diff < 9)
        bucket = 3;
      else
        bucket = 4;
      String fname = String.format("%s-DIFF.%d", FEATURE_NAME, bucket);
      features.add(new FeatureValue<String>(fname, 1.0));
    }
  }
  
  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>();
    List<Integer> permutationSequence = getPermutationSequence(f);
    double correlationCoeff = pearsonCorrelationCoeff(permutationSequence, this.preorderedPermutationSequence);
    features.add(new FeatureValue<String>(FEATURE_NAME + "-CORR", correlationCoeff));
   
    boolean permIdentical = isPermutationSequenceIdentical(permutationSequence, this.preorderedPermutationSequence);
    double featVal = permIdentical ? 1.0 / this.preorderedPermutationSequence.size() : 0.0;
    features.add(new FeatureValue<String>(FEATURE_NAME + "-IDENT", featVal));
    
    addDistanceCountFeatures(features, permutationSequence, this.preorderedPermutationSequence);
    
    return features;
  }
  
  public static void main(String args[]) throws IOException {
    PreorderingAgreement ag = new PreorderingAgreement();
    List<Integer> p1 = parsePermutation("0 1 2 3 4 5 6 7 8 9 10 11");
    List<Integer> p2 = parsePermutation("0 2 10 3 5 4 6 8 7 9 11 1");
    System.out.println("Identical? " + ag.isPermutationSequenceIdentical(p1, p2));
    double s = 0;
    for (int i = 0; i < p1.size(); i = i + 2) {
      double part = ag.pearsonCorrelationCoeff(p1.subList(i, i + 2), p2);
      s += part;
      System.out.println("Spearman Correlation: " + part);
      System.out.println("Identical?: " + ag.isPermutationSequenceIdentical(p1.subList(i, i + 2), p2));

    }
    System.out.println("Sum: " + s);

    
  }
 
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
