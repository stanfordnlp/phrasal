package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;

/**
 * Evaluate the perplexity of an input file under a language model.
 * 
 * @author danielcer
 * @author Spence Green
 *
 */
public final class LanguageModelPerplexity {
  
  private LanguageModelPerplexity() {}

  /**
   * 
   * @param args
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err
          .printf("Usage: java %s type:path [input_file] < input_file%n", LanguageModelPerplexity.class.getName());
      System.exit(-1);
    }

    String model = args[0];
    System.out.printf("Loading lm: %s...%n", model);
    LanguageModel<IString> lm = LanguageModelFactory.load(model);

    LineNumberReader reader = (args.length == 1) ? 
        new LineNumberReader(new InputStreamReader(System.in)) :
          IOTools.getReaderFromFile(args[1]);
    
    double logSum = 0;
    final long startTimeMillis = System.nanoTime();
    for (String sent; (sent = reader.readLine()) != null;) {
      Sequence<IString> seq = IStrings.tokenize(sent);
      Sequence<IString> paddedSequence = Sequences.wrapStartEnd(seq, lm.getStartToken(),
          lm.getEndToken());
      final double score = lm.score(paddedSequence, 1, null).getScore();
      assert score != 0.0;
      assert ! Double.isNaN(score);
      assert ! Double.isInfinite(score);
      
      logSum += score;
      
      System.out.println("Sentence: " + sent);
      System.out.printf("Sequence score: %f score_log10: %f%n", score, score
          / Math.log(10));
    }
    reader.close();
    System.out.printf("Log sum score: %e%n", logSum);
        
    double elapsed = (System.nanoTime() - startTimeMillis) / 1e9;
    System.err.printf("Elapsed time: %.3fs%n", elapsed);
  }
}
