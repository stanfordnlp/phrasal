package edu.stanford.nlp.mt.tools.deplm;

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
  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("annotations", 1);
    optionArgDefs.put("changepreps", 0);

    return optionArgDefs;
  }
  
  
  

  public static void main(String[] args) {

    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    String annotations = PropertiesUtils.get(options, "annotations", null, String.class);
    
    boolean changepreps = PropertiesUtils.getBool(options, "changepreps", false);
    
    int sentenceCount = CoreNLPCache.loadSerialized(annotations);
   
    
    CoreMap sentence;
    for (int i = 0; i < sentenceCount; i++) {
      try {  
        sentence = CoreNLPCache.get(i);
        if (sentence == null) {
          System.out.println();
          System.err.println("Empty sentence #" + i);
          continue;
        }
        printDependencies(sentence, changepreps);
        //System.err.println("---------------------------");
      } catch (Exception e) {
        System.err.println("SourceSentence #" + i);
        e.printStackTrace();
        return;
      }
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
        
        if (dep.second.equals("pobj") || dep.second.equals("pcomp")) {
          int origHead = dep.first;
          Pair<Integer, String> pdep = reverseDependencies.get(origHead);
          if (pdep == null)
            continue;
          dep.first  = pdep.first;
          pdep.first = idx;
          pdep.second = dep.second.equals("pobj") ? "case" : "mark";
          dep.second = dep.second.equals("pobj") ? "nmod" : "ccomp";
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

