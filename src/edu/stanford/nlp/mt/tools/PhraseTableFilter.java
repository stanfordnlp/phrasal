package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.base.FlatPhraseTable.IntArrayTranslationOption;

/**
 * @author Michel Galley
 */
public class PhraseTableFilter {

  private static final int N_VARIABLE_MOSES_FEATURES = 4;

  static class ScoredOpt implements Comparable<ScoredOpt> {

    final Rule<IString> opt;
    final float score;

    ScoredOpt(Rule<IString> opt, float score) {
      this.opt = opt;
      this.score = score;
    }

    @Override
    public int compareTo(ScoredOpt o) {
      return Double.compare(o.score, score);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.out
          .println("Usage:\n\tjava ...PhraseTableFilter (options per input phrase) (input phrase table file) (output phrase table file)");
      System.exit(-1);
    }

    int nOpts = Integer.parseInt(args[0]);
    String inTable = args[1];
    String outTable = args[2];

    System.out.printf("Loading phrase table: %s\n", inTable);
    FlatPhraseTable<String> ppt = new FlatPhraseTable<String>(null, inTable);

    // Create Scorers:
    int nBits = N_VARIABLE_MOSES_FEATURES;
    int nScorers = (1 << nBits) - 1;
    float[][] ws = new float[nScorers][];

    for (int mask=1; mask<=nScorers; ++mask) {
      float[] w = new float[nBits];
      for (int i=0; i<nBits; ++i) {
        w[i] = (1 & (mask >> i));
      }
      ws[mask-1] = w;
    }

    // Set of phrase pairs to keep:
    Set<Rule<IString>> keptOptions = new HashSet<Rule<IString>>();

    // Open output file:
    PrintStream oStream = IOTools.getWriterFromFile(outTable);

    for (int pi=0; pi<ppt.translations.size(); ++pi) { // For each input phrase:

      keptOptions.clear(); 

      // Get foreign phrase:
      List<IntArrayTranslationOption> opts = ppt.translations.get(pi);
      int[] foreignInts = FlatPhraseTable.foreignIndex.get(pi);
      RawSequence<IString> rawForeign = new RawSequence<IString>(IStrings.toIStringArray(foreignInts));

      // Generate translation options:
      List<Rule<IString>> transOpts = new ArrayList<Rule<IString>>(opts.size());
      for (IntArrayTranslationOption intTransOpt : opts) {
        RawSequence<IString> translation = new RawSequence<IString>(
            intTransOpt.translation, IString.identityIndex());
        transOpts.add(new Rule<IString>(intTransOpt.id, intTransOpt.scores,
            ppt.scoreNames, translation, rawForeign, intTransOpt.alignment));
      }

      // Score translation options:
      if (transOpts.size() <= nOpts) {
        for (Rule<IString> transOpt : transOpts)
          keptOptions.add(transOpt);
      } else {
        for (float[] w : ws) {
          // Score and sort:
          List<ScoredOpt> scoredTransOpts = new ArrayList<ScoredOpt>(transOpts.size());
          for (Rule<IString> transOpt : transOpts)
            scoredTransOpts.add(new ScoredOpt(transOpt, (float) ArrayMath.innerProduct(w, transOpt.scores)));
          Collections.sort(scoredTransOpts);
          // Keep n-best:
          //System.err.println("sorting with: "+ Arrays.toString(w));
          for (int j=0; j<scoredTransOpts.size() && j<nOpts; ++j) {
            //System.err.printf("keep: %.3f %s", scoredTransOpts.get(j).score, scoredTransOpts.get(j).opt);
            keptOptions.add(scoredTransOpts.get(j).opt);
          }
        }
      }

      // Print phrases to keep:
      System.err.printf("Translations for {%s}: %d -> %d\n", rawForeign, opts.size(), keptOptions.size());
      for (Rule<IString> opt : keptOptions) {
        oStream.print(opt.source);
        oStream.print(" ||| ");
        oStream.print(opt.target);
        oStream.print(" ||| ");
        oStream.print(opt.alignment.s2tStr());
        oStream.print(" ||| ");
        oStream.print(opt.alignment.t2sStr());
        oStream.print(" ||| ");
        for (int j=0; j<opt.scores.length; ++j) {
          if (j>0) oStream.print(" ");
          oStream.print(opt.scores[j]);
        }
        oStream.print(" \n");
      }
    }
    oStream.close();
  }
}
