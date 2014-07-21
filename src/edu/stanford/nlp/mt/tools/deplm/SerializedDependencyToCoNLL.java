package edu.stanford.nlp.mt.tools.deplm;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class SerializedDependencyToCoNLL {
  
  private static int parseFileIndex = 0;
  private static int parseSentenceIndex = 0;

  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("annotations", 1);
    optionArgDefs.put("changepreps", 0);

    return optionArgDefs;
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
  

  public static void main(String[] args) {

    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    String annotations = PropertiesUtils.get(options, "annotations", null, String.class);
    
    boolean changepreps = PropertiesUtils.getBool(options, "changepreps", false);
    
   
    CoreMap sentence;
    int i = 0;
    while ((sentence = getParsedSentence(annotations, i, false)) != null) {
      try {     
        printDependencies(sentence, changepreps);
        //System.err.println("---------------------------");
      } catch (Exception e) {
        System.err.println("SourceSentence #" + i);
        e.printStackTrace();
        return;
      }
      i++;
    }
    
  }

  private static void printDependencies(CoreMap annotation, boolean changepreps) {
    
    SemanticGraph semanticGraph = annotation.get(BasicDependenciesAnnotation.class);
    Collection<TypedDependency> dependencies = semanticGraph.typedDependencies();
    HashMap<Integer, Pair<Integer, String>> reverseDependencies = new HashMap<Integer, Pair<Integer, String>>() ;
    
    
    for (TypedDependency dep : dependencies) {
      int govIndex = dep.gov().index();
      int depIndex = dep.dep().index();
      reverseDependencies.put(depIndex, Generics.newPair(govIndex, dep.reln().getShortName()));
    }
    
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);

    // Change the treatment of prepositions according to the
    // Universal Stanford Dependencies standard
    if (changepreps) {
      for (int idx  = 1; idx <= tokens.size(); idx++) {
        Pair<Integer, String> dep = reverseDependencies.get(idx);
        if (dep == null)
          continue;
        
        if (dep.second.equals("pobj")) {
          int origHead = dep.first;
          Pair<Integer, String> pdep = reverseDependencies.get(origHead);
          dep.first  = pdep.first;
          dep.second = "nmod";
          pdep.first = idx;
          pdep.second = "case";
        }
      }
    }

    for (int idx  = 1; idx <= tokens.size(); idx++) {
      CoreLabel token = tokens.get(idx-1);
      System.out.print(idx); //index
      System.out.print("\t");
      System.out.print(token.word()); //form
      System.out.print("\t");  
      System.out.print("_"); //lemma
      System.out.print("\t"); 
      System.out.print(token.tag()); //pos tag
      System.out.print("\t");
      System.out.print(token.tag()); //pos tag
      System.out.print("\t");
      System.out.print("_"); //feats
      System.out.print("\t");
      if (reverseDependencies.get(idx) != null) {
        int gov = reverseDependencies.get(idx).first;
        String rel = reverseDependencies.get(idx).second;
        System.out.print(gov); //head
        System.out.print("\t");
        System.out.print(rel); //DEPREL
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
    System.out.println("");

  }
  
}

