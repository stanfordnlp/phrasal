package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;

public class DependencyProjectorCoNLL {

  private static int parseFileIndex = 0;
  private static int parseSentenceIndex = 0;

  private static int skippedSentences = 0;
  private static int processedSentences = 0;
  
  private static BufferedWriter leftDepLMWriter;
  private static BufferedWriter rightDepLMWriter;
  private static BufferedWriter headDepLMWriter;

  private static String HEAD_SUFFIX = "<HEAD>";
  
  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("annotationsSplit", 0); 
    optionArgDefs.put("sourceTokens", 1); 
    optionArgDefs.put("targetTokens", 1); 
    optionArgDefs.put("alignment", 1);
    optionArgDefs.put("annotations", 1);
    optionArgDefs.put("outdir", 1);
    optionArgDefs.put("transitive", 0);

    return optionArgDefs;
  }
  
  
  
  public static void printDependencies(Map<Integer, NavigableSet<Integer>> dependencies, Sequence<IString> tokens, List<String> posTags) {
    Map<Integer,Integer> reverseDependencies = Generics.newHashMap();
    
    for (int head : dependencies.keySet()) {
      for (int dep : dependencies.get(head)) {
        reverseDependencies.put(dep, head);
      }
    }
    int fragmentCount = 1;
    for (int i = 0; i < tokens.size(); i++) {
      if (reverseDependencies.get(i) == null) {
        if (tokens.get(i).word().length() > 0 && Character.isAlphabetic(tokens.get(i).word().charAt(0))) {
          reverseDependencies.put(i, -2);
          fragmentCount++;
        } else {
          reverseDependencies.put(i, -3);
        }
      } else if (reverseDependencies.get(i) == -2) {
        fragmentCount++;
      }
    }
    
    if (fragmentCount > 3) {
      skippedSentences++;
      return;
    }
    
    for (int i = 0; i < tokens.size(); i++) {
      System.out.print((i + 1)); //index
      System.out.print("\t");
      System.out.print(tokens.get(i).word()); //form
      System.out.print("\t");  
      System.out.print("_"); //lemma
      System.out.print("\t"); 
      System.out.print(posTags.get(i)); //pos tag
      System.out.print("\t");
      System.out.print(posTags.get(i)); //pos tag
      System.out.print("\t");
      System.out.print("_"); //feats
      System.out.print("\t");
      if (reverseDependencies.get(i) > -1) {
        System.out.print((reverseDependencies.get(i)  + 1)); //head
        System.out.print("\t");
        System.out.print("dep"); //DEPREL
      } else if (reverseDependencies.get(i) > -2) {
        System.out.print("0"); //head
        System.out.print("\t");
        System.out.print("root"); //DEPREL
      } else if (reverseDependencies.get(i) > -3){
        System.out.print("0"); //head
        System.out.print("\t");
        System.out.print("frag"); //DEPREL
      } else {
        System.out.print("0"); //head
        System.out.print("\t");
        System.out.print("punct"); //DEPREL
      }
      System.out.print("\t");
      System.out.print("_"); //PHEAD
      System.out.print("\t");
      System.out.print("_"); //PDEPREL
      
      System.out.println("");
      
    }
          
    processedSentences++;
    
  }

 
   
  public static Map<Integer, NavigableSet<Integer>> projectDependencies(CoreMap annotation, SymmetricalWordAlignment alignment, boolean transitive) {
    Map<Integer, NavigableSet<Integer>> projectedDependencies = Generics.newHashMap();
    
    //source to target token aligment (we force 1:1)
    Map<Integer, Integer> alignedSourceTokens = Generics.newHashMap();
    
    //left dependencies indexed by source head index
    Map<Integer, SortedSet<Integer>> leftDependencies = Generics.newHashMap();
    
    SemanticGraph semanticGraph = annotation.get(BasicDependenciesAnnotation.class);

    Collection<TypedDependency> dependencies = semanticGraph.typedDependencies();
    HashMap<Integer, Integer> reverseDependencies = new HashMap<Integer, Integer>() ;
    
    
    for (TypedDependency dep : dependencies) {
      int govIndex = dep.gov().index() - 1;
      int depIndex = dep.dep().index() - 1;
      reverseDependencies.put(depIndex, govIndex);
    }
    
    if (transitive) {
      //delete all nodes that are not aligned and make things transitive
      for (int depIndex : reverseDependencies.keySet()) {
        Integer govIndex = reverseDependencies.get(depIndex);
        if (govIndex == null || govIndex == -1)
          continue;
        boolean changed = false;
        while (govIndex != null && govIndex > -1 && (alignment.f2e(govIndex) == null || alignment.f2e(govIndex).size() < 1)) {
          govIndex = reverseDependencies.get(govIndex);
          changed = true;
        }
        
        if (changed && govIndex == -1)
          govIndex = -2;
        
        reverseDependencies.put(depIndex, govIndex);
      }
    }

    int len = alignment.eSize();

    for (int i = 0; i < len; i++) {
      IString token = alignment.e().get(i);
      if (token.word().length() < 1 || !Character.isAlphabetic(token.word().charAt(0)))
        continue;
      if (alignment.e2f(i) == null || alignment.e2f(i).size() < 1)
        continue;
      
      Integer sourceGovIndex = null;
      int sourceDepIndex = -1;
      for (int j : alignment.e2f(i)) {
        int k = j;
        if (sourceGovIndex == null &&  reverseDependencies.get(k) != null) {
          sourceGovIndex = reverseDependencies.get(k);
          sourceDepIndex = k;
        } else if (reverseDependencies.get(k) != null) {
          if (sourceGovIndex == k) {
            sourceGovIndex = reverseDependencies.get(k);
            sourceDepIndex = k;
          }
        }
      }
      
      //check if the current word has a head
      if (sourceGovIndex == null)
        continue; 

      //force 1:1 alignment
      if (alignedSourceTokens.containsKey(sourceDepIndex))
        continue;
      
      alignedSourceTokens.put(sourceDepIndex, i);
      //check for root
      if (sourceGovIndex == -1) {
        if (projectedDependencies.get(-1) == null)
          projectedDependencies.put(-1, new TreeSet<Integer>());
        //add root dependency
        projectedDependencies.get(-1).add(i);
      } else {
          if (alignedSourceTokens.containsKey(sourceGovIndex)) {
            int targetGovIndex = alignedSourceTokens.get(sourceGovIndex);
            if (!projectedDependencies.containsKey(targetGovIndex)) {
              projectedDependencies.put(targetGovIndex, new TreeSet<Integer>());
            }
            //add right dependency
            projectedDependencies.get(targetGovIndex).add(i);
        } else {
          if (!leftDependencies.containsKey(sourceGovIndex))
            leftDependencies.put(sourceGovIndex, new TreeSet<Integer>());
          leftDependencies.get(sourceGovIndex).add(i);
        }
      }
      
      
      //add all the left dependents 
      if (leftDependencies.containsKey(sourceDepIndex)) {
        if (!projectedDependencies.containsKey(i))
          projectedDependencies.put(i, new TreeSet<Integer>());

        for (int j : leftDependencies.get(sourceDepIndex)) {
          projectedDependencies.get(i).add(j);
        }
        leftDependencies.remove(sourceDepIndex);
      }
    }
    
   
    return projectedDependencies;
  }

  
  public static CoreMap getParsedSentence(String filename, int index, boolean isSplit) {
    if (!isSplit) {
      if (!CoreNLPCache.isLoaded()) {
        CoreNLPCache.loadSerialized(filename);
      }
      return CoreNLPCache.get(index);
    } else {
      if (!CoreNLPCache.isLoaded()) {
        parseFileIndex = 0;
        parseSentenceIndex = 0;
        String composedFilename = filename + "." + parseFileIndex;
        CoreNLPCache.loadSerialized(composedFilename);
      }
      CoreMap c = CoreNLPCache.get(index - parseSentenceIndex);
      if (c == null) {
        parseFileIndex++;
        String composedFilename = filename + "." + parseFileIndex;
        File file = new File(composedFilename);
        if (file.exists()) {
          parseSentenceIndex = index;
          CoreNLPCache.loadSerialized(composedFilename);
          c = CoreNLPCache.get(index - parseSentenceIndex);
        }
      }
      return c;
    }
  }
  
  public static void main(String[] args) throws IOException {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    String sourceTokens = PropertiesUtils.get(options, "sourceTokens", null, String.class);
    String targetTokens = PropertiesUtils.get(options, "targetTokens", null, String.class);
    String alignments = PropertiesUtils.get(options, "alignment", null, String.class);
    String annotations = PropertiesUtils.get(options, "annotations", null, String.class);
 

 

    
    boolean annotationsSplit = PropertiesUtils.getBool(options, "annotationsSplit", false);
    boolean transitive = PropertiesUtils.getBool(options, "transitive", true);

    File sourceSentences = new File(sourceTokens);
    File targetSentences = new File(targetTokens);
    File alignmentFile = new File(alignments);
    BufferedReader sourceReader = new BufferedReader(new FileReader(sourceSentences));
    BufferedReader targetReader = new BufferedReader(new FileReader(targetSentences));
    BufferedReader alignmentReader = new BufferedReader(new FileReader(alignmentFile));
    String sourceSentence;
    int i = 0;
    while ((sourceSentence = sourceReader.readLine()) != null) {
      //try {
        CoreMap sentence = getParsedSentence(annotations, i, annotationsSplit);
        String targetSentence = targetReader.readLine();
        String alignmentString = alignmentReader.readLine();
        //System.err.println("---------------------------");
        //System.err.println("alignment = \"" + alignmentString + "\";");
        SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sourceSentence, targetSentence, alignmentString);
        //projectSentence(sentence, alignment);
        Map<Integer, NavigableSet<Integer>> dependencies = projectDependencies(sentence, alignment, transitive);
        //if (i == 0) {
        //  System.err.println(dependencies.get(-1));
        //  System.err.println(dependencies.get(1));

        //}
        //printDependencyString(dependencies, -1, alignment.e(), "");
        //System.out.println(dependencies);
        
        List<String> posTags = Generics.newArrayList();
        for (IString s : alignment.e()) {
          posTags.add("POS");
        }
        
        printDependencies(dependencies, alignment.e(), posTags);
        //System.err.println("---------------------------");
      //} catch (Exception e) {
      //  e.printStackTrace();
      //}
      i++;
    }
  
    System.err.println("Processed sentences: " + processedSentences);
    System.err.println("Skipped sentences:" + skippedSentences);
    
    sourceReader.close();
    targetReader.close();
    alignmentReader.close();

  
    
  }

}
