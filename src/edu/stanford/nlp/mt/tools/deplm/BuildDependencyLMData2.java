package edu.stanford.nlp.mt.tools.deplm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.AbstractWordClassMap;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class BuildDependencyLMData2 {

  private static BufferedWriter lmWriter;
  //private static BufferedWriter headLmWriter;

  private static BufferedWriter noEventWriter;
  
  private static LocalWordClassMap classMap;

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
    optionArgDefs.put("alignment", 1);
    optionArgDefs.put("sourceTokens", 1);
    optionArgDefs.put("targetTokens", 1);
    optionArgDefs.put("classMap", 1);
    return optionArgDefs;
  }
  

 private static void incrementHeadCount(IString child, IString head) throws IOException {
   /* 
   head = new IString(head + HEAD_SUFFIX);
   
   headLmWriter.write(head + " " + child);
   headLmWriter.write("\n");
   
   noEventWriter.write(head.word());
   noEventWriter.write("\n");
   */
  }
  
  private static void incrementChildCount(IString child, IString sibling, IString head, IString direction) throws IOException {
    
    head = new IString(head + HEAD_SUFFIX);
    
    if (classMap != null && sibling != START_TOKEN) {
      sibling = classMap.get(sibling);
    }
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
  
  private static void updateCounts(HashMap<Integer, Pair<IndexedWord, List<Integer>>> dependencies, SymmetricalWordAlignment alignment) throws IOException {

    for (int gov : dependencies.keySet()) {
      
      IndexedWord iw = dependencies.get(gov).first;
      if (iw != null && TokenUtils.isPunctuation(iw.word()))
        continue;
      
      
      if (gov < 1) {
        for (Integer dep : dependencies.get(gov).second) {
          String word = dependencies.get(dep).first.word().toLowerCase();
          if (TokenUtils.isPunctuation(word))
            continue;
          
          IString depToken = new IString(word); 
          IString headToken = gov == 0 ? ROOT_TOKEN : FRAG_TOKEN;
          
          incrementHeadCount(depToken, headToken);
          incrementChildCount(depToken, START_TOKEN, headToken, ROOT_DIR_TOKEN);
          incrementChildCount(END_TOKEN, depToken, headToken, ROOT_DIR_TOKEN);
        }
          
      } else {
        String headWord = iw.word();

        List<IString> leftChildren = Generics.newLinkedList();
        List<IString> rightChildren = Generics.newLinkedList();
        
        List<Integer> sortedChildren = Generics.newLinkedList();
        sortedChildren.addAll(dependencies.get(gov).second);
        Collections.sort(sortedChildren);
        for (Integer dep : sortedChildren) {
          String word = dependencies.get(dep).first.word().toLowerCase();
          if (TokenUtils.isPunctuation(word))
            continue;
          if (alignment != null && alignment.e2f(gov - 1).isEmpty()) {
            /* Add a FRAG training example. */
            IString depToken = new IString(word); 
            IString headToken = FRAG_TOKEN;
            
            incrementHeadCount(depToken, headToken);
            incrementChildCount(depToken, START_TOKEN, headToken, ROOT_DIR_TOKEN);
            incrementChildCount(END_TOKEN, depToken, headToken, ROOT_DIR_TOKEN);
          } else {
            if (dep < gov) {
              leftChildren.add(new IString(word));
            } else {
              rightChildren.add(new IString(word));
            }
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
    String dependenciesFilename = PropertiesUtils.get(options, "input", null, String.class);
    String outdirPath = PropertiesUtils.get(options, "outdir", ".", String.class);
    String alignmentFilename = PropertiesUtils.get(options, "alignment", null, String.class);
    String sourceTokensFilename = PropertiesUtils.get(options, "sourceTokens", null, String.class);
    String targetTokensFilename = PropertiesUtils.get(options, "targetTokens", null, String.class);
    String rightDepLMFilename = outdirPath + File.separator + "deplm.nonevents";
    String leftDepLMFilename = outdirPath + File.separator + "deplm.data";
    String classMapFilename = PropertiesUtils.get(options, "classMap", null, String.class);
    
    if (classMapFilename != null) {
      System.err.println("Loading word class mapping from " + classMapFilename);
      classMap = new LocalWordClassMap();
      classMap.load(classMapFilename);
    } else {
      classMap = null;
    }
    
    /* Include alignment information and generate a "FRAG" tuple for each unaligned word instead of the real one. */
    boolean includeAlignment = (alignmentFilename != null && sourceTokensFilename != null);

    LineNumberReader alignmentReader = null;
    LineNumberReader sourceTokensReader = null;
    LineNumberReader targetTokensReader = null;

    
    if (includeAlignment) {
      alignmentReader = IOTools.getReaderFromFile(alignmentFilename);
      sourceTokensReader = IOTools.getReaderFromFile(sourceTokensFilename);
      targetTokensReader = IOTools.getReaderFromFile(targetTokensFilename);
    }
    
    
    File leftDepLMFile = new File(leftDepLMFilename);
    if (!leftDepLMFile.exists())
      leftDepLMFile.createNewFile();

    
    File rightDepLMFile = new File(rightDepLMFilename);
    if (!rightDepLMFile.exists())
      rightDepLMFile.createNewFile();
    
 
    

    FileWriter leftFW = new FileWriter(leftDepLMFile.getAbsoluteFile());
    FileWriter rightFW = new FileWriter(rightDepLMFile.getAbsoluteFile());
 
    
    lmWriter = new BufferedWriter(leftFW);
    noEventWriter = new BufferedWriter(rightFW);

    LineNumberReader inputReader = IOTools.getReaderFromFile(dependenciesFilename);

    
    HashMap<Integer, Pair<IndexedWord, List<Integer>>>  dependencies =  null;
    while ((dependencies =  DependencyUtils.getDependenciesFromCoNLLFileReader(inputReader, false, true)) != null) {
      
      SymmetricalWordAlignment alignment = null;
      
      if (includeAlignment) {
        alignment = new SymmetricalWordAlignment(sourceTokensReader.readLine(), targetTokensReader.readLine(), alignmentReader.readLine());
      }
      
      updateCounts(dependencies, alignment);
    }
    
    inputReader.close();
    lmWriter.close();
    noEventWriter.close();
    //headLmWriter.close();
    
  }

  private static class LocalWordClassMap extends AbstractWordClassMap {
    public LocalWordClassMap() {
      wordToClass = Generics.newHashMap();
    }
  }
  
  
}
