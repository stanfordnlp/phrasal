package edu.stanford.nlp.mt.preordering;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.mt.preordering.ClauseTypeLabeller.Clause;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class RuleBasedGermanPreorderer {

  private static int parseFileIndex = 0;
  private static int parseSentenceIndex = 0;

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
  
  
  /**
   * Command-line option specification.
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("annotations", 1); 
    optionArgDefs.put("annotationsSplit", 0); 
    optionArgDefs.put("permutations", 0); 


    return optionArgDefs;
  }
  
  
  public static void main(String[] args) throws ClassNotFoundException, IOException {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());

    ClauseTypeLabeller labeller = new ClauseTypeLabeller();
    String annotations = PropertiesUtils.get(options, "annotations", null, String.class);
    if (annotations == null)
      return;
    boolean annotationsSplit = PropertiesUtils.getBool(options, "annotationsSplit", false);
    boolean outputPermutations = PropertiesUtils.getBool(options, "permutations", false);

    int i = 0;
    CoreMap sentence = null;
    while ((sentence = getParsedSentence(annotations, i, annotationsSplit)) != null 
        || getParsedSentence(annotations, i+1, annotationsSplit) != null) {
        
      //for (CoreLabel t: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
      //  System.out.print(t.word() + " ");
      //}
      //System.out.print("\n");
      //System.err.print("Reordered sentence #");
      //System.err.println(i);
      
      //In case of an empty line sentence will be null.
      if (sentence != null) {
        Tree parseTree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        //System.out.println(parseTree.toString());
        HashMap<String, Clause> clauses = labeller.labelTree(parseTree);
        Clause c1 = clauses.get("--C-1");
        if (c1 != null){
          try {
            System.out.println(c1.preorder(clauses));
          } catch (Exception e) {
            e.printStackTrace();
            List<Tree> gloss = parseTree.getLeaves();
            for (Tree t : gloss) {
              System.out.print(t.label().value());
              System.out.print(" ");
            }
            System.out.println("");
          }
        } else {
          List<Tree> gloss = parseTree.getLeaves();
          for (Tree t : gloss) {
            System.out.print(t.label().value());
            System.out.print(" ");
          }
          System.out.println("");
        }
          
      } else 
        System.out.println("");
      i++;
    }
  }
  
}
