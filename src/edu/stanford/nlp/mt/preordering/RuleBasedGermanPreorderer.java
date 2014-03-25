package edu.stanford.nlp.mt.preordering;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.base.CoreNLPCache;
import edu.stanford.nlp.mt.preordering.ClauseTypeLabeller.Clause;
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
        
    
      //In case of an empty line sentence will be null.
      if (sentence != null) {
        Tree parseTree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        List<Tree> originalGloss = parseTree.getLeaves();
        StringBuffer osb = new StringBuffer();
        {
          boolean first = true;
          for (Tree t : originalGloss) {
            if (!first)
              osb.append(" ");
            else
              first = false;
            CoreLabel cl = (CoreLabel) t.label();
            if (outputPermutations)
              osb.append(cl.index());
            else
              osb.append(cl.value());
          }
        }
        
        HashMap<String, Clause> clauses = labeller.labelTree(parseTree);                
        try {
          StringBuffer sb = new StringBuffer();
          List<Tree> gloss = parseTree.getLeaves();
          boolean first = true;
          for (Tree t : gloss) {
            if (!first) 
              sb.append(" ");
            else
              first = false;
            String w = t.label().value();
            if (w.startsWith("--C-")) {
              Clause c = clauses.get(w);
              sb.append(c.preorder(clauses, outputPermutations));
            } else {
              if (outputPermutations) {
                CoreLabel cl = (CoreLabel) t.label();
                sb.append(cl.index());
              } else {
                sb.append(w);
              }
            }
            
          }
          System.out.println(sb.toString());
        } catch (Exception e) {
          e.printStackTrace();
          System.err.println("Error caused by: \n" + osb.toString());

          System.out.println(osb.toString());
        }
      } else 
        System.out.println("");
      i++;
    }
  }
  
}
