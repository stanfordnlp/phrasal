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

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.Generics;

public class DistortionProbability extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString, String> {

  private List<Label> posTags;
  
  private static TwoDimensionalCounter<String, Integer> posDistortionProbabilities = null;
  
  public DistortionProbability(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);

    if (!options.containsKey("distortionProbs")) {
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR No distortion probabilities file was specified!");
    }
        
    try {
      loadProbabilities(options.getProperty("distortionProbs"));
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR Could not load probabilities!");
    }
    if (!options.containsKey("annotations")) {
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR No POS annotations file was specified!");
    }
    
    CoreNLPCache.loadSerialized(options.getProperty("annotations"));
    
    
  }
  
  
private void loadProbabilities(String filename) throws IOException {
    
  if (posDistortionProbabilities != null) return;
  posDistortionProbabilities = new TwoDimensionalCounter<String,Integer>();
    
    File file = new File(filename);
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String line = null;
    while ((line = reader.readLine()) != null) {
      String[] parts = line.split(" ");
      int bucket = Integer.parseInt(parts[1]);
      double val = Double.parseDouble(parts[2]);
      posDistortionProbabilities.setCount(parts[0], bucket, Math.log(val));
    }
    reader.close();
  }
  
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
      Tree parseTree = CoreNLPCache.get(sourceInputId).get(TreeAnnotation.class);
      this.posTags = parseTree.preTerminalYield();
      
      
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

  private int getBucket(int dist) {
    if (Math.abs(dist) <= 2)
      return 0;
    else if (dist < -2 && dist >= -5)
      return -1;
    else if (dist < -5)
      return -2;
    else if (dist > 2 && dist <= 5)
      return 1;
    else 
      return 2;
  }
  
  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    List<Integer> permutation = getPermutationSequence(f);
    int sourceLength = f.sourcePhrase.size();
    int hypLength = permutation.size();
    
    int start = hypLength - sourceLength;
    List<Integer> sortedReference = new ArrayList<Integer>(permutation.subList(start, hypLength));
    Collections.sort(sortedReference);
    
    for (int i = start; i < hypLength; i++) {
      int dist = sortedReference.get(i - start) - i;
      int bucket = getBucket(dist);
      double val = posDistortionProbabilities.getCount(this.posTags.get(i).value(), bucket);
      features.add(new FeatureValue<String>("DIST_PROB", val));
    }
    
    return features;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

}
