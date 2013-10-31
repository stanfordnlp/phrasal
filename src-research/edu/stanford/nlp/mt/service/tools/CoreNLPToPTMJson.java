package edu.stanford.nlp.mt.service.tools;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
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
    // Use a map with ordered keys so that the output is ordered by segmentId.
    Map<Integer,AnnotationContainer> annotations = new TreeMap<Integer,AnnotationContainer>();
    for (int i = 0; i < sentences.size(); ++i) {
      CoreMap sentence = sentences.get(i);
      Tree tree = sentence.get(TreeAnnotation.class);
      tree.indexLeaves();
      String[] chunkVector = getChunkVector(tree);
      AnnotationContainer container = new AnnotationContainer();
      List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
      for (int j = 0; j < tokens.size(); ++j) {
        CoreLabel token = tokens.get(j);
        String word = token.get(TextAnnotation.class);
        container.tokens.add(unescape(word));
        String pos = mapPOS(token.get(PartOfSpeechAnnotation.class));
        container.pos.add(pos);
        String ne = token.get(NamedEntityTagAnnotation.class);
        container.ner.add(ne);
        container.chunkIOB.add(chunkVector[j]);
      }
      annotations.put(i, container);
    }
    System.err.printf("Processed %d sentences%n", sentences.size());
    
    final Document jsonDocument = new Document(annotationFile, annotations);
    
    // Convert to json
    Gson gson = new Gson();
    String json = gson.toJson(jsonDocument);
    System.out.println(json);
  }

  private static String unescape(String word) {
    if (word.equals("-LRB-")) {
      return "(";
    } else if (word.equals("-RRB-")) {
      return ")";
    }
    return word;
  }

  /**
   * Map PTB tags to reduced form.
   * 
   * TODO(spenceg): Maybe this is too coarse. Could load Petrov's universal POS
   * set or some thing to that effect.
   * 
   * @param posTag
   * @return
   */
  private static String mapPOS(String posTag) {
    if (posTag.startsWith("NN")) {
      return "N";
    } else if (posTag.startsWith("VB")) {
      return "V";
    } else if (posTag.startsWith("JJ")) {
      return "A";
    } else if (posTag.startsWith("RB")) {
      return "ADV";
    }
    return "O";
  }

  private static void fillVectorWithYield(String[] vector, TregexMatcher tregexMatcher) {
    while (tregexMatcher.find()) {
      Tree match = tregexMatcher.getMatch();
      List<Tree> leaves = match.getLeaves();
      if (leaves.size() == 1) continue;
      boolean seenStart = false;
      for (Tree leaf : leaves) {
        int index = ((HasIndex) leaf.label()).index() - 1;
        if ( ! vector[index].equals("O")) break;
        vector[index] = seenStart ? "I" : "B";
        seenStart = true;
      }
    }
  }
  
  /**
   * Extract chunks. 
   * 
   * @param tree
   * @return
   */
  private static String[] getChunkVector(Tree tree) {
    String[] vector = new String[tree.yield().size()];
    Arrays.fill(vector, "O");
    
    // Yield patterns
//    TregexPattern baseNPPattern = TregexPattern.compile("@NP < (/NN/ < (__ !< __)) !< @NP");
    TregexPattern baseXPPattern = TregexPattern.compile("__ < (__ < (__ !< __)) !< (__ < (__ < __))");
    TregexPattern basePPPattern = TregexPattern.compile("@PP <, @IN !<< @NP >! @PP");
    TregexMatcher tregexMatcher = baseXPPattern.matcher(tree);
    fillVectorWithYield(vector, tregexMatcher);
    tregexMatcher = basePPPattern.matcher(tree);
    fillVectorWithYield(vector, tregexMatcher);
    
    // Edge patterns
    TregexPattern vpPattern = TregexPattern.compile("@VP >! @VP");
    TregexPattern argumentPattern = TregexPattern.compile("!@VP=node > @VP !< (__ !< __)");
    TregexPattern puncPattern = TregexPattern.compile("/^[^a-zA-Z0-9]+$/=node < __ ");
    TsurgeonPattern p = Tsurgeon.parseOperation("delete node");
    tregexMatcher = vpPattern.matcher(tree);
    while (tregexMatcher.find()) {
      Tree match = tregexMatcher.getMatch();
      Tsurgeon.processPattern(argumentPattern, p, match);
      Tsurgeon.processPattern(puncPattern, p, match);
      List<Tree> leaves = match.getLeaves();
      if (leaves.size() == 1) continue;
      boolean seenStart = false;
      int lastIndex = -1;
      for (Tree leaf : leaves) {
        int index = ((HasIndex) leaf.label()).index() - 1;
        if (lastIndex > 0 && index - lastIndex != 1) break;
        if ( ! vector[index].equals("O")) break;
        vector[index] = seenStart ? "I" : "B";
        seenStart = true;
        lastIndex = index;
      }
    }
    return vector;
  }
  
  private static class Document {
    // Name of this document
    public final String docId;
    // Annotated segments, indicated with a name
    public final Map<Integer,AnnotationContainer> segments;
    public Document(String docId, Map<Integer,AnnotationContainer> segments) {
      this.docId = docId;
      this.segments = segments;
    }
  }
  
  private static class AnnotationContainer {
    public final List<String> tokens;
    public final List<String> pos;
    public final List<String> ner;
    public final List<String> chunkIOB; 
    public AnnotationContainer() {
      this.tokens = Generics.newLinkedList();
      this.pos = Generics.newLinkedList();
      this.ner = Generics.newLinkedList();
      this.chunkIOB = Generics.newLinkedList();
    }
  }
}
