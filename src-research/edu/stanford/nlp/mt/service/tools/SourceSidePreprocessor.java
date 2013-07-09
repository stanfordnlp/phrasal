package edu.stanford.nlp.mt.service.tools;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;

/**
 * Converts English CoreNLP annotations to the PTM source side 
 * format.
 * 
 * @author Spence Green
 *
 */
public final class SourceSidePreprocessor {

  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.printf("Usage: java %s corenlp_ser_gz%n", SourceSidePreprocessor.class.getName());
      System.exit(-1);
    }
    
    String annotationFile = args[0];
    Annotation document = null;
    try {
      document = (Annotation) IOUtils.readObjectFromFile(annotationFile);
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    System.err.println("Loaded " + annotationFile);
    
    System.out.println("index\ttoken\tpos\tne\tis_np");
    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      // Get the parse tree
      Tree tree = sentence.get(TreeAnnotation.class);
      tree.indexLeaves();
      
      BitSet isBaseNPToken = markBaseNPs(tree);
      int i = 0;
      for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
        String word = token.get(TextAnnotation.class);
        String pos = token.get(PartOfSpeechAnnotation.class);
        String ne = token.get(NamedEntityTagAnnotation.class);
        System.out.printf("%d\t%s\t%s\t%s\t%b%n", i, word,pos,ne,isBaseNPToken.get(i));
        ++i;
      }
      System.out.println();
    }
    System.err.printf("Processed %d sentences%n", sentences.size());
  }

  private static BitSet markBaseNPs(Tree tree) {
    TregexPattern baseNPMatcher = TregexPattern.compile("@NP < (/NN/ < (__ !< __)) !< @NP");
    
    TregexMatcher tregexMatcher = baseNPMatcher.matcher(tree);
    BitSet b = new BitSet();
    while (tregexMatcher.find()) {
      Tree match = tregexMatcher.getMatch();
      List<Tree> leaves = match.getLeaves();
      for (Tree leaf : leaves) {
        b.set(((HasIndex) leaf.label()).index() - 1);
      }
    }
      
    return b;
  }

}
