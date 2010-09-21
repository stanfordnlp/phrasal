package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.base.MosesPhraseTable.IntArrayTranslationOption;

/**
 * @author Michel Galley
 */
public class MosesPhraseTableFilter {

  private static final int N_VARIABLE_MOSES_FEATURES = MosesPhraseTable.CANONICAL_FIVESCORE_SCORE_TYPES.length - 1;

  static class ScoredOpt implements Comparable<ScoredOpt> {
    TranslationOption<IString> opt;
    float score;
    ScoredOpt(TranslationOption<IString> opt, float score) {
      this.opt = opt; this.score = score;
    }
    @Override
    public int compareTo(ScoredOpt o) {
      return Double.compare(o.score, score);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.out
          .println("Usage:\n\tjava ...MosesPhraseTableFilter (options per input phrase) (input phrase table file) (output phrase table file)");
      System.exit(-1);
    }

    int nOpts = Integer.parseInt(args[0]);
    String inTable = args[1];
    String outTable = args[2];

    System.out.printf("Loading phrase table: %s\n", inTable);
    MosesPhraseTable<String> ppt = new MosesPhraseTable<String>(null, null, inTable);

    // Create Scorers:
    int nBits = N_VARIABLE_MOSES_FEATURES; // 3 -> 2
    int nScorers = (1 << nBits) - 1; // << 124 -1  = 3
    float[][] ws = new float[nScorers][];

    for (int mask=1; mask<=nScorers; ++mask) {
      float[] w = new float[nBits];
      for (int i=0; i<nBits; ++i) {
        w[i] = (1 & (mask >> i));
      }
      ws[mask-1] = w;
    }

    // Set of phrase pairs to keep:
    Set<TranslationOption<IString>> keptOptions = new HashSet<TranslationOption<IString>>();

    // Open output file:
    PrintStream oStream = IOTools.getWriterFromFile(outTable);

    for (int i=0; i<ppt.translations.size(); ++i) { // For each input phrase:

      keptOptions.clear(); 

      // Get foreign phrase:
      List<IntArrayTranslationOption> opts = ppt.translations.get(i);
      int[] foreignInts = MosesPhraseTable.foreignIndex.get(i);
      RawSequence<IString> rawForeign = new RawSequence<IString>(IStrings.toIStringArray(foreignInts));
      //System.err.printf("phrases for: %s\n", rawForeign);

      // Generate translation options:
      List<TranslationOption<IString>> transOpts = new ArrayList<TranslationOption<IString>>(opts.size());
      for (IntArrayTranslationOption intTransOpt : opts) {
        RawSequence<IString> translation = new RawSequence<IString>(
            intTransOpt.translation, IString.identityIndex());
        transOpts.add(new TranslationOption<IString>(intTransOpt.id, intTransOpt.scores,
            ppt.scoreNames, translation, rawForeign, intTransOpt.alignment));
      }

      for (float[] w : ws) {
        //System.err.printf("w=%s\n", Arrays.toString(w));
        // Score and sort:
        List<ScoredOpt> scoredTransOpts = new ArrayList<ScoredOpt>(transOpts.size());
        for (TranslationOption<IString> transOpt : transOpts)
          scoredTransOpts.add(new ScoredOpt(transOpt, (float) ArrayMath.innerProduct(transOpt.scores,w)));
        Collections.sort(scoredTransOpts);
        // Keep n-best:
        for (int j=0; j<scoredTransOpts.size() && j<nOpts; ++j) {
          //System.err.printf("keeping: %.3f %s\n", scoredTransOpts.get(j).score, scoredTransOpts.get(j).opt);
          keptOptions.add(scoredTransOpts.get(j).opt);
        }
      }

      // Print phrases to keep:
      System.err.printf("Translations for {%s}: %d -> %d\n", rawForeign, opts.size(), keptOptions.size());
      for (TranslationOption<IString> opt : keptOptions) {
        oStream.print(opt.foreign);
        oStream.print(" ||| ");
        oStream.print(opt.translation);
        oStream.print(" ||| ");
        oStream.print(opt.alignment.str);
        oStream.print(" ||| ");
        for (int j=0; j<opt.scores.length; ++j)
          oStream.print(opt.scores[j]);
        oStream.print("\n");
      }
    }
    oStream.close();
  }
}
