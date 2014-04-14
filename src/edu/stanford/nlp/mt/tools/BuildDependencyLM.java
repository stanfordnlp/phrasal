package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;

import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class BuildDependencyLM {

  private static int parseFileIndex = 0;
  private static int parseSentenceIndex = 0;

  private static TwoDimensionalCounter<String, String> dependencyCounter = new TwoDimensionalCounter<String, String>(); 
  
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
  
  public static void projectSentence(CoreMap annotation, SymmetricalWordAlignment alignment) {
    SemanticGraph semanticGraph = annotation.get(BasicDependenciesAnnotation.class);
    
    Collection<TypedDependency> dependencies = semanticGraph.typedDependencies();
    
    for (TypedDependency dep : dependencies) {
      SortedSet<Integer> depIds = alignment.f2e(dep.dep().index() - 1);
      if (dep.gov().index() > 0) {
        SortedSet<Integer> govIds = alignment.f2e(dep.gov().index() - 1);
        if (govIds.size() > 0 && depIds.size() > 0) {
          String head = alignment.e().get(govIds.last()).word();
          String dependent = alignment.e().get(depIds.last()).word();
          dependencyCounter.incrementCount(head, dependent);

        }
      } else if (depIds.size() > 0) {
        String dependent = alignment.e().get(depIds.last()).word();
        dependencyCounter.incrementCount("ROOT", dependent);
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
        projectSentence(sentence, alignment);
      } catch (Exception e) {
        e.printStackTrace();
      }
      i++;
    }
        
    for (String k : dependencyCounter.firstKeySet()) {
      Counter<String> c = dependencyCounter.getCounter(k);
      Counters.logInPlace(c);
      Counters.logNormalizeInPlace(c);
    }
    
    for (String k1 : dependencyCounter.firstKeySet()) {
      Counter<String> c = dependencyCounter.getCounter(k1);
      for (String k2 : c.keySet()) {
        System.out.println(k1 + " " + k2 + " " + c.getCount(k2));
      }
    }
    
    sourceReader.close();
    targetReader.close();
    alignmentReader.close();


  }

}
