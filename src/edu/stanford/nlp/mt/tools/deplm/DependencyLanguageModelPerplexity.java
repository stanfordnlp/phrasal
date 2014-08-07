package edu.stanford.nlp.mt.tools.deplm;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Evaluate the perplexity of an input file under a language model.
 * 
 * @author danielcer
 * @author Spence Green
 * @author Sebastian Schuster
 *
 */
public final class DependencyLanguageModelPerplexity {
  
  private static LanguageModel<IString> leftLm;
  private static LanguageModel<IString> rightLm;
  private static LanguageModel<IString> rootLm;
  
  private static String HEAD_SUFFIX = "<HEAD>";
  private static String ROOT_SUFFIX = "<ROOT>";
  private static String FRAG_SUFFIX = "<FRAG>";

  static int wordCount = 0;
  
  public static double scoreTree(HashMap<Integer, Pair<String, List<Integer>>> dependencies) {
    
    double score = 0.0;
    
    for (int gov : dependencies.keySet()) {
      
      String headWord = dependencies.get(gov).first;
      if (headWord != null && !DependencyUtils.isWord(headWord))
        continue;
      
      
      if (gov < 1) {
        for (Integer dep : dependencies.get(gov).second) {
          String word = dependencies.get(dep).first.toLowerCase();
          if (!DependencyUtils.isWord(word))
            continue;
          String suffix = gov == 0 ? ROOT_SUFFIX : FRAG_SUFFIX;
          Sequence<IString> seq = new SimpleSequence<IString>(new IString(word + suffix));
          seq = Sequences.wrapStartEnd(seq, rootLm.getStartToken(), rootLm.getEndToken());
          score += rootLm.score(seq, 1, null).getScore();
          wordCount += seq.size() - 1;
          System.err.println("DEBUG: Scoring head" + seq.toString());
        }
      } else {
        List<IString> leftChildren = Generics.newLinkedList();
        List<IString> rightChildren = Generics.newLinkedList();

        
        
        rightChildren.add(new IString(headWord.toLowerCase() + HEAD_SUFFIX));

        
        List<Integer> sortedChildren = Generics.newLinkedList();
        sortedChildren.addAll(dependencies.get(gov).second);
        Collections.sort(sortedChildren);
        for (Integer dep : sortedChildren) {
          String word = dependencies.get(dep).first.toLowerCase();
          if (!DependencyUtils.isWord(word))
            continue;
          if (dep < gov) {
            leftChildren.add(new IString(word));
          } else {
            rightChildren.add(new IString(word));
          }
        }
        
        Collections.reverse(leftChildren);
        leftChildren.add(0, new IString(headWord.toLowerCase() + HEAD_SUFFIX));
        
        Sequence<IString> leftSequence = new SimpleSequence<IString>(leftChildren);
        leftSequence = Sequences.wrapStartEnd(leftSequence, leftLm.getStartToken(), leftLm.getEndToken());
        Sequence<IString> rightSequence = new SimpleSequence<IString>(rightChildren);
        rightSequence = Sequences.wrapStartEnd(rightSequence, rightLm.getStartToken(), rightLm.getEndToken());

        score += leftLm.score(leftSequence, 2, null).getScore();
        wordCount += leftSequence.size() - 2;
        System.err.println("DEBUG: Scoring left: " + leftSequence.toString());
        score += rightLm.score(rightSequence, 2, null).getScore();
        wordCount += rightSequence.size() - 2;
        System.err.println("DEBUG: Scoring right: " + rightSequence.toString());
      }
    }
    
    //System.err.println("DEBUG: Wordcount " + wordCount);

    return score;
  }
  
  /**
   * 
   * @param args
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 4) {
      System.err
          .printf("Usage: java %s type:path_leftlm type:path_righlm type:path_rootlm input_file.conll%n", DependencyLanguageModelPerplexity.class.getName());
      System.exit(-1);
    }

    String leftModel = args[0];
    String rightModel = args[1];
    String rootModel = args[2];
    
    System.out.printf("Loading left lm: %s...%n", leftModel);
    leftLm = LanguageModelFactory.load(leftModel);

    System.out.printf("Loading right lm: %s...%n", rightModel);
    rightLm = LanguageModelFactory.load(rightModel);
    
    System.out.printf("Loading root lm: %s...%n", rootModel);
    rootLm = LanguageModelFactory.load(rootModel);

 
    
    
    LineNumberReader reader = IOTools.getReaderFromFile(args[3]);
    
    HashMap<Integer, Pair<String, List<Integer>>> dependencies;
    
    double logSum = 0.0;
    final long startTimeMillis = System.nanoTime();
    
    while ((dependencies = DependencyUtils.getDependenciesFromCoNLLFileReader(reader, false)) != null) {
      final double score = scoreTree(dependencies);
      assert score != 0.0;
      assert ! Double.isNaN(score);
      assert ! Double.isInfinite(score);
      logSum += score;
    }
    
    logSum = logSum / Math.log10(2.0);
    
    reader.close();
    System.out.printf("Word count: %d%n", wordCount);
    System.out.printf("Log sum score: %e%n", logSum / wordCount);
    System.out.printf("Perplexity: %e%n", Math.pow(2.0, -logSum / wordCount));
        
    double elapsed = (System.nanoTime() - startTimeMillis) / 1e9;
    System.err.printf("Elapsed time: %.3fs%n", elapsed);
  }
}
