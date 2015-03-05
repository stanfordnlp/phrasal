package edu.stanford.nlp.mt.preordering;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
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
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class DependencyBnBPreorderer {

  
  private static Set<String> mostFrequentTokens;

  private static LogisticClassifier<Integer, String> classifier;
  
  private static AbstractWordClassMap classMap;
  
  private static final double  REG_STRENGTH = 1.5;
  
  
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
  
  private static int computeCrossingLinks(List<Label> sourceWords, SymmetricalWordAlignment alignment) {
    
    int sourceLen = sourceWords.size();
    
    int score = 0;
    
    for (int i = 0; i < sourceLen; i++) {
      IndexedWord iw1 = (IndexedWord) sourceWords.get(i);
      Set<Integer> aAlignment = new HashSet<Integer>();
      aAlignment.addAll(alignment.f2e(iw1.index() - 1));
      for (int j = i + 1; j < sourceLen; j++) {
        IndexedWord iw2 = (IndexedWord) sourceWords.get(j);
        Set<Integer> bAlignment = new HashSet<Integer>();
        bAlignment.addAll(alignment.f2e(iw2.index() - 1));
        for (int k : aAlignment) {
          for (int l : bAlignment) {
            if (k > l)
              score++;
          }
        }
      }
    }
    
    return score;
    
  }
  
  
  private static int computeCrossingScore(Tree a, Tree b, SymmetricalWordAlignment alignment) {
    List<Tree> aChildren = a.getChildrenAsList();
    List<Tree> bChildren = b.getChildrenAsList();
    
    int score = 0;
    Set<Integer> aAlignment = new HashSet<Integer>();
    int aIdx = ((IndexedWord) a.label()).index() - 1;
    aAlignment.addAll(alignment.f2e(aIdx));
    for (Tree aChild : aChildren) {
      aIdx = ((IndexedWord) aChild.label()).index() - 1;
      aAlignment.addAll(alignment.f2e(aIdx));
    } 

    Set<Integer> bAlignment = new HashSet<Integer>();
    int bIdx = ((IndexedWord) b.label()).index() - 1;
    bAlignment.addAll(alignment.f2e(bIdx));
    for (Tree bChild : bChildren) {
      bIdx = ((IndexedWord) bChild.label()).index() - 1;
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
  
  private static String preorder(Tree tree) {
    
    List<Tree> queue = new LinkedList<>();
    queue.add(tree);
    
    
    while ( ! queue.isEmpty()) {
      Tree currentNode = queue.remove(0);
      
      if (currentNode.isLeaf())
        continue;
      
      Tree children[] = currentNode.children();
      int childCount = children.length;
      IndexedWord hw = (IndexedWord) currentNode.label();
      List<FeatureNode> featureNodes = new ArrayList<>(childCount);
      for (int i = 0; i < childCount; i++) {
        featureNodes.add(new FeatureNode(children[i], hw));
        queue.add(children[i]);
      }
      if (childCount < 8) {
        Pair<Double, List<Integer>> result = search(featureNodes, new LinkedList<Integer>(), Double.NEGATIVE_INFINITY);
        if (result != null) {
          List<Integer> permutation = result.second;
          List<Tree> newChildren = new ArrayList<>(Arrays.asList(children));
          for (int i = 0; i < childCount; i++) {
            int idx = permutation.get(i);
            newChildren.set(idx, children[i]);
          }
          currentNode.setChildren(newChildren);
        } else {
          System.err.println("Warning: No path found.");
        }
      }
    }
    
    return StringUtils.join(tree.yieldWords());
  }
  

  private static List<TrainingExample> generateTrainingExamples(Tree tree, SymmetricalWordAlignment alignment) {
    List<TrainingExample> examples = new LinkedList<>();
    
    if (tree.isLeaf()) return examples;
    
    int childCount = tree.children().length;
    IndexedWord hw = (IndexedWord) tree.label();
    
    for (int i = 0; i < childCount; i++) {
      Tree a = tree.children()[i];
      for (int j = i + 1; j < childCount; j++) {
       if (i == j) continue;
       Tree b = tree.children()[j];
       int cs1 = computeCrossingScore(a, b, alignment);
       int cs2 = computeCrossingScore(b, a, alignment);
       
       if (cs1 != cs2) {
         FeatureNode n1 = new FeatureNode(a, hw);
         FeatureNode n2 = new FeatureNode(b, hw);
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

    Set<String> mostFrequentTokens = new HashSet<>(k);
    Counters.retainTop(tokenCounts, k);
    mostFrequentTokens.addAll(tokenCounts.keySet());
    tokenCounts = null;
    return mostFrequentTokens;
  }
  
  private static Pair<Double, List<Integer>> search(List<FeatureNode> nodes, List<Integer> partialPermutation, double bound) {
    
    double score = scorePermutation(nodes, partialPermutation);
    List<Integer> bestPath = null;
    if (score > bound) {
      if (nodes.size() == partialPermutation.size()) {
        bound = score;
        bestPath = partialPermutation;
        return new Pair<Double, List<Integer>>(score, bestPath);
      } else {
        int size = nodes.size();
        Set<Integer> fixedPositions = new HashSet<>(partialPermutation);
        Pair<Double, List<Integer>> retValue = null;
        for (int i = 0; i < size; i++) {
          if ( ! fixedPositions.contains(i)) {
            List<Integer> perm = new LinkedList<>(partialPermutation);
            perm.add(i);
            Pair<Double, List<Integer>> result = search(nodes, perm, bound);
            if (result != null) {
              bound = result.first;
              bestPath = result.second;
              retValue = result;
            }
          }
        }
        if (retValue != null)
          return retValue;
      }
    }
    return null;
  }
  
  
  private static double scorePermutation(List<FeatureNode> nodes, List<Integer> permutation) {
    
    int size = permutation.size();
    
    assert size <= nodes.size();
    
    double score = 0.0;
    for (int i = 0; i < size; i++) {
      FeatureNode fn1 = nodes.get(permutation.get(i));
      for (int j = i + 1; j < size; j++) {
       FeatureNode fn2 = nodes.get(permutation.get(j));
       
       TrainingExample example = new TrainingExample(fn1, fn2, 0);
       List<String> features = example.extractFeatures();
       double p = classifier.probabilityOf(features, 1);
       double p_inv = 1 - p;
       if (permutation.get(i) > permutation.get(j)) {
         score += Math.log(p);
       } else {
         score += Math.log(p_inv);
       }
      }
    }
    return score;
  }
  
  
  
  private static double scorePartialPermuation(List<FeatureNode> nodes, List<Integer> partialPermutation, int i) {
    Set<Integer> fixedPositions = new HashSet<>(partialPermutation);
    
    int childCount = nodes.size();
    
    FeatureNode fn1 = nodes.get(i);
    
    double score = 0.0;

    for (int j = 0; j < childCount; j++) {
      if (i == j || fixedPositions.contains(j))
        continue;
      FeatureNode fn2 = nodes.get(j);
      TrainingExample example = new TrainingExample(fn1, fn2, 0);
      List<String> features = example.extractFeatures();
      double p = classifier.probabilityOf(features, 1);
      double p_inv = 1 - p;

      if (i > j) {
        score += Math.log(p);
      } else {
        score += Math.log(p_inv);
      }
    }
    
    return score;
  }
  
  private static void saveModel(String path) throws IOException {
    
    Model model = new Model(classifier, mostFrequentTokens);
    
    FileOutputStream fos = new FileOutputStream(path);
    ObjectOutputStream oos = new ObjectOutputStream(fos);
    oos.writeObject(model);
    oos.close();
    fos.close();
  }
  
  
  private static void loadModel(String path) throws IOException, ClassNotFoundException {
    FileInputStream fis = new FileInputStream(path);
    ObjectInputStream ois = new ObjectInputStream(fis);
    Model model = (Model) ois.readObject();
    classifier = model.classifier;
    mostFrequentTokens = model.mostFrequentTokens;
    ois.close();
    fis.close();
  }
  
  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = new HashMap<>();
    optionArgDefs.put("train", 0); 
    optionArgDefs.put("dependencies", 1);
    optionArgDefs.put("sourceSentences", 1);
    optionArgDefs.put("targetSentences", 1);
    optionArgDefs.put("alignment", 1);
    optionArgDefs.put("classMap", 1);
    optionArgDefs.put("model", 1);
    optionArgDefs.put("devSourceSentences", 1);
    optionArgDefs.put("devTargetSentences", 1);
    optionArgDefs.put("devAlignment", 1);
    optionArgDefs.put("devDependencies", 1);

    return optionArgDefs;
  }
  

  
  public static void main(String[] args) throws IOException, ClassNotFoundException {
    
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    boolean train = PropertiesUtils.getBool(options, "train", false);

    String dependencyFile = PropertiesUtils.getString(options, "dependencies", null);
    String modelFile = PropertiesUtils.getString(options, "model", null);
    String classMapFile = PropertiesUtils.getString(options, "classMap", null);

    
    if (dependencyFile == null || modelFile == null || classMapFile == null) {
      System.err.println("Usage: java " + DependencyBnBPreorderer.class.getName() + " -dependencies path_to_conll_file -model file  -classMap file [-train -sourceSentences file -targetSentences file -alignment file -devSourceSentences file -devTargetSentences file -devAlignment file -devDependencies file]");
      return;
    }
    
    System.err.println("Loading dependency trees: " +  dependencyFile);
    
    LineNumberReader dependencyReader = IOTools.getReaderFromFile(dependencyFile);
    
    HashMap<Integer, Pair<IndexedWord, List<Integer>>> dependencies = null;
   
    
    classMap = new LocalWordClassMap();
    classMap.load(classMapFile);

    
    if (train) {
      System.err.println("Training a new model.");
      String sourceTokenFile = PropertiesUtils.getString(options, "sourceSentences", null);
      String targetTokenFile = PropertiesUtils.getString(options, "targetSentences", null);
      String alignmentFile = PropertiesUtils.getString(options, "alignment", null);

      String devSourceTokenFile = PropertiesUtils.getString(options, "devSourceSentences", null);
      String devTargetTokenFile = PropertiesUtils.getString(options, "devTargetSentences", null);
      String devAlignmentFile = PropertiesUtils.getString(options, "devAlignment", null);
      String devDependencyFile = PropertiesUtils.getString(options, "devDependencies", null);
      
      if (sourceTokenFile == null || targetTokenFile == null || alignmentFile == null || classMapFile == null) {
        System.err.println("Usage: java " + DependencyBnBPreorderer.class.getName() + " -dependencies path_to_conll_file -model file [-train -sourceSentences file -targetSentences file -alignment file -classMap file -devSourceSentences file -devTargetSentences file -devAlignment file -devDependencies file]");
        return;
      }
      
      LineNumberReader sourceTokenReader = IOTools.getReaderFromFile(sourceTokenFile);
      
      /* Compute most frequent tokens and reset sourceTokenReader */
      mostFrequentTokens = getMostFrequentTokens(sourceTokenReader);
          
      sourceTokenReader.close();   
      

      
      
      sourceTokenReader =  IOTools.getReaderFromFile(sourceTokenFile);
      LineNumberReader targetTokenReader =  IOTools.getReaderFromFile(targetTokenFile);
      LineNumberReader alignmentReader =  IOTools.getReaderFromFile(alignmentFile);
      
      LineNumberReader devSourceTokenReader = IOTools.getReaderFromFile(devSourceTokenFile);
      LineNumberReader devTargetTokenReader = IOTools.getReaderFromFile(devTargetTokenFile);
      LineNumberReader devAlignmentReader = IOTools.getReaderFromFile(devAlignmentFile);
      LineNumberReader devDependencyReader = IOTools.getReaderFromFile(devDependencyFile);
      
      Dataset<Integer, String> dataset = new Dataset<Integer, String>();
      
      

      Dataset<Integer, String> testDataset = new Dataset<Integer, String>();

      List<Tree> treesToReorder = new ArrayList<>();
      List<SymmetricalWordAlignment> alignmentsToReorder = new ArrayList<>();

      int i = 0;
      while ((dependencies = DependencyUtils.getDependenciesFromCoNLLFileReader(dependencyReader, false, false)) != null) {
        Tree tree = generateShallowTree(dependencies);

        if (tree.yield().size() > 100)
          continue;
        
        String sourceLine = sourceTokenReader.readLine();
        String targetLine = targetTokenReader.readLine();
        String alignmentLine = alignmentReader.readLine();
        
//        System.err.println(sourceLine);
//        System.err.println(targetLine);
//        System.err.println(alignmentLine);


        
        //tree.pennPrint(System.err);
        //System.err.println(tree.yield());
        
        SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sourceLine, targetLine, alignmentLine);

//        System.err.println(sourceLine.split("\\s+").length);
//        System.err.println(targetLine.split("\\s+").length);
//        System.err.println(alignment.toString());
//        System.err.println(alignment.fSize());
//        System.err.println(alignment.eSize());
        

        
        //System.err.println(tree.yield());
        
        List<TrainingExample> trainingExamples = generateTrainingExamples(tree, alignment);
        
        
        for (TrainingExample ex : trainingExamples) {
          //System.err.println(ex.label);
          //System.err.println(ex.extractFeatures());
            dataset.add(ex.extractFeatures(), ex.label);
        }
        i++;
        if (i % 100 == 0) 
          System.err.println(i);
      }
      
      
      dataset.applyFeatureCountThreshold(5);
      
      while ((dependencies = DependencyUtils.getDependenciesFromCoNLLFileReader(devDependencyReader, false, false)) != null) {
        String sourceLine = devSourceTokenReader.readLine();
        String targetLine = devTargetTokenReader.readLine();
        String alignmentLine = devAlignmentReader.readLine();
        SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sourceLine, targetLine, alignmentLine);
        Tree tree = generateShallowTree(dependencies);
        //tree.pennPrint(System.err);
        
        //System.err.println(tree.yield());
        
        List<TrainingExample> trainingExamples = generateTrainingExamples(tree, alignment);
        
        for (TrainingExample ex : trainingExamples) {
          //System.err.println(ex.label);
          //System.err.println(ex.extractFeatures());
            testDataset.add(ex.extractFeatures(), ex.label);
        }
        treesToReorder.add(tree);
        alignmentsToReorder.add(alignment);
      }  
      
      
      LogisticClassifierFactory<Integer,String> lcf = new LogisticClassifierFactory<Integer,String>();
      
      classifier = lcf.trainClassifier(dataset, REG_STRENGTH);

      
      saveModel(modelFile);
      
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
      System.out.println("Accurracy\tTP\tTN\tFP\tFN");
      System.out.printf("%.4f", (correct * 100.0 / count ));
      System.out.print("\t");
      System.out.print(tp);
      System.out.print("\t");
      System.out.print(tn);
      System.out.print("\t");
      System.out.print(fp);
      System.out.print("\t");
      System.out.println(fn);
      
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
      System.out.println("Accurracy\tTP\tTN\tFP\tFN");
      System.out.printf("%.4f", (correct * 100.0 / count ));
      System.out.print("\t");
      System.out.print(tp);
      System.out.print("\t");
      System.out.print(tn);
      System.out.print("\t");
      System.out.print(fp);
      System.out.print("\t");
      System.out.println(fn);

      
      int totalOriginalCrossingScore = 0;
      int totalPreorderedCrossingScore = 0;
      
      for (int j = 0; j < treesToReorder.size(); j++) {
        Tree tree = treesToReorder.get(j);
        SymmetricalWordAlignment alignment = alignmentsToReorder.get(j);
        //System.out.println("---------------");
        //System.out.println("Original: " + tree.yieldWords());
        int OCS = computeCrossingLinks(tree.yield(), alignment);
        //System.out.println("Reordered: " + preorder(tree));
        preorder(tree);
        int PCS = computeCrossingLinks(tree.yield(), alignment);
        //System.out.println("Crossing score, before: " + OCS + ", after: " + PCS);
        totalOriginalCrossingScore += OCS;
        totalPreorderedCrossingScore += PCS;
      }
      
      System.out.println("##################");
      System.out.println("Crossing score reduction: " + totalPreorderedCrossingScore + "/" + totalOriginalCrossingScore);
      
      sourceTokenReader.close();
      targetTokenReader.close();
      alignmentReader.close();
    } else {
      // load model
      // reorder trees and print them
      System.err.println("Loading model from " + modelFile);
      loadModel(modelFile);
      
      while ((dependencies = DependencyUtils.getDependenciesFromCoNLLFileReader(dependencyReader, false, false)) != null) {

        Tree tree = generateShallowTree(dependencies);
        //tree.pennPrint(System.err);
        
        //System.err.println(tree.yield());
        
        
        System.out.println(preorder(tree));
      }  
     
      
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
      List<String> features = new LinkedList<>();
      
      List<String> leftFeatures = a.extractFeatures("l");
      List<String> rightFeatures = b.extractFeatures("r");
      
//      for (int i = 0, lfc = leftFeatures.size(); i < lfc; i++) {
//        for (int j = i + 1; j < lfc; j++) {
//          leftFeatures.add(leftFeatures.get(i) + "_" + leftFeatures.get(j));
//        }
//      }
//      
//      for (int i = 0, lfc = rightFeatures.size(); i < lfc; i++) {
//        for (int j = i + 1; j < lfc; j++) {
//          rightFeatures.add(rightFeatures.get(i) + "_" + rightFeatures.get(j));
//        }
//      }


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
    
    FeatureNode(Tree node, IndexedWord hw) {
      
      List<Label> yield = node.yield();
      
      this.word = (IndexedWord) node.label();
      this.hw = hw;
      this.lm = (IndexedWord) yield.get(0);
      this.rm = (IndexedWord) yield.get(yield.size() - 1);
      this.dst = hw.index() - this.word.index();
    }
    
    List<String> extractFeatures(String prefix) {
      List<String> features = new LinkedList<>();
      //dependency label
      features.add(prefix + ":l:" + this.word.lemma());
      //POS tag
      features.add(prefix + ":t:" + this.word.tag());
      //word
      //features.add(prefix + ":w:" + getWordOrClass(this.word));
      //word class
      //features.add(prefix + ":c:" + getClass(this.word));
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
      if (mostFrequentTokens.contains(iw.toString().toLowerCase())) {
        return iw.toString().toLowerCase();
      }
      return this.getClass(iw);
    }
    
    String getClass(IndexedWord iw) {
      //return iw.toString();
      return classMap.get(new IString(iw.toString().toLowerCase())).toString();
    }
    
  }

  
  private static class LocalWordClassMap extends AbstractWordClassMap {
    public LocalWordClassMap() {
      wordToClass = new HashMap<>();
    }
  }

  
  private static class Model implements Serializable {
    
    //private static final long serialVersionUID = 1L;
    
    public LogisticClassifier<Integer, String> classifier;
    public Set<String> mostFrequentTokens;
    
    
    public Model(LogisticClassifier<Integer, String> classifier,  Set<String> mostFrequentTokens) {
      this.classifier = classifier;
      this.mostFrequentTokens = mostFrequentTokens;
    }
    
  }
  

}
