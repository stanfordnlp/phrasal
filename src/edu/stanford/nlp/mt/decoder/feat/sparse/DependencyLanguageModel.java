package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.NeedsCloneable;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Generics;

public class DependencyLanguageModel  extends DerivationFeaturizer<IString, String> implements NeedsCloneable<IString, String>{

  
  private static final String FEATURE_NAME = "DEPLM";
  private static final String FEATURE_NAME_MISSING = "DEPLM_MISSING";
  
  private static TwoDimensionalCounter<String,String> dependencyProbabilities; 
  private HashMap<Integer, HashSet<Integer>> forwardDependencies;
  private HashMap<Integer, Integer> reverseDependencies;
  
  public DependencyLanguageModel(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    if (!options.containsKey("LM")) {
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR No dependency LM file was specified!");
    }
    
    if (!options.containsKey("annotations")) {
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR No dependency annotations file was specified!");
    }
    
    try {
      loadLM(options.getProperty("LM"));
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR Could not load dependency LM!");
    }
    
    CoreNLPCache.loadSerialized(options.getProperty("annotations"));
    
    
  }
  
  private void loadLM(String filename) throws IOException {
    
    dependencyProbabilities = new TwoDimensionalCounter<String,String>();
    
    File file = new File(filename);
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String line = null;
    while ((line = reader.readLine()) != null) {
      String[] parts = line.split(" ");
      double val = Double.parseDouble(parts[2]);
      //PSEUDO-SMOOTHING
      if (val > -0.1) {
        val = -0.1;
      }
      dependencyProbabilities.setCount(parts[0], parts[1], val);
    }
    reader.close();
  }
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
    SemanticGraph semanticGraph = CoreNLPCache.get(sourceInputId).get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    this.forwardDependencies = new HashMap<Integer, HashSet<Integer>>();
    this.reverseDependencies = new HashMap<Integer, Integer>() ;
    
    for (TypedDependency dep : semanticGraph.typedDependencies()) {
      int govIndex = dep.gov().index() - 1;
      int depIndex = dep.dep().index() -1;
      if (!this.forwardDependencies.containsKey(govIndex)) {
        this.forwardDependencies.put(govIndex, new HashSet<Integer>());
      } 
      this.forwardDependencies.get(govIndex).add(depIndex);
      this.reverseDependencies.put(depIndex, govIndex);
    }
  }
  
  private List<Integer> projectionAlignment(Featurizable<IString, String> f) {
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    
    int srcLength = f.sourcePhrase.size();
    int tgtLength = f.targetPhrase.size();
    
    List<Integer> s2t = Generics.newArrayList(srcLength);
    for (int i = 0; i < srcLength; ++i) {
      s2t.add(null);
    }
    
    for (int i = 0; i < tgtLength; i++) {
      int[] alignments =  alignment.t2s(i);
      if (alignments != null) {
        for (int j : alignments) {
          s2t.set(j, i);
        }
      }
    }
    return s2t;
  }
  
  

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    
    if (f.sourcePhrase.size() < 1 || f.targetPhrase.size() < 1)
      return features;
    
    List<Integer> currentAlignment = projectionAlignment(f);
    
    Sequence<IString> targetSequence = f.derivation.targetSequence;
    
    Featurizable<IString, String> prev = f.prior;
    while (prev != null) {
      
      List<Integer> alignment = projectionAlignment(f);
      
      //previous rule source indices
      for (int i = 0; i < alignment.size(); i++) {
        Integer targetIdx1 = alignment.get(i);
        if (targetIdx1 == null) continue;
        
        //current rule source indices
        for (int j = 0; j < currentAlignment.size(); j++) {
          Integer targetIdx2 = currentAlignment.get(j);
          if (targetIdx2 == null) continue;
          
          //check if any of the  words in the rule are a dependent of a previous head
          if (this.forwardDependencies.get(i).contains(j)) {
            String headWord = targetSequence.get(targetIdx1).word();
            String depWord = targetSequence.get(targetIdx2).word();
            double score = dependencyProbabilities.getCount(headWord, depWord);
            if (score < 0.0) {
              features.add(new FeatureValue<String>(FEATURE_NAME, -score));
            } else {
              features.add(new FeatureValue<String>(FEATURE_NAME_MISSING, 1.0));
            }
          }
          
          //check if any of the words in the rule is the head of a previous word
          if (this.reverseDependencies.get(i) == j) {
            String headWord = targetSequence.get(targetIdx1).word();
            String depWord = targetSequence.get(targetIdx2).word();
            
            double score = dependencyProbabilities.getCount(headWord, depWord);
            if (score < 0.0) {
              features.add(new FeatureValue<String>(FEATURE_NAME, -score));
            } else {
              features.add(new FeatureValue<String>(FEATURE_NAME_MISSING, 1.0));
            }
          }
        }
      }
      prev = f.prior;
    }
  
    return features;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

}
