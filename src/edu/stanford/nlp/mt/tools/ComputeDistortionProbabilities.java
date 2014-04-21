package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class ComputeDistortionProbabilities {

  private static int parseFileIndex = 0;
  private static int parseSentenceIndex = 0;
  private static TwoDimensionalCounter<String, Integer> wordDistortionCounter = new TwoDimensionalCounter<String, Integer>(); 
  private static TwoDimensionalCounter<String, Integer> posDistortionCounter = new TwoDimensionalCounter<String, Integer>(); 

  
  
  
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
    return optionArgDefs;
  }
  
  public static void getDistortionCounts(CoreMap annotation, SymmetricalWordAlignment alignment) {
    
    Tree parseTree = annotation.get(TreeAnnotation.class);
  
    List<Label> posTags = parseTree.preTerminalYield();
    List<Label> tokens = parseTree.yield();

    int size = posTags.size();
    for (int i = 0; i < size; i++) {
      SortedSet<Integer> a = alignment.f2e(i);
      if (!a.isEmpty()) {
        int first = a.first();
        wordDistortionCounter.incrementCount(tokens.get(i).value(), first - i);
        posDistortionCounter.incrementCount(posTags.get(i).value(), first - i);

      }
    }
    
  

    
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
    File sourceSentences = new File(sourceTokens);
    File targetSentences = new File(targetTokens);
    File alignmentFile = new File(alignments);
    BufferedReader sourceReader = new BufferedReader(new FileReader(sourceSentences));
    BufferedReader targetReader = new BufferedReader(new FileReader(targetSentences));
    BufferedReader alignmentReader = new BufferedReader(new FileReader(alignmentFile));
    String sourceSentence;
    int i = 0;
    while ((sourceSentence = sourceReader.readLine()) != null) {
      try {
        CoreMap sentence = getParsedSentence(annotations, i, annotationsSplit);
        String targetSentence = targetReader.readLine();
        String alignmentString = alignmentReader.readLine();
        //System.err.println("---------------------------");
        //System.err.println(sourceSentence);
        //System.err.println(targetSentence);
        //System.err.println(alignmentString);
        SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(sourceSentence, targetSentence, alignmentString);
        getDistortionCounts(sentence, alignment);
      } catch (Exception e) {
        e.printStackTrace();
      }
      i++;
    }
        

    
    sourceReader.close();
    targetReader.close();
    alignmentReader.close();

    
    File posOut = new File("pos.distortion.out");
    File wordOut = new File("word.distortion.out");
    if (!posOut.exists()) posOut.createNewFile();
    if (!wordOut.exists()) wordOut.createNewFile();
    
    FileWriter posFW = new FileWriter(posOut.getAbsoluteFile());
    BufferedWriter posBW = new BufferedWriter(posFW);

    
    for (String k : posDistortionCounter.firstKeySet()) {
      Counter<Integer> c = posDistortionCounter.getCounter(k);
      Counters.normalize(c);
    }
    
    for (String k1 : posDistortionCounter.firstKeySet()) {
      Counter<Integer> c = posDistortionCounter.getCounter(k1);
      for (Integer k2 : c.keySet()) {
        posBW.write(k1 + " " + k2 + " " + c.getCount(k2) + "\n");
      }
    }
    
    posBW.close();

    FileWriter wordFW = new FileWriter(wordOut.getAbsoluteFile());
    BufferedWriter wordBW = new BufferedWriter(wordFW);

    for (String k : wordDistortionCounter.firstKeySet()) {
      Counter<Integer> c = wordDistortionCounter.getCounter(k);
      Counters.normalize(c);
    }
    
    for (String k1 : wordDistortionCounter.firstKeySet()) {
      Counter<Integer> c = wordDistortionCounter.getCounter(k1);
      for (Integer k2 : c.keySet()) {
        wordBW.write(k1 + " " + k2 + " " + c.getCount(k2) + "\n");
      }
    }
    
    wordBW.close();
  }

}
