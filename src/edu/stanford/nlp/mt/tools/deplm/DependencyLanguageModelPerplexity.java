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
  
  
  
  public static double scoreTree(HashMap<Integer, Pair<String, List<Integer>>> dependencies) {
    
    double score = 0.0;
    
    for (int gov : dependencies.keySet()) {
      if (gov == 0) {
        for (Integer dep : dependencies.get(gov).second) {
          score += rootLm.score(new SimpleSequence<IString>(new IString(dependencies.get(dep).first)), 0, null).getScore();
        }
      } else {
        List<IString> leftChildren = Generics.newLinkedList();
        List<IString> rightChildren = Generics.newLinkedList();

        rightChildren.add(new IString(dependencies.get(gov).first + "<HEAD>"));

        
        List<Integer> sortedChildren = Generics.newLinkedList();
        sortedChildren.addAll(dependencies.get(gov).second);
        Collections.sort(sortedChildren);
        for (Integer dep : sortedChildren) {
          if (dep < gov) {
            leftChildren.add(new IString(dependencies.get(gov).first));
          } else {
            rightChildren.add(new IString(dependencies.get(gov).first));
          }
        }
        
        Collections.reverse(leftChildren);
        leftChildren.add(0, new IString(dependencies.get(gov).first + "<HEAD>"));
        
        Sequence<IString> leftSequence = new SimpleSequence<IString>(leftChildren);
        leftSequence = Sequences.wrapStartEnd(leftSequence, leftLm.getStartToken(), leftLm.getEndToken());
        Sequence<IString> rightSequence = new SimpleSequence<IString>(rightChildren);
        leftSequence = Sequences.wrapStartEnd(leftSequence, rightLm.getStartToken(), rightLm.getEndToken());

        score += leftLm.score(leftSequence, 0, null).getScore();
        score += rightLm.score(rightSequence, 0, null).getScore();
      }
    }
    
    
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

    //train LM:
    // - on entire bitext
    // - only on good alignments (try 0 1 2 3 4 5)
    // - 
    //READ conll sentence
    //score according to dependency scoring scheme
    //try with and without start/end tokens
    
    
    LineNumberReader reader = IOTools.getReaderFromFile(args[3]);
    
    HashMap<Integer, Pair<String, List<Integer>>> dependencies;
    
    double logSum = 0.0;
    final long startTimeMillis = System.nanoTime();
    
    while ((dependencies = DependencyUtils.getDependenciesFromCoNLLFileReader(reader)) != null) {
      final double score = scoreTree(dependencies);
      assert score != 0.0;
      assert ! Double.isNaN(score);
      assert ! Double.isInfinite(score);
      logSum += score;
    }
    
    reader.close();
    System.out.printf("Log sum score: %e%n", logSum);
        
    double elapsed = (System.nanoTime() - startTimeMillis) / 1e9;
    System.err.printf("Elapsed time: %.3fs%n", elapsed);
  }
}
