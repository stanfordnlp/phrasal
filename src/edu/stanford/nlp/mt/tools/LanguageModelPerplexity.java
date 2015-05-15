package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.TimingUtils;

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
    if (args.length != 2) {
      System.err
          .printf("Usage: java %s type:path input_file%n", LanguageModelPerplexity.class.getName());
      System.exit(-1);
    }

    String model = args[0];
    System.out.printf("Loading lm: %s...%n", model);
    LanguageModel<IString> lm = LanguageModelFactory.load(model);

    String infile = args[1];
    List<Sequence<IString>> lines = IStrings.tokenizeFile(infile).stream()
        .map(s -> Sequences.wrapStartEnd(s, lm.getStartToken(),
        lm.getEndToken())).collect(Collectors.toList());
    
    int numQueries = 0;
    double logSum = 0.0;
    final long startTime = TimingUtils.startTime();
    for (Sequence<IString> seq : lines) {
      final double score = lm.score(seq, 1, null).getScore();
      numQueries += seq.size() - 1;
      assert score != 0.0;
      assert ! Double.isNaN(score);
      assert ! Double.isInfinite(score);

      logSum += score;
    }
    final double elapsedTime = TimingUtils.elapsedSeconds(startTime);

    System.out.printf("Log sum score: %.3f%n", logSum);
    System.out.printf("Log2 Perplexity: %.3f%n", Math.pow(2.0, -logSum / Math.log(2.0) / numQueries));
    System.out.printf("# segments: %d%n", lines.size());
    System.out.printf("# queries: %d%n", numQueries);
    System.out.printf("queries / sec: %.2f%n", numQueries / elapsedTime);
    System.err.printf("Elapsed time: %.5fs%n", elapsedTime);
  }
}
