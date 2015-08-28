package edu.stanford.nlp.mt.tools.deplm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class BuildDependencyLMData {

  private static BufferedWriter leftDepLMWriter;
  private static BufferedWriter rightDepLMWriter;
  private static BufferedWriter headDepLMWriter;

  private static String HEAD_SUFFIX = "<HEAD>";
  private static String ROOT_SUFFIX = "<ROOT>";
  private static String FRAG_SUFFIX = "<FRAG>";


  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = new HashMap<>();
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
  
  

  public static void printLeftAndRightDependencies(Map<Integer, NavigableSet<Integer>> dependencies, Sequence<String> tokens) throws IOException {
    for (Integer idx : dependencies.keySet()) {
      if (dependencies.get(idx) != null && !dependencies.get(idx).isEmpty()) {
        if (idx >= 0) {
            NavigableSet<Integer> leftNodes =  dependencies.get(idx).headSet(idx, false);
            NavigableSet<Integer> rightNodes =  dependencies.get(idx).tailSet(idx, false);
  
            leftDepLMWriter.write(tokens.get(idx) + HEAD_SUFFIX);
            leftDepLMWriter.write(" ");
            for (Integer child : leftNodes.descendingSet()) {
              leftDepLMWriter.write(tokens.get(child));
              leftDepLMWriter.write(" ");
            }
            leftDepLMWriter.write("\n");
            
            rightDepLMWriter.write(tokens.get(idx) + HEAD_SUFFIX);
            rightDepLMWriter.write(" ");
            for (Integer child : rightNodes) {
              rightDepLMWriter.write(tokens.get(child));
              rightDepLMWriter.write(" ");
            }
            rightDepLMWriter.write("\n");          
        } else {
          for (int headIdx : dependencies.get(idx)) {
            String suffix = idx == -1 ? ROOT_SUFFIX : FRAG_SUFFIX;
            headDepLMWriter.write(tokens.get(headIdx) + suffix);
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
    Map<Integer, NavigableSet<Integer>> dependencies = new HashMap<>();
    List<String> tokens = new LinkedList<>();
    while (true) {
      sourceSentence = sourceReader.readLine();
      if (sourceSentence == null || sourceSentence.equals("")) {
        printLeftAndRightDependencies(dependencies, new ArraySequence<String>(tokens));
        dependencies = new HashMap<>();
        tokens = new LinkedList<>();
        if (sourceSentence == null) {
          break;
        } else {
          continue;
        }
      }
     
      String fields[] = sourceSentence.split("\t");
      int id = Integer.parseInt(fields[0]) - 1;
      int head = fields[7].equals("frag") ? -2 : Integer.parseInt(fields[6]) - 1;
     
      String token = fields[1];
      tokens.add(token);
      if (!isWord(token))
        continue;
      if (!dependencies.containsKey(head)) 
        dependencies.put(head, new TreeSet<Integer>());
      if (!dependencies.containsKey(id))
        dependencies.put(id, new TreeSet<Integer>());
      dependencies.get(head).add(id);
    }
  
    
    sourceReader.close();

    leftDepLMWriter.close();
    rightDepLMWriter.close();
    headDepLMWriter.close();
    
  }

}
