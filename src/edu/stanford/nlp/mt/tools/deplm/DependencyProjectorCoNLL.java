package edu.stanford.nlp.mt.tools.deplm;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.stanford.nlp.mt.util.CoreNLPCache;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;

import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class DependencyProjectorCoNLL {

  private static int parseFileIndex = 0;
  private static int parseSentenceIndex = 0;

  private static int skippedSentences = 0;
  private static int processedSentences = 0;
    
  private static Map<String, Integer> DEFAULT_ATTACHMENT = new HashMap<>();
  
  static {
    //root = 0
    //left = -1
    //right = 1
    DEFAULT_ATTACHMENT.put("adv", 1);
    DEFAULT_ATTACHMENT.put("noun", -1);
    DEFAULT_ATTACHMENT.put("adp", 1);
    DEFAULT_ATTACHMENT.put("prt", 1);
    DEFAULT_ATTACHMENT.put("det", 1);
    DEFAULT_ATTACHMENT.put("num", 1);
    DEFAULT_ATTACHMENT.put(".", 0);
    DEFAULT_ATTACHMENT.put("pron", 1);
    DEFAULT_ATTACHMENT.put("verb", -1);
    DEFAULT_ATTACHMENT.put("x", 1);
    DEFAULT_ATTACHMENT.put("conj", -1);
    DEFAULT_ATTACHMENT.put("adj", 1);  
  }
  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = new HashMap<>();
    optionArgDefs.put("annotationsSplit", 0); 
    optionArgDefs.put("sourceTokens", 1); 
    optionArgDefs.put("targetTokens", 1); 
    optionArgDefs.put("alignment", 1);
    optionArgDefs.put("annotations", 1);
    optionArgDefs.put("transitive", 0);
    optionArgDefs.put("maxFragments", 1);
    optionArgDefs.put("posTagged", 0);

    
    return optionArgDefs;
  }
  
  /*
   * returns true if token is a word 
   * (starts with a letter or a digit)
   */
  private static boolean isWord(String token) {
    return Character.isAlphabetic(token.charAt(0)) || Character.isDigit(token.charAt(0));
  }
  
  public static void printDependencies(Map<Integer, NavigableSet<Integer>> dependencies, Sequence<IString> tokens, int maxFragments, boolean posTagged) {
    Map<Integer,Integer> reverseDependencies = new HashMap<>();
    
    for (int head : dependencies.keySet()) {
      for (int dep : dependencies.get(head)) {
        reverseDependencies.put(dep, head);
      }
    }
    int fragmentCount = 1;
    for (int i = 0; i < tokens.size(); i++) {
      if (reverseDependencies.get(i) == null) {
        if (tokens.get(i).toString().length() > 0 && isWord(tokens.get(i).toString())) {
          reverseDependencies.put(i, -2);
          fragmentCount++;
        } else {
          reverseDependencies.put(i, -3);
        }
      } else if (reverseDependencies.get(i) == -2) {
        fragmentCount++;
      }
    }
    
    if (maxFragments > 0 && fragmentCount > maxFragments) {
      skippedSentences++;
      return;
    }
    
    for (int i = 0; i < tokens.size(); i++) {
      String token = tokens.get(i).toString();
      String parts[] = token.split("_");
      String pos = "POS";
      String word = token;
      if (posTagged && parts.length > 1) {
        word = token.substring(0, token.lastIndexOf("_"));
        pos = parts[parts.length -1];
      }
      
      System.out.print((i + 1)); //index
      System.out.print("\t");
      System.out.print(word); //form
      System.out.print("\t");  
      System.out.print("_"); //lemma
      System.out.print("\t"); 
      System.out.print(pos); //pos tag
      System.out.print("\t");
      System.out.print(pos); //pos tag
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
    System.out.println();      
    processedSentences++;
    
  }

 
  public static HashMap<Integer, Integer> getDependenciesFromCoreMap(CoreMap annotation) {

    SemanticGraph semanticGraph = annotation.get(BasicDependenciesAnnotation.class);
    Collection<TypedDependency> dependencies = semanticGraph.typedDependencies();

    
    HashMap<Integer, Integer> reverseDependencies = new HashMap<Integer, Integer>() ;

    for (TypedDependency dep : dependencies) {
      int govIndex = dep.gov().index() - 1;
      int depIndex = dep.dep().index() - 1;
      reverseDependencies.put(depIndex, govIndex);
    }
    
    return reverseDependencies;
  }
  
  public static HashMap<Integer, Integer> getDependenciesFromCoNLLFileReader(LineNumberReader reader) {
    HashMap<Integer, Integer> reverseDependencies = new HashMap<Integer, Integer>();
    
    String line = null;
    try {
      while ((line = reader.readLine()) != null && line.length() > 1) {
        String[] fields = line.split("\t");
        int dep = Integer.parseInt(fields[0]) - 1;
        int gov = Integer.parseInt(fields[6]) - 1;
        reverseDependencies.put(dep, gov);
      }
    
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    
    return reverseDependencies;
    
  }
  
   
  public static Map<Integer, NavigableSet<Integer>> projectDependencies(Map<Integer, Integer> reverseDependencies , SymmetricalWordAlignment alignment, boolean transitive) {
    Map<Integer, NavigableSet<Integer>> projectedDependencies = new HashMap<>();
    
    //source to target token aligment (we force 1:1)
    Map<Integer, Integer> alignedSourceTokens = new HashMap<>();
    
    //left dependencies indexed by source head index
    Map<Integer, SortedSet<Integer>> leftDependencies = new HashMap<>();
    
    
    if (transitive) {
      //delete all nodes that are not aligned and make things transitive
      for (int depIndex : reverseDependencies.keySet()) {
        Integer govIndex = reverseDependencies.get(depIndex);
        if (govIndex == null || govIndex == -1)
          continue;
        boolean changed = false;
        int i = 0;
        while (govIndex != null && govIndex > -1 && (alignment.f2e(govIndex) == null || alignment.f2e(govIndex).size() < 1)) {
          if (i >= 2) {
            govIndex = -2;
            break;
          }  
          govIndex = reverseDependencies.get(govIndex);
          changed = true;
          i++;
        }
        
        if (govIndex != null && changed && govIndex == -1)
          govIndex = -2;
        
        reverseDependencies.put(depIndex, govIndex);
      }
    }

    int len = alignment.eSize();

    for (int i = 0; i < len; i++) {
      IString token = alignment.e().get(i);
      if (token.toString().length() < 1 || !isWord(token.toString()))
        continue;
      if (alignment.e2f(i) == null || alignment.e2f(i).size() < 1) {
        
      }
      
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
    
   
    //try to attach unaligned tokens
    //requires POS tagged input
    //TODO: What to do about circles?
    /*for (int i = 0; i < len; i++) {
      IString token = alignment.e().get(i);
      if (token.toString().length() < 1 || !isWord(token.toString()))
        continue;
      if (alignment.e2f(i) == null || alignment.e2f(i).size() < 1) {
        String parts[] = token.toString().split("_");
        if (parts.length > 1) {
          String pos = parts[parts.length - 1];
          int dir = DEFAULT_ATTACHMENT.get(pos.toLowerCase());
          if (dir != 0) {
            int head = i + dir;
            if (!projectedDependencies.containsKey(head))
              projectedDependencies.put(head, new TreeSet<Integer>());
            projectedDependencies.get(head).add(i);
          }
        }
      }
    }
    */
    
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
    int maxFragments = PropertiesUtils.getInt(options, "maxFragments", 0);
    boolean posTagged = PropertiesUtils.getBool(options, "posTagged", true); 
    
    boolean isCoNLL = annotations.toLowerCase().endsWith(".conll");
    
    
 

    
    boolean annotationsSplit = PropertiesUtils.getBool(options, "annotationsSplit", false);
    boolean transitive = PropertiesUtils.getBool(options, "transitive", false);

    File alignmentFile = new File(alignments);
    LineNumberReader sourceReader = IOTools.getReaderFromFile(sourceTokens);
    LineNumberReader targetReader = IOTools.getReaderFromFile(targetTokens);
    LineNumberReader alignmentReader = IOTools.getReaderFromFile(alignmentFile);
    
    LineNumberReader coNLLReader = null;
    if (isCoNLL)
      coNLLReader = IOTools.getReaderFromFile(annotations);
    
    String sourceSentence;
    int i = 0;
    while ((sourceSentence = sourceReader.readLine()) != null) {
      try {
        String targetSentence = targetReader.readLine();
        String alignmentString = alignmentReader.readLine();
        SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sourceSentence, targetSentence, alignmentString);
        
        HashMap<Integer, Integer> reverseDependencies  = null;
        if (isCoNLL) {
          reverseDependencies = getDependenciesFromCoNLLFileReader(coNLLReader);
        } else {
          CoreMap sentence = getParsedSentence(annotations, i, annotationsSplit);
          reverseDependencies = getDependenciesFromCoreMap(sentence);
        }
        
        Map<Integer, NavigableSet<Integer>> dependencies = projectDependencies(reverseDependencies, alignment, transitive);
     
        printDependencies(dependencies, alignment.e(), maxFragments, posTagged);
      } catch (Exception e) {
        System.err.println("SourceSentence: " + sourceSentence);
        e.printStackTrace();
        return;
      }
      i++;
    }
  
    System.err.println("Processed sentences: " + processedSentences);
    System.err.println("Skipped sentences:" + skippedSentences);
    
    sourceReader.close();
    targetReader.close();
    alignmentReader.close();
    
    if (isCoNLL)
      coNLLReader.close();

  
    
  }

}
