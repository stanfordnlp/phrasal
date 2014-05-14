package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;

import java.util.TreeSet;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
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

public class DependencyProjector {

  private static int parseFileIndex = 0;
  private static int parseSentenceIndex = 0;

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

    return optionArgDefs;
  }
  
  
  

  public static void printJSONHeader() {
    System.err.print("{ \"action\": \"convert\", \"attributes\": [], \"comments\": [], \"ctime\": 1398402641.0, \"equivs\": [], \"events\": [], \"messages\": [], \"modifications\": [], \"mtime\": 1398402641.0, \"normalizations\": [], \"protocol\": 1, \"source_files\": [ \"ann\", \"txt\" ], \"triggers\": [],");
  }
  
  public static void printJSONFooter() {
    System.err.println("};");

  }
  
  public static void printJSONTokens(List<String> tokens, List<String> posTags) {

    int len = tokens.size();
    int j = 0;

    StringBuffer tokenOffsets = new StringBuffer();
    tokenOffsets.append("\"token_offsets\": [");
    
    System.err.print("\"entities\": [");
    for (int i = 0; i < len; i++) {
      if (i > 0) {
        System.err.print(",");
        tokenOffsets.append(",");
      }
      int end = j + tokens.get(i).length();
      System.err.print("[\"T"+ (i+1) +"\", \""+ posTags.get(i) +"\", [[" + j + ","  + end + "]]]");
      tokenOffsets.append("[" + j + ","  + end + "]");
      j = end + 1;
    }
    System.err.print("],");
    tokenOffsets.append("],");
    System.err.print(tokenOffsets.toString());
    
    System.err.print("\"sentence_offsets\": " + "[[0," + (j-1) + "]],");
    
    System.err.print("\"text\":\"");
    for (String t : tokens) {
        System.err.print(t + " ");
    }
    System.err.print("\",");
    

  }
  
  //return all dfs sequences

  public static void printDependencyString(Map<Integer, Set<Integer>> dependencies, int idx, Sequence<IString> tokens, String parentString) {
    if (dependencies.get(idx) == null || dependencies.get(idx).isEmpty()) {
      System.out.print(parentString);
      if (idx > -1)  {
        if (parentString.length() > 0)
          System.out.print(" ");
        System.out.print(tokens.get(idx).word());
        
      }
     System.out.println("");
    } else {
      String word = idx > -1 ? tokens.get(idx).word() : "";
      String baseString = parentString.length() > 0 && idx > -1 ? parentString + " " + word : parentString + word;
      for (Integer child : dependencies.get(idx)) {
        printDependencyString(dependencies, child, tokens, baseString);
      }
    }
  }
  
  public static void printLeftAndRightDependencies(Map<Integer, NavigableSet<Integer>> dependencies, Sequence<IString> tokens) throws IOException {
    for (Integer idx : dependencies.keySet()) {
      if (dependencies.get(idx) != null && !dependencies.get(idx).isEmpty()) {
        if (idx >= 0) {
            NavigableSet<Integer> leftNodes =  dependencies.get(idx).headSet(idx, false);
            NavigableSet<Integer> rightNodes =  dependencies.get(idx).tailSet(idx, false);
  
            if (!leftNodes.isEmpty()) {
              leftDepLMWriter.write(tokens.get(idx).word() + HEAD_SUFFIX);
              leftDepLMWriter.write(" ");
              for (Integer child : leftNodes.descendingSet()) {
                leftDepLMWriter.write(tokens.get(child).word());
                leftDepLMWriter.write(" ");
              }
              leftDepLMWriter.write("\n");
            }
            
            if (!rightNodes.isEmpty()) {
              rightDepLMWriter.write(tokens.get(idx).word() + HEAD_SUFFIX);
              rightDepLMWriter.write(" ");
              for (Integer child : rightNodes) {
                rightDepLMWriter.write(tokens.get(child).word());
                rightDepLMWriter.write(" ");
              }
              rightDepLMWriter.write("\n");
            }          
        } else {
          for (int headIdx : dependencies.get(idx)) {
            headDepLMWriter.write(tokens.get(headIdx).word());
            headDepLMWriter.write("\n");
          }
        }
      }
    }
  }
  
 /* 
  public static Map<Integer, NavigableSet<Integer>> projectDependencies(CoreMap annotation, SymmetricalWordAlignment alignment) {
    Map<Integer, NavigableSet<Integer>> projectedDependencies = Generics.newHashMap();

    SemanticGraph semanticGraph = annotation.get(BasicDependenciesAnnotation.class);

    Collection<TypedDependency> dependencies = semanticGraph.typedDependencies();
    HashMap<Integer, Integer> reverseDependencies = new HashMap<Integer, Integer>() ;
        
    for (TypedDependency dep : dependencies) {
      int govIndex = dep.gov().index() - 1;
      int depIndex = dep.dep().index() - 1;
      reverseDependencies.put(depIndex, govIndex);
    }
    

    int len = alignment.eSize();

    Integer tSentenceHeadIdx = -1;
    //add root
    Collection<IndexedWord> sentenceHeads = semanticGraph.getRoots();
    NavigableSet<Integer> tHeadIndices = null;
    if (!sentenceHeads.isEmpty()) {
      IndexedWord sentenceHead = semanticGraph.getFirstRoot();
      int sHeadIndex = sentenceHead.index() - 1;
      tHeadIndices = Generics.newTreeSet(alignment.f2e(sHeadIndex));
      if (tHeadIndices.size() == 1) {
        tSentenceHeadIdx = tHeadIndices.last();
        projectedDependencies.put(-1, tHeadIndices);
      } else if (tHeadIndices.size() > 1) {
        //make the right-most word the sentence head and make all the other words dependent of the head
        tSentenceHeadIdx = tHeadIndices.last();
        NavigableSet<Integer> depSet = Generics.newTreeSet();
        projectedDependencies.put(tSentenceHeadIdx, depSet);

        for (Integer idx : tHeadIndices ) {
          if (idx != tSentenceHeadIdx)
          projectedDependencies.get(tSentenceHeadIdx).add(idx);
        }
        
        NavigableSet<Integer> headSet = Generics.newTreeSet();
        headSet.add(tSentenceHeadIdx);
        projectedDependencies.put(-1, headSet);
      }
    }
    
    
    for (int j = 0; j < len; j++) {
      String word = alignment.e().get(j).word();
      //ignore garbage collection alignments to punction marks
      if (!Character.isAlphabetic(word.charAt(0))) 
        continue;

      //don't add dependencies for sentence heads
      if (tHeadIndices != null && tHeadIndices.contains(j))
        continue;
      
      SortedSet<Integer> sIdxs = alignment.e2f(j);
      Pair<Integer, Integer> dep = null; 
      //one-to-*      
      if (sIdxs.size() > 0) {
        //SortedSet<Integer> tIdxs = alignment.f2e(sIdxs.first());
        //one-to-1+
        //if (tIdxs.size()  > 0) {
          int depSIdx = sIdxs.first();
          int headIdx;
          while((reverseDependencies.get(depSIdx) != null) 
              && (headIdx = reverseDependencies.get(depSIdx)) > -1) {
            SortedSet<Integer> headTIdxs = alignment.f2e(headIdx);
            //one-to-one and one-to-many
            if (headTIdxs.size() > 0 && (headTIdxs.first() !=  j || sIdxs.size() < 2)) {
              //prevent self loops and that the sentence head depends on any other word
              if (headTIdxs.first() !=  j && tSentenceHeadIdx != j) {
                dep = Generics.newPair(headTIdxs.last(), j);
              } 
              break;
            } else { //unaligned, try to map to transitive head
              depSIdx = headIdx;
            }
          }
      }
    
      if (dep != null) {
        if (projectedDependencies.get(dep.first()) == null) 
          projectedDependencies.put(dep.first(), new TreeSet<Integer>());
        projectedDependencies.get(dep.first()).add(dep.second());
      }
    }
    
   
    
    
    return projectedDependencies;
  }
 */
 
  public static Map<Integer, NavigableSet<Integer>> projectDependencies(CoreMap annotation, SymmetricalWordAlignment alignment) {
    Map<Integer, NavigableSet<Integer>> projectedDependencies = Generics.newHashMap();
    
    //source to target token aligment (we force 1:1)
    Map<Integer, Integer> alignedSourceTokens = Generics.newHashMap();
    
    //left dependencies indexed by source head index
    Map<Integer, SortedSet<Integer>> leftDependencies = Generics.newHashMap();
    
    SemanticGraph semanticGraph = annotation.get(BasicDependenciesAnnotation.class);

    Collection<TypedDependency> dependencies = semanticGraph.typedDependencies();
    HashMap<Integer, Integer> reverseDependencies = new HashMap<Integer, Integer>() ;
    HashMap<Integer, Set<Integer>> forwardDependencies = new HashMap<Integer, Set<Integer>>();
    
    for (TypedDependency dep : dependencies) {
      int govIndex = dep.gov().index() - 1;
      int depIndex = dep.dep().index() - 1;
      reverseDependencies.put(depIndex, govIndex);
    }
    
    //delete all nodes that are not aligned and make things transitive
    for (int depIndex : reverseDependencies.keySet()) {
      int govIndex = reverseDependencies.get(depIndex);
      if (govIndex == -1)
        continue;
      while (govIndex != -1 && (alignment.f2e(govIndex) == null || alignment.f2e(govIndex).size() < 1)) {
        govIndex = reverseDependencies.get(govIndex);
      }
      reverseDependencies.put(depIndex, govIndex);
    }
    
    for (int depIndex : reverseDependencies.keySet()) {
      int govIndex = reverseDependencies.get(depIndex);
      if (!forwardDependencies.containsKey(govIndex)) {
        forwardDependencies.put(govIndex, new HashSet<Integer>());
      }
      forwardDependencies.get(govIndex).add(depIndex);
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
        sourceDepIndex = j;
        if ((sourceGovIndex = reverseDependencies.get(sourceDepIndex)) != null)
          break;
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
  
  
  public static void projectSentence(CoreMap annotation, SymmetricalWordAlignment alignment) {
    SemanticGraph semanticGraph = annotation.get(BasicDependenciesAnnotation.class);
    Tree parseTree = annotation.get(TreeAnnotation.class);
    
    List<Label> tokenLabels = parseTree.yield();
    List<Label> posTagLabels = parseTree.preTerminalYield();
    
    int len = tokenLabels.size();

    List<String> tokens = Generics.newArrayList(len);
    List<String> posTags = Generics.newArrayList(len);
    for (int j = 0; j < len; j++) {
      tokens.add(tokenLabels.get(j).value());
      posTags.add(posTagLabels.get(j).value());

    }
    
    System.err.print("sourcedep = ");
    
    printJSONHeader();

    printJSONTokens(tokens, posTags);
    
    
    Collection<TypedDependency> dependencies = semanticGraph.typedDependencies();
    
    
    System.err.print("\"relations\": [");
    
    HashMap<Integer, Integer> reverseDependencies = new HashMap<Integer, Integer>() ;
    
    for (TypedDependency dep : semanticGraph.typedDependencies()) {
      int govIndex = dep.gov().index() - 1;
      int depIndex = dep.dep().index() - 1;
      reverseDependencies.put(depIndex, govIndex);
    }
    

    
    boolean first = true;
    int i = 1;
    for (TypedDependency dep : dependencies) {
      if (!first) 
        System.err.print(",");
      first = false;
      System.err.print("[");
      System.err.print("\"R" + i + "\",");
      System.err.print("\"" + dep.reln().getShortName() + "\",");
      System.err.print("[[\"Governor\", \"T" + dep.gov().index() + "\"], [\"Dependent\", \"T" + dep.dep().index() + "\"]]");
      System.err.print("]");

      i++;
      
      
    }
    
    System.err.print("]");
    printJSONFooter();

    System.err.print("targetdep = ");

    printJSONHeader();

    len = alignment.eSize();
    tokens = Generics.newArrayList(len);
    posTags = Generics.newArrayList(len);
    
    for (int j = 0; j < len; j++) {
      tokens.add(alignment.e().get(j).word());
      SortedSet<Integer> sourceIdxs = alignment.e2f(j);
      if (!sourceIdxs.isEmpty()) {
        posTags.add(posTagLabels.get(sourceIdxs.last()).value());
      } else {
        posTags.add("UNK");
      }

      
    }
    
    printJSONTokens(tokens, posTags);

    System.err.print("\"relations\": [");

    
    i = 1;
    first = true;
    
    for (int j = 0; j < len; j++) {
      SortedSet<Integer> sIdxs = alignment.e2f(j);
      Triple<Integer, Integer, String> dep = null; 
      //one-to-*      
      if (sIdxs.size() > 0) {
        //SortedSet<Integer> tIdxs = alignment.f2e(sIdxs.first());
        //one-to-1+
        //if (tIdxs.size()  > 0) {
          int depSIdx = sIdxs.first();
          int headIdx;
          while((reverseDependencies.get(depSIdx) != null) && (headIdx = reverseDependencies.get(depSIdx)) > -1) {
            SortedSet<Integer> headTIdxs = alignment.f2e(headIdx);
            //one-to-one and one-to-many
            if (headTIdxs.size() > 0 && (headTIdxs.first() !=  j || sIdxs.size() < 2)) {
              if (headTIdxs.first() !=  j) {
                dep = Generics.newTriple(headTIdxs.last(), j, "dep");
              } 
           
              break;
            } else { //unaligned, try to map to transitive head
              depSIdx = headIdx;
            }
          }
          
          //handle special case where root aligns to two words
          //handle special case where root does not align to any word
          
          
          
        //} else { //unaligned
          //do nothing
        //} 
      }
    
      if (dep !=null) {
        if (!first) 
          System.err.print(",");
        first = false;
        System.err.print("[");
        System.err.print("\"R" + i + "\",");
        System.err.print("\"" + dep.third + "\",");
        System.err.print("[[\"Governor\", \"T" + (dep.first + 1) + "\"], [\"Dependent\", \"T" + (dep.second() + 1) + "\"]]");
        System.err.print("]");

        i++;

      }
    }
    System.err.print("]");

    printJSONFooter();
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
    String outdirPath = PropertiesUtils.get(options, "outdir", ".", String.class);
    String leftDepLMFilename = outdirPath + File.separator + "left.deplm";
    String rightDepLMFilename = outdirPath + File.separator + "right.deplm";
    String headDepLMFilename = outdirPath + File.separator + "head.deplm";


    File leftDepLMFile = new File(leftDepLMFilename);
    if (!leftDepLMFile.exists())
      leftDepLMFile.createNewFile();

    
    File rightDepLMFile = new File(rightDepLMFilename);
    if (!rightDepLMFile.exists())
      rightDepLMFile.createNewFile();
    
    File headDepLMFile = new File(headDepLMFilename);
    if (!headDepLMFile.exists())
      headDepLMFile.createNewFile();

    FileWriter leftFW = new FileWriter(leftDepLMFile.getAbsoluteFile());
    FileWriter rightFW = new FileWriter(rightDepLMFile.getAbsoluteFile());
    FileWriter headFW = new FileWriter(headDepLMFile.getAbsoluteFile());

    leftDepLMWriter = new BufferedWriter(leftFW);
    rightDepLMWriter = new BufferedWriter(rightFW);
    headDepLMWriter = new BufferedWriter(headFW);

    
    boolean annotationsSplit = PropertiesUtils.getBool(options, "annotationsSplit", false);
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
        Map<Integer, NavigableSet<Integer>> dependencies = projectDependencies(sentence, alignment);
        //if (i == 0) {
        //  System.err.println(dependencies.get(-1));
        //  System.err.println(dependencies.get(1));

        //}
        //printDependencyString(dependencies, -1, alignment.e(), "");
        System.out.println(dependencies);
        printLeftAndRightDependencies(dependencies, alignment.e());
        //System.err.println("---------------------------");
      //} catch (Exception e) {
      //  e.printStackTrace();
      //}
      i++;
    }
  
    
    sourceReader.close();
    targetReader.close();
    alignmentReader.close();

    leftDepLMWriter.close();
    rightDepLMWriter.close();
    headDepLMWriter.close();
    
  }

}
