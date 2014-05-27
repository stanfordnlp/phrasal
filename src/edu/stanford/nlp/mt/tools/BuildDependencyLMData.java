package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class BuildDependencyLMData {

  private static BufferedWriter leftDepLMWriter;
  private static BufferedWriter rightDepLMWriter;
  private static BufferedWriter headDepLMWriter;

  private static String HEAD_SUFFIX = "<HEAD>";
  
  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("input", 1); 
    optionArgDefs.put("outdir", 1);
    return optionArgDefs;
  }
  
  
  /*
   * returns true if token is a word 
   * (starts with a letter or a digit)
   */
  private static boolean isWord(String token) {
    return Character.isAlphabetic(token.charAt(0)) || Character.isDigit(token.charAt(0));
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
  
  public static void printLeftAndRightDependencies(Map<Integer, NavigableSet<Integer>> dependencies, Sequence<String> tokens) throws IOException {
    for (Integer idx : dependencies.keySet()) {
      if (dependencies.get(idx) != null && !dependencies.get(idx).isEmpty()) {
        if (idx >= 0) {
            NavigableSet<Integer> leftNodes =  dependencies.get(idx).headSet(idx, false);
            NavigableSet<Integer> rightNodes =  dependencies.get(idx).tailSet(idx, false);
  
            if (!leftNodes.isEmpty()) {
              leftDepLMWriter.write(tokens.get(idx) + HEAD_SUFFIX);
              leftDepLMWriter.write(" ");
              for (Integer child : leftNodes.descendingSet()) {
                leftDepLMWriter.write(tokens.get(child));
                leftDepLMWriter.write(" ");
              }
              leftDepLMWriter.write("\n");
            }
            
            if (!rightNodes.isEmpty()) {
              rightDepLMWriter.write(tokens.get(idx) + HEAD_SUFFIX);
              rightDepLMWriter.write(" ");
              for (Integer child : rightNodes) {
                rightDepLMWriter.write(tokens.get(child));
                rightDepLMWriter.write(" ");
              }
              rightDepLMWriter.write("\n");
            }          
        } else {
          for (int headIdx : dependencies.get(idx)) {
            headDepLMWriter.write(tokens.get(headIdx));
            headDepLMWriter.write("\n");
          }
        }
      }
    }
  }
  

 
 
  
  public static void main(String[] args) throws IOException {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    String sourceTokens = PropertiesUtils.get(options, "input", null, String.class);
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

    File sourceSentences = new File(sourceTokens);
    BufferedReader sourceReader = new BufferedReader(new FileReader(sourceSentences));
    String sourceSentence;
    Map<Integer, NavigableSet<Integer>> dependencies = Generics.newHashMap();
    List<String> tokens = Generics.newLinkedList();
    while (true) {
      sourceSentence = sourceReader.readLine();
      if (sourceSentence == null || sourceSentence.equals("")) {
        printLeftAndRightDependencies(dependencies, new SimpleSequence<String>(tokens));
        dependencies = Generics.newHashMap();
        tokens = Generics.newLinkedList();
        if (sourceSentence == null) {
          break;
        } else {
          continue;
        }
      }
     
      String fields[] = sourceSentence.split("\t");
      int id = Integer.parseInt(fields[0]) - 1;
      int head = Integer.parseInt(fields[6]) - 1;
      String token = fields[1];
      tokens.add(token);
      if (!isWord(token))
        continue;
      if (!dependencies.containsKey(head)) 
        dependencies.put(head, new TreeSet<Integer>());
      dependencies.get(head).add(id);
    }
  
    
    sourceReader.close();

    leftDepLMWriter.close();
    rightDepLMWriter.close();
    headDepLMWriter.close();
    
  }

}
