package edu.stanford.nlp.mt.preordering;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.mt.tools.deplm.DependencyUtils;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.AbstractWordClassMap;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class DependencyBnBPreorderer {

  
  private static Set<String> mostFrequentTokens;
  private static LinearClassifier<String, String> classifier;
  
  private static AbstractWordClassMap classMap;
  
  /**
   * Implements the dependency tree-based preordering method
   * described by Jehl et al., EACL 2014.
   * 
   * @author Sebastian Schuster
   */
  
  
  /**
   * 
   * Recursively generates a shallow constituent tree rooted
   * at root.
   * 
   * @param tree
   * @param dependencies
   * @param root
   * @return
   */
  private static Tree generateSubTree(HashMap<Integer, Pair<IndexedWord, List<Integer>>> dependencies, int root) {
    List<Integer> children = dependencies.get(root).second;
    IndexedWord tw = dependencies.get(root).first;
    Tree tree = new LabeledScoredTreeNode(tw);
    boolean addedHead = children.isEmpty();
 
    List<Integer> sortedChildren = CollectionUtils.sorted(children);
    
    for (Integer c : sortedChildren) {
      if (c > root && ! addedHead) {
        Tree child = new LabeledScoredTreeNode(tw);
        tree.addChild(child);
        addedHead = true;
      }
      Tree child = generateSubTree(dependencies, c);
      tree.addChild(child);
    }
    
    if ( ! addedHead) {
      Tree child = new LabeledScoredTreeNode(tw);
      tree.addChild(child);
    }
    
    return tree;
    
  }
  
  private static int computeCrossingScore(Tree a, Tree b, SymmetricalWordAlignment alignment) {
    List<Tree> aChildren = a.getChildrenAsList();
    List<Tree> bChildren = b.getChildrenAsList();
    
    int score = 0;
    Set<Integer> aAlignment = new HashSet<Integer>();
    for (Tree aChild : aChildren) {
      int aIdx = ((IndexedWord) aChild.label()).index() - 1;
      aAlignment.addAll(alignment.f2e(aIdx));
    } 

    Set<Integer> bAlignment = new HashSet<Integer>();
    for (Tree bChild : bChildren) {
      int bIdx = ((IndexedWord) bChild.label()).index() - 1;
      bAlignment.addAll(alignment.f2e(bIdx));
    }   
   
    for (int i : aAlignment) {
      for (int j : bAlignment) {
        if (i > j)
          score++;
      }
    }
    
    return score;
    
  }
  

  private static List<TrainingExample> generateTrainingExamples(Tree tree, SymmetricalWordAlignment alignment) {
    List<TrainingExample> examples = Generics.newLinkedList();
    
    if (tree.isLeaf()) return examples;
    
    int childCount = tree.children().length;
    IndexedWord hw = (IndexedWord) tree.label();
    
    for (int i = 0; i < childCount; i++) {
      Tree a = tree.children()[i];
      IndexedWord iw1 = (IndexedWord) a.label();
      for (int j = i + 1; j < childCount; j++) {
       Tree b = tree.children()[j];
       IndexedWord iw2 = (IndexedWord) b.label();
       int cs1 = computeCrossingScore(a, b, alignment);
       int cs2 = computeCrossingScore(b, a, alignment);
       
       if (cs1 != cs2) {
         IndexedWord lm1 = a.isLeaf() ? null : (IndexedWord) a.children()[0].label();
         IndexedWord rm1 = a.isLeaf() ? null : (IndexedWord) a.children()[a.children().length - 1].label();
         int dst1 = hw.index() - iw1.index();
         FeatureNode n1 = new FeatureNode(iw1, hw, lm1, rm1, dst1);
         
         IndexedWord lm2 = b.isLeaf() ? null : (IndexedWord) b.children()[0].label(); 
         IndexedWord rm2 = b.isLeaf() ? null : (IndexedWord) b.children()[b.children().length - 1].label();
         int dst2 = hw.index() - iw2.index();
         FeatureNode n2 = new FeatureNode(iw2, hw, lm2, rm2, dst2);
         int label = cs1 > cs2 ? 1 : -1;
         TrainingExample ex = new TrainingExample(n1, n2, label);
         examples.add(ex);
       }
      }
     
      /* Add training examples for child nodes. */
      examples.addAll(generateTrainingExamples(a, alignment));
    }
    
    return examples;
    
  }
  
  
  private static Tree generateShallowTree(HashMap<Integer, Pair<IndexedWord, List<Integer>>> dependencies) {
    
    if (dependencies.get(0) == null || dependencies.get(0).second.isEmpty()) {
      return new LabeledScoredTreeNode();
    }
    
    return generateSubTree(dependencies, dependencies.get(0).second.get(0));
   }
  
  private static Set<String> getMostFrequentTokens(LineNumberReader reader) throws IOException {
    return getMostFrequentTokens(reader, 100);
  }
  
  private static Set<String> getMostFrequentTokens(LineNumberReader reader, int k) throws IOException {
    
    Counter<String> tokenCounts = new ClassicCounter<String>();
    
    String line;
    while ((line = reader.readLine()) != null) {
      String tokens[] = line.split("\\s+");
      for (String t : tokens) {
        tokenCounts.incrementCount(t);
      }
    }

    Set<String> mostFrequentTokens = Generics.newHashSet(k);
    Counters.retainTop(tokenCounts, k);
    mostFrequentTokens.addAll(tokenCounts.keySet());
    tokenCounts = null;
    return mostFrequentTokens;
  }
  
  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("train", 0); 
    optionArgDefs.put("dependencies", 1);
    optionArgDefs.put("sourceSentences", 1);
    optionArgDefs.put("targetSentences", 1);
    optionArgDefs.put("alignment", 1);
    optionArgDefs.put("classMap", 1);
    optionArgDefs.put("model", 1);

    return optionArgDefs;
  }
  
  
  
  public static void main(String[] args) throws IOException {
    
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    boolean train = PropertiesUtils.getBool(options, "train", false);

    String dependencyFile = PropertiesUtils.getString(options, "dependencies", null);
    if (dependencyFile == null) {
      System.err.println("Usage: java " + DependencyBnBPreorderer.class.getName() + " -dependencies path_to_conll_file -model file [-train -sourceSentences file -targetSentences file -alignment file -classMap file]");
      return;
    }
    
    System.err.println("Loading dependency trees: " +  dependencyFile);
    
    LineNumberReader dependencyReader = IOTools.getReaderFromFile(dependencyFile);
    
    HashMap<Integer, Pair<IndexedWord, List<Integer>>> dependencies = null;
   
    if (train) {
      System.err.println("Training a new model.");
      String sourceTokenFile = PropertiesUtils.getString(options, "sourceSentences", null);
      String targetTokenFile = PropertiesUtils.getString(options, "targetSentences", null);
      String alignmentFile = PropertiesUtils.getString(options, "alignment", null);
      String classMapFile = PropertiesUtils.getString(options, "classMap", null);


      if (sourceTokenFile == null || targetTokenFile == null || alignmentFile == null || classMapFile == null) {
        System.err.println("Usage: java " + DependencyBnBPreorderer.class.getName() + " -dependencies path_to_conll_file -model file [-train -sourceSentences file -targetSentences file -alignment file -classMap file]");
        return;
      }
      
      LineNumberReader sourceTokenReader = IOTools.getReaderFromFile(sourceTokenFile);
      
      /* Compute most frequent tokens and reset sourceTokenReader */
      mostFrequentTokens = getMostFrequentTokens(sourceTokenReader);
          
      sourceTokenReader.close();   
      
      classMap = new LocalWordClassMap();
      classMap.load(classMapFile);

      
      
      sourceTokenReader =  IOTools.getReaderFromFile(sourceTokenFile);
      LineNumberReader targetTokenReader =  IOTools.getReaderFromFile(targetTokenFile);
      LineNumberReader alignmentReader =  IOTools.getReaderFromFile(alignmentFile);
      
      Dataset<Integer, String> dataset = new Dataset<Integer, String>();
      
      

      Dataset<Integer, String> testDataset = new Dataset<Integer, String>();

      int i = 0;
      
      while ((dependencies = DependencyUtils.getDependenciesFromCoNLLFileReader(dependencyReader, false, true)) != null) {
        String sourceLine = sourceTokenReader.readLine();
        String targetLine = targetTokenReader.readLine();
        String alignmentLine = alignmentReader.readLine();
        
        //System.err.println(sourceLine);
        //System.err.println(targetLine);
        //System.err.println(alignmentLine);


        
        SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sourceLine, targetLine, alignmentLine);
        Tree tree = generateShallowTree(dependencies);
        //tree.pennPrint(System.err);
        
        //System.err.println(tree.yield());
        
        List<TrainingExample> trainingExamples = generateTrainingExamples(tree, alignment);
        
        
        for (TrainingExample ex : trainingExamples) {
          //System.err.println(ex.label);
          //System.err.println(ex.extractFeatures());
          if (i < 100000)
            dataset.add(ex.extractFeatures(), ex.label);
          else
            testDataset.add(ex.extractFeatures(), ex.label);
        }
        
        i++;
        
        if (i > 105000)
          break;
        
        
      }
      
      dataset.applyFeatureCountThreshold(5);

      LogisticClassifierFactory<Integer,String> lcf = new LogisticClassifierFactory<Integer,String>();
      
      LogisticClassifier<Integer, String> classifier = lcf.trainClassifier(dataset, 1.0);

      int correct = 0;
      int count = 0;
      int tp = 0;
      int tn = 0;
      int fp = 0;
      int fn = 0;
      for (Datum<Integer, String> d : dataset) {
        int label = classifier.classOf(d.asFeatures());
        if (label == d.label()) {
          if (label == 1)
            tp++;
          else
            tn++;
          correct++;
        } else {
          if (label == 1)
            fp++;
          else
            fn++;
        }
        count++;
      }
      
      
      System.out.println("Train:");
      System.out.println("Correct: " + correct + "/" + count);
      System.out.println("TP: " + tp + ", TN: " + tn + ", FP: " + fp + ", FN: " + fn);
      
      correct = 0;
      count = 0;
      tp = 0;
      tn = 0;
      fp = 0;
      fn = 0;
      
      for (Datum<Integer, String> d : testDataset) {
        int label = classifier.classOf(d.asFeatures());
        if (label == d.label()) {
          if (label == 1)
            tp++;
          else
            tn++;
          correct++;
        } else {
          if (label == 1)
            fp++;
          else
            fn++;
        }
        count++;
      }
      
      System.out.println("Test:");
      System.out.println("Correct: " + correct + "/" + count);
      System.out.println("TP: " + tp + ", TN: " + tn + ", FP: " + fp + ", FN: " + fn);

      sourceTokenReader.close();
      targetTokenReader.close();
      alignmentReader.close();
    }
    
    
    dependencyReader.close();
  }


  
  private static class TrainingExample {
    
    FeatureNode a;
    FeatureNode b;
    int label;
    
    TrainingExample(FeatureNode a, FeatureNode b,  int label) {
      this.a = a;
      this.b = b;
      this.label = label;
    }
    
    List<String> extractFeatures() {
      List<String> features = Generics.newLinkedList();
      
      List<String> leftFeatures = a.extractFeatures("l");
      List<String> rightFeatures = b.extractFeatures("r");
      
      features.addAll(leftFeatures);
      features.addAll(rightFeatures);
      for (String l : leftFeatures) {
        for (String r : rightFeatures) {
          features.add(l + "_" + r);
        }
      }
      return features;
    }
    
  }
  
  private static class FeatureNode {
    
    IndexedWord word;
    
    /* Head word. */
    IndexedWord hw;
    
    /* Left most child. */
    IndexedWord lm;
    
    /* Right most child. */
    IndexedWord rm;
    
    /* Distance between the word and the head. */
    int dst;
    
    FeatureNode(IndexedWord word, IndexedWord hw, IndexedWord lm, IndexedWord rm, int dst) {
      this.word = word;
      this.hw = hw;
      this.lm = lm;
      this.rm = rm;
      this.dst = dst;
    }
    
    List<String> extractFeatures(String prefix) {
      List<String> features = Generics.newLinkedList();
      //dependency label
      features.add(prefix + ":l:" + this.word.category());
      //POS tag
      features.add(prefix + ":t:" + this.word.tag());
      //head word
      features.add(prefix + ":hw:" + getWordOrClass(this.hw));
      //head class
      features.add(prefix + ":hc:" + getClass(this.hw));
      //left most word
      features.add(prefix + ":lmw:" + getWordOrClass(this.lm));
      //left most class
      features.add(prefix + ":lmc:" + getClass(this.lm));
      //right most word
      features.add(prefix + ":rmw:" + getWordOrClass(this.rm));
      //right most class
      features.add(prefix + ":rmc:" + getClass(this.rm));
      //distance between node and head
      features.add(prefix + ":dst:" + this.dst);
         
      
      return features;
    }
    
    String getWordOrClass(IndexedWord iw) {
      if (mostFrequentTokens.contains(iw.word())) {
        return iw.word();
      }
      return this.getClass(iw);
    }
    
    String getClass(IndexedWord iw) {
      //return iw.word();
      return classMap.get(new IString(iw.word())).word();
    }
    
  }

  
  private static class LocalWordClassMap extends AbstractWordClassMap {
    public LocalWordClassMap() {
      wordToClass = Generics.newHashMap();
    }
  }

  

}
