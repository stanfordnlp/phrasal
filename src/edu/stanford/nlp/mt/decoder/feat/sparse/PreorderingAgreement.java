package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

/**
 * 
 * @author Sebastian Schuster
 *
 */

public class PreorderingAgreement extends DerivationFeaturizer<IString, String> {

  
  private static final String FEATURE_NAME = "POAGR";

  private static List<List<Integer>> preorderedPermutations = null;
  private List<Integer> preorderedPermutationSequence = null;
  
  
  
  public PreorderingAgreement(String... args) throws IOException {
    
    Properties options = SparseFeatureUtils.argsToProperties(args);
    if (!options.containsKey("permutationsFile")) {
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR No permutations file was specified!");
    }
    preorderedPermutations = new ArrayList<List<Integer>>();

    String permutationsFilename = options.getProperty("permutationsFile");
    File permutationsFile = new File(permutationsFilename);
    BufferedReader reader = new BufferedReader(new FileReader(permutationsFile));
    String line = null;
    while ((line = reader.readLine()) != null) {
      preorderedPermutations.add(parsePermutation(line));
    }
    reader.close();
  }
  
  
  private List<Integer> parsePermutation(String permutation) {
    ArrayList<Integer> permutationSequence = new ArrayList<Integer>();
    String[] splits = permutation.split(" ");
    for (String s : splits) {
      permutationSequence.add(Integer.parseInt(s));
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
    Featurizable<IString, String> prev = f;
    while (prev != null) {
      int last = f.sourcePosition + f.sourcePhrase.size() - 1;
      int first = f.sourcePosition;
      for (int i = last; i >= first; i--) {
        permutationSequence.add(i);
      }
      prev = prev.prior;
    }
    Collections.reverse(permutationSequence);
    return permutationSequence;
  }

  private boolean isPermutationSequenceIdentical(List<Integer> prediction, List<Integer> reference) {
    int predLength = prediction.size();
    for (int i = 0; i < predLength; i++) {
      if (!prediction.get(i).equals(reference.get(i)))
        return false;
    }
    return true;
  }
  
  private double pearsonCorrelationCoeff(List<Integer> prediction, List<Integer> reference) {
    int predLength = prediction.size();
    double predMean = 0.0;
    double refMean = 0.0;
    for (int i = 0; i < predLength; i++) {
      predMean += prediction.get(i);
      refMean += reference.get(i);
    }
    predMean = predMean / predLength;
    refMean = refMean / predLength;
    double numerator = 0.0;
    double predDenominator = 0.0;
    double refDenominator = 0.0;
    for (int i = 0; i < predLength; i++) {
      int pred = prediction.get(i);
      int ref = reference.get(i);
      numerator += (pred - predMean) * (ref - refMean);
      predDenominator += Math.pow(pred - predMean, 2);
      refDenominator += Math.pow(ref - refMean, 2);
    }
    return numerator / Math.sqrt(refDenominator * predDenominator);
  }
  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>();
    List<Integer> permutationSequence = getPermutationSequence(f);
    boolean permIdentical = isPermutationSequenceIdentical(permutationSequence, this.preorderedPermutationSequence);
    double featVal = permIdentical ? 1.0 : 0.0;
    features.add(new FeatureValue<String>(FEATURE_NAME + "-IDENT", featVal));
    double correlationCoeff = pearsonCorrelationCoeff(permutationSequence, this.preorderedPermutationSequence);
    features.add(new FeatureValue<String>(FEATURE_NAME + "-CORR", correlationCoeff));
    return features;
  }
  
  public static void main(String args[]) throws IOException {
    PreorderingAgreement ag = new PreorderingAgreement();
    List<Integer> p1 = ag.parsePermutation("0 3 4 5 1 2 6 7 8 9 10");
    List<Integer> p2 = ag.parsePermutation("0 1 2 4 3 5 6 7 8 9 10 11 12");
    System.out.println("Identical? " + ag.isPermutationSequenceIdentical(p1, p2));
    System.out.println("Spearman Correlation: " + ag.pearsonCorrelationCoeff(p1, p2));
    
  }

}
