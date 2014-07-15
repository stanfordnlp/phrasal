package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.util.Generics;

/**
 * 
 * @author Sebastian Schuster
 *
 */

public class PreorderingAgreement extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString, String> {

  
  private static final String FEATURE_NAME = "POAGR";

  private static List<List<Integer>> preorderedPermutations = null;
  private List<Integer> preorderedPermutationSequence = null;
  
  private boolean addCorrelationFeature = false;
  private boolean addPermutationIdentityFeature = false;
  private boolean addPermutationDistortionFeature = false;
  
  public PreorderingAgreement(String... args) throws IOException {
    Properties options = FeatureUtils.argsToProperties(args);
    if (!options.containsKey("permutationsFile")) {
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR No permutations file was specified!");
    }

    String permutationsFilename = options.getProperty("permutationsFile");
    preorderedPermutations = parsePermutations(permutationsFilename);

    
    addCorrelationFeature = options.getProperty("addCorrelationFeature") != null;
    addPermutationIdentityFeature = options.getProperty("addPermutationIdentityFeature") != null;
    addPermutationDistortionFeature = options.getProperty("addPermutationDistortionFeature") != null;
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
    ArrayList<Integer> permutationSequence = new ArrayList<Integer>();
    String[] splits = permutation.split("-");
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
  List<Integer> permutationSequence = new LinkedList<Integer>();
    Featurizable<IString, String> prior = f;
    while (prior != null) {
      int end = prior.sourcePosition + prior.sourcePhrase.size() - 1;
      int start = prior.sourcePosition;
      for (int i = end; i >= start; i--) {
        permutationSequence.add(i);
      }
      prior = prior.prior;
    }
    Collections.reverse(permutationSequence);
    return permutationSequence;
  }
  

  
  private boolean isPermutationSequenceIdentical(List<Integer> prediction, List<Integer> reference, int start) {
    List<Integer> sortedReference = new ArrayList<Integer>(reference.subList(start, prediction.size()));
    Collections.sort(sortedReference);
    for (int i = 0; i < sortedReference.size(); i++) {
      if (!prediction.get(start + i).equals(sortedReference.get(i)))
        return false;
    }
    return true;
  }
  
  private double pearsonCorrelationCoeff(List<Integer> prediction, List<Integer> reference, int start) {
    int refLength = reference.size();
    
    if (refLength == 1) {
      return 1.0;
    }
    
    List<Integer> sortedReference = new ArrayList<Integer>(reference.subList(start, prediction.size()));
    Collections.sort(sortedReference);
    double numerator = 0;
    double denominator = (Math.pow(refLength, 2) - 1) * refLength;
    
    for (int i = 0; i < sortedReference.size(); i++) {
      numerator += (Math.pow(refLength, 2) - 1) - 6 * Math.pow(prediction.get(start + i) - sortedReference.get(i), 2);
    }
    return numerator / denominator;
  }
  
  private double getDistortion(List<Integer> prediction, List<Integer> reference, int start) {
    List<Integer> sortedReference = new ArrayList<Integer>(reference.subList(start, prediction.size()));
    Collections.sort(sortedReference);
    
    double distortion = 0;
    for (int i = 0; i < sortedReference.size(); i++) {
      distortion += Math.abs(prediction.get(start + i) - sortedReference.get(i));
    }
    
    return distortion;
  }
  
  /*private void addDistanceCountFeatures (List<FeatureValue<String>> features, List<Integer> prediction, List<Integer> reference, int start) {
  
    List<Integer> sortedReference = new ArrayList<Integer>(reference.subList(start, prediction.size()));
    List<Integer> remainingPrediction = new ArrayList<Integer>();
    Collections.sort(sortedReference);
    int len = sortedReference.size();
    for (int i = 0; i < len; i++) {
      boolean found = false;
      for (int j = 0; j < sortedReference.size(); j++) {
        if (prediction.get(start + i).equals(sortedReference.get(j))) {
          String fname = String.format("%s-DIFF.0", FEATURE_NAME);
          features.add(new FeatureValue<String>(fname, 1.0));
          sortedReference.remove(j);
          found = true;
          break;
        }
      }
      if (!found) {
        remainingPrediction.add(prediction.get(start + i));
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
  */
  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    if (f  == null || f.sourcePhrase == null)
      return features;

    List<Integer> permutationSequence = getPermutationSequence(f);
    //System.err.println("----------------------------------------");
    //System.err.println("Reference Permutation: " + StringUtils.join(this.preorderedPermutationSequence));
    //System.err.println("Predicted Permutation: " + StringUtils.join(permutationSequence));
    //System.err.println("SourcePhraseSize: " + f.sourcePhrase.size());

    int start = permutationSequence.size() - f.sourcePhrase.size();
    if (addCorrelationFeature) {
      double correlationCoeff = pearsonCorrelationCoeff(permutationSequence, this.preorderedPermutationSequence, start);
      features.add(new FeatureValue<String>(FEATURE_NAME + "-CORR", correlationCoeff));
    }
   
    if (addPermutationIdentityFeature) {
      boolean permIdentical = isPermutationSequenceIdentical(permutationSequence, this.preorderedPermutationSequence, start);
      double featVal = permIdentical ? 1.0 / this.preorderedPermutationSequence.size() : 0.0;
      features.add(new FeatureValue<String>(FEATURE_NAME + "-IDENT", featVal));
    }
    
    if (addPermutationDistortionFeature) {
      features.add(new FeatureValue<String>(FEATURE_NAME + "-DISTORTION", getDistortion(permutationSequence, preorderedPermutationSequence, start)));
    }
    //System.err.println("Permutation identical?: " + featVal);

    //System.err.println("----------------------------------------");

    //addDistanceCountFeatures(features, permutationSequence, this.preorderedPermutationSequence, start);
    
    return features;
  }
  
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
