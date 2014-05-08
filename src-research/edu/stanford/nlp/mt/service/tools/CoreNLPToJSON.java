package edu.stanford.nlp.mt.service.tools;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import com.google.gson.Gson;

import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.InputProperties;
import edu.stanford.nlp.mt.process.en.EnglishPreprocessor;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

/**
 * Converts an input to JSON using StanfordCoreNLP.
 * 
 * TODO: Support languages other than English for which we have CoreNLP.
 * 
 * @author Spence Green
 *
 */
public final class CoreNLPToJSON {
  
  // Formatting commands
  private static final String CLOSE_LEFT = "cl";
  
  private static final Properties properties = new Properties();
  static {
    properties.put("annotators", "tokenize,ssplit,pos,lemma,ner,parse");
    properties.put("ssplit.eolonly", "true");
    properties.put("tokenize.options", "invertible=true,ptb3Escaping=false,asciiQuotes=true,splitAssimilations=false");
  }
  
  /**
   * Process an English text file.
   * 
   * @param args
   * @throws IOException 
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.printf("Usage: java %s file [inputproperties_str] > json_output%n", CoreNLPToJSON.class.getName());
      System.exit(-1);
    }
    String textFile = args[0];
    InputProperties inputProperties = args.length > 1 ? InputProperties.fromString(args[1]) : new InputProperties();

    StanfordCoreNLP coreNLP = new StanfordCoreNLP(properties);
    
    // Configure tokenizer
    EnglishPreprocessor preprocessor = new EnglishPreprocessor(true);
    
    // Use a map with ordered keys so that the output is ordered by segmentId.
    Map<Integer,SourceSegment> annotations = new TreeMap<Integer,SourceSegment>();
    LineNumberReader reader = IOTools.getReaderFromFile(textFile);
    for (String line; (line = reader.readLine()) != null;) {
      Annotation annotation = coreNLP.process(line);
      List<CoreMap> sentences = annotation.get(SentencesAnnotation.class);
      if (sentences.size() != 1) {
        throw new RuntimeException("Sentence splitting on line: " + String.valueOf(reader.getLineNumber()));
      }
      CoreMap sentence = sentences.get(0);
      Tree tree = sentence.get(TreeAnnotation.class);
      tree.indexLeaves();
      int[] chunkVector = getChunkVector(tree);
      List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
      int numTokens = tokens.size();
      SymmetricalWordAlignment alignment = preprocessor.processAndAlign(line);
      if (alignment.e().size() != numTokens) {
        throw new RuntimeException(String.format("Tokenizer configurations differ: %d/%d", alignment.e().size(), numTokens));
      }
      SourceSegment segment = new SourceSegment(numTokens);
      segment.layoutSpec.addAll(makeLayoutSpec(alignment));
      segment.inputProperties = inputProperties.toString();
      for (int j = 0; j < numTokens; ++j) {
        CoreLabel token = tokens.get(j);
        String word = token.get(TextAnnotation.class);
        segment.tokens.add(unescape(word));
        String pos = mapPOS(token.get(PartOfSpeechAnnotation.class));
        segment.pos.add(pos);
        String ne = token.get(NamedEntityTagAnnotation.class);
        segment.ner.add(ne);
        segment.chunkVector[j] = chunkVector[j];
      }
      annotations.put(reader.getLineNumber()-1, segment);
    }
    reader.close();
    System.err.printf("Processed %d sentences%n", reader.getLineNumber());
    
    final SourceDocument jsonDocument = new SourceDocument(textFile, annotations);
    
    // Convert to json
    Gson gson = new Gson();
    String json = gson.toJson(jsonDocument);
    System.out.println(json);
  }

  public static List<String> makeLayoutSpec(SymmetricalWordAlignment alignment) {
    List<String> formatSpec = Generics.newArrayList(alignment.eSize());
    int lastFIndex = -1;
    for (int j = 0, size = alignment.eSize(); j < size; ++j) {
      Set<Integer> e2fSet = alignment.e2f(j);
      if (e2fSet.size() != 1) {
        throw new RuntimeException();
      }
      int fIndex = e2fSet.iterator().next();
      formatSpec.add(fIndex == lastFIndex ? CLOSE_LEFT : "");
      lastFIndex = fIndex;
    }
    
    return formatSpec;
  }
  
  static String unescape(String word) {
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

  static void fillVectorWithYield(String[] vector, TregexMatcher tregexMatcher) {
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
  private static int[] getChunkVector(Tree tree) {
    String[] iobVector = new String[tree.yield().size()];
    Arrays.fill(iobVector, "O");
    
    // Yield patterns
//    TregexPattern baseNPPattern = TregexPattern.compile("@NP < (/NN/ < (__ !< __)) !< @NP");
    TregexPattern baseXPPattern = TregexPattern.compile("__ < (__ < (__ !< __)) !< (__ < (__ < __))");
    TregexPattern basePPPattern = TregexPattern.compile("@PP <, @IN !<< @NP >! @PP");
    TregexMatcher tregexMatcher = baseXPPattern.matcher(tree);
    fillVectorWithYield(iobVector, tregexMatcher);
    tregexMatcher = basePPPattern.matcher(tree);
    fillVectorWithYield(iobVector, tregexMatcher);
    
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
        if (index < 0 || index >= iobVector.length) {
          System.err.println("ERROR: Mangled subtree: " + match.toString());
          continue;
        }
        if (lastIndex > 0 && index - lastIndex != 1) break;
        if ( ! iobVector[index].equals("O")) break;
        iobVector[index] = seenStart ? "I" : "B";
        seenStart = true;
        lastIndex = index;
      }
    }
    int[] indexVector = iobToIndices(iobVector);
    return indexVector;
  }
  
  static int[] iobToIndices(String[] vector) {
    int[] indexVector = new int[vector.length];
    int chunkId = -1;
    for (int i = 0; i < indexVector.length; ++i) {
      String label = vector[i];
      if (label.equals("B") || label.equals("O")) {
        ++chunkId;
      }
      indexVector[i] = chunkId;
    }
    return indexVector;
  }
}
