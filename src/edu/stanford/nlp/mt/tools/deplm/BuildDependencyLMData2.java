package edu.stanford.nlp.mt.tools.deplm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class BuildDependencyLMData2 {

  private static BufferedWriter lmWriter;
  private static BufferedWriter headLmWriter;

  private static BufferedWriter noEventWriter;

  private static String HEAD_SUFFIX = "<HEAD>";
  private static String SIBLING_SUFFIX = "<SIB>";



  private static final IString ROOT_TOKEN = new IString("<ROOT>");
  private static final IString FRAG_TOKEN = new IString("<FRAG>");
  private static final IString START_TOKEN = new IString("<START>");
  private static final IString END_TOKEN = new IString("<END>");

  private static final IString ROOT_DIR_TOKEN = new IString("0<DIR>");
  private static final IString LEFT_DIR_TOKEN = new IString("1<DIR>");
  private static final IString RIGHT_DIR_TOKEN = new IString("2<DIR>");

  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("input", 1); 
    optionArgDefs.put("outdir", 1);
    return optionArgDefs;
  }
  

 private static void incrementHeadCount(IString child, IString head) throws IOException {
    
   head = new IString(head + HEAD_SUFFIX);
   
   headLmWriter.write(head + " " + child);
   headLmWriter.write("\n");
   
   noEventWriter.write(head.word());
   noEventWriter.write("\n");

  }
  
  private static void incrementChildCount(IString child, IString sibling, IString head, IString direction) throws IOException {
    
    head = new IString(head + HEAD_SUFFIX);
    sibling = new IString(sibling + SIBLING_SUFFIX);

    
    lmWriter.write(sibling + " " + direction + " " + head + " " + child);
    lmWriter.write("\n");

    noEventWriter.write(head.word());
    noEventWriter.write("\n");
    noEventWriter.write(direction.word());
    noEventWriter.write("\n");
    noEventWriter.write(sibling.word());
    noEventWriter.write("\n");

    
  }
  
  private static void updateCounts(HashMap<Integer, Pair<String, List<Integer>>> dependencies) throws IOException {

    for (int gov : dependencies.keySet()) {
      
      String headWord = dependencies.get(gov).first;
      if (headWord != null && !DependencyUtils.isWord(headWord))
        continue;
      
      if (gov < 1) {
        for (Integer dep : dependencies.get(gov).second) {
          String word = dependencies.get(dep).first.toLowerCase();
          if (!DependencyUtils.isWord(word))
            continue;
          
          IString depToken = new IString(word); 
          IString headToken = gov == 0 ? ROOT_TOKEN : FRAG_TOKEN;
          
          incrementHeadCount(depToken, headToken);
          incrementChildCount(depToken, START_TOKEN, headToken, ROOT_DIR_TOKEN);
          incrementChildCount(END_TOKEN, depToken, headToken, ROOT_DIR_TOKEN);
        }
          
      } else {
        List<IString> leftChildren = Generics.newLinkedList();
        List<IString> rightChildren = Generics.newLinkedList();
        
        List<Integer> sortedChildren = Generics.newLinkedList();
        sortedChildren.addAll(dependencies.get(gov).second);
        Collections.sort(sortedChildren);
        for (Integer dep : sortedChildren) {
          String word = dependencies.get(dep).first.toLowerCase();
          if (!DependencyUtils.isWord(word))
            continue;
          if (dep < gov) {
            leftChildren.add(new IString(word));
          } else {
            rightChildren.add(new IString(word));
          }
        }
        
        Collections.reverse(leftChildren);
        IString headToken = new IString(headWord);
        
        int leftChildrenCount = leftChildren.size();
        
        for (int i = 0; i <= leftChildrenCount; i++) {
          IString depToken1 =  (i > 0) ?  leftChildren.get(i-1) : START_TOKEN;
          IString depToken2 =  (i < leftChildrenCount) ?  leftChildren.get(i) : END_TOKEN;
          if (i > 0)
            incrementHeadCount(depToken1, headToken);
          
          incrementChildCount(depToken2, depToken1, headToken, LEFT_DIR_TOKEN);
        }
        
        
        int rightChildrenCount = rightChildren.size();
        for (int i = 0; i <= rightChildrenCount; i++) {
          IString depToken1 =  (i > 0) ?  rightChildren.get(i-1) : START_TOKEN;
          IString depToken2 =  (i < rightChildrenCount) ?  rightChildren.get(i) : END_TOKEN;
          if (i > 0)
            incrementHeadCount(depToken1, headToken);
          incrementChildCount(depToken2, depToken1, headToken, RIGHT_DIR_TOKEN);
        }
      }
    }
  }  


 
 
  
  public static void main(String[] args) throws IOException {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    String sourceTokens = PropertiesUtils.get(options, "input", null, String.class);
    String outdirPath = PropertiesUtils.get(options, "outdir", ".", String.class);
    String headDepLMFilename = outdirPath + File.separator + "deplm.head.data";
    String rightDepLMFilename = outdirPath + File.separator + "deplm.nonevents";
    String leftDepLMFilename = outdirPath + File.separator + "deplm.data";



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

    
    lmWriter = new BufferedWriter(leftFW);
    headLmWriter = new BufferedWriter(headFW);
    noEventWriter = new BufferedWriter(rightFW);

    LineNumberReader inputReader = IOTools.getReaderFromFile(sourceTokens);

    
    HashMap<Integer, Pair<String, List<Integer>>>  dependencies =  null;
    while ((dependencies =  DependencyUtils.getDependenciesFromCoNLLFileReader(inputReader, false)) != null) {
      updateCounts(dependencies);
    }
    
    inputReader.close();
    lmWriter.close();
    noEventWriter.close();
    headLmWriter.close();
    
  }

}
