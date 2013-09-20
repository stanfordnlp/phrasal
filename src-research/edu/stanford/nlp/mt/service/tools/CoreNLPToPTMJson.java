package edu.stanford.nlp.mt.service.tools;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;

import com.google.gson.Gson;

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
import edu.stanford.nlp.util.Generics;

/**
 * Converts CoreNLP annotations to the PTM source side 
 * format, which is in json.
 * 
 * @author Spence Green
 *
 */
public final class CoreNLPToPTMJson {

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.printf("Usage: java %s corenlp_ser_gz > json_output%n", CoreNLPToPTMJson.class.getName());
      System.exit(-1);
    }

    String annotationFile = args[0];
    Annotation document = null;
    try {
      document = (Annotation) IOUtils.readObjectFromFile(annotationFile);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.err.println("Loaded " + annotationFile);

    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
    List<AnnotationContainer> annotations = Generics.newLinkedList();
    for (CoreMap sentence : sentences) {
      Tree tree = sentence.get(TreeAnnotation.class);
      tree.indexLeaves();
      BitSet isBaseNPToken = markBaseNPs(tree);
      AnnotationContainer container = new AnnotationContainer();
      int i = 0;
      for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
        String word = token.get(TextAnnotation.class);
        container.tokens.add(word);
        String pos = token.get(PartOfSpeechAnnotation.class);
        container.pos.add(pos);
        String ne = token.get(NamedEntityTagAnnotation.class);
        container.ner.add(ne);
        container.isBaseNP.add(isBaseNPToken.get(i++));
      }
      annotations.add(container);
    }
    System.err.printf("Processed %d sentences%n", sentences.size());
    
    // Convert to json
    Gson gson = new Gson();
    String json = gson.toJson(annotations);
    System.out.println(json);
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
  
  private static class AnnotationContainer {
    public final List<String> tokens;
    public final List<String> pos;
    public final List<String> ner;
    public final List<Boolean> isBaseNP; 
    public AnnotationContainer() {
      this.tokens = Generics.newLinkedList();
      this.pos = Generics.newLinkedList();
      this.ner = Generics.newLinkedList();
      this.isBaseNP = Generics.newLinkedList();
    }
  }
}
