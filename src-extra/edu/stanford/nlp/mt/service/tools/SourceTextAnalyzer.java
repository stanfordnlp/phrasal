package edu.stanford.nlp.mt.service.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.CollinsHeadFinder;
import edu.stanford.nlp.trees.Dependency;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.UnnamedConcreteDependency;
import edu.stanford.nlp.util.CoreMap;

/**
 * Source text statistics used in the CHI-13 analysis.
 * 
 * @author Spence Green
 *
 */
public final class SourceTextAnalyzer {

  // Setup CoreNLP
  private static final Properties props = new Properties();
  static {
    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
    props.put("ssplit.eolonly", "true");
  }
  private static StanfordCoreNLP pipeline = null;

  private static CoreMap annotate(String text) {
    Annotation document = new Annotation(text);
    pipeline.annotate(document);
    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
    assert sentences.size() == 1;
    return sentences.get(0);
  }

  /**
   * Syntactic complexity as defined by Lin (1996).
   * 
   * @param tree
   * @return
   */
  private static int complexityOf(Tree tree) {
    tree.indexLeaves();
    tree.percolateHeads(new CollinsHeadFinder());
    tree.percolateHeadIndices();
    
    Set<Dependency<Label,Label,Object>> deps = tree.dependencies();
    int complexity = 0;
    for (Dependency<Label,Label,Object> dep : deps) {
      if (!(dep instanceof UnnamedConcreteDependency)) {
        throw new RuntimeException("Cannot measure syntactic complexity.");
      }
      UnnamedConcreteDependency uDep = (UnnamedConcreteDependency) dep;
      int headIndex = uDep.getGovernorIndex();
      int depIndex = uDep.getDependentIndex();
      complexity += Math.abs(headIndex - depIndex);
    }
    
    return complexity;
  }

  // private
  private SourceTextAnalyzer() {}

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.printf("Usage: java %s source_file%n", SourceTextAnalyzer.class.getName());
      System.exit(-1);
    }

    // Load the models after verifying the command line arguments
    pipeline = new StanfordCoreNLP(props);
    
    final String sourceFile = args[0];
    final String tokOutFile = sourceFile + ".tokens.tsv";
    final String sentOutFile = sourceFile + ".sentence.tsv";

    int lineId = 0;
    try {
      PrintWriter pwTokens = new PrintWriter(new PrintStream(new FileOutputStream(tokOutFile),false,"UTF-8"));
      PrintWriter pwSentences = new PrintWriter(new PrintStream(new FileOutputStream(sentOutFile),false,"UTF-8"));
      try (LineNumberReader br = IOTools.getReaderFromFile(sourceFile)) {
        for (String sentence; (sentence = br.readLine()) != null;) {
          CoreMap annotatedSentence = annotate(sentence);
          // Zero-indexing
          lineId = br.getLineNumber() - 1;
          
          // Per token features
          int tokenId = 0;
          int numNETokens = 0;
          Counter<String> posCounter = new ClassicCounter<String>();
          for (CoreLabel token : annotatedSentence.get(TokensAnnotation.class)) {
            String word = token.get(TextAnnotation.class);
            String pos = token.get(PartOfSpeechAnnotation.class);
            posCounter.incrementCount(pos);
            String ne = token.get(NamedEntityTagAnnotation.class);
            if (! ne.equals("O")) ++numNETokens;
            pwTokens.printf("%d,%d,%s,%s%n", lineId, tokenId, word, pos);
            ++tokenId;
          }

          // Per sentence features
          Tree tree = annotatedSentence.get(TreeAnnotation.class);
          int score = complexityOf(tree);

          StringBuilder sb = new StringBuilder();
          for (String pos : posCounter.keySet()) {
            sb.append(String.format("%s:%d ", pos, (int) posCounter.getCount(pos)));
          }

          pwSentences.printf("%d,%d,%d,%s%n", lineId, score, numNETokens, sb.toString().trim());
        }
      }
      pwTokens.close();
      pwSentences.close();

    } catch (IOException e) {
      System.err.println("Error at line: " + String.valueOf(lineId));
      e.printStackTrace();
    }
    
    System.out.printf("Processed %d sentences%n", lineId);
  }
}

