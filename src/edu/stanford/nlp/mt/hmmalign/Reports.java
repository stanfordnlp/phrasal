package mt.hmmalign;


/**
 * A class that implements some methods of general utility for all models
 * similar to reports.cc in GIZA
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.PrintStream;


public class Reports {

  public Reports() {
  }


  public static void printAlignToFile(SentencePair sent, PrintStream of2, int[] viterbi_alignment, int pair_no, double alignment_score)

          // prints the given alignment to alignments file (given it stream pointer)
          // in a format recognizable by the draw-alignment tool ... which is of the
          // example (each line triple is one sentence pair):
          //   # sentence caption
          //   target_word_1 target_word_2  ..... target_word_m
          //   source_word_1 ({ x y z }) source_word_2 ({ })  .. source_word_n ({w})
          // where x, y, z, and w are positions of target words that each source word
          // is connected to.
  {
    int l, m;

    // of zero or more translations .
    l = sent.e.getLength() - 1;
    m = sent.f.getLength() - 1;
    of2.println("# Sentence pair (" + pair_no + ") source length " + l + " target length " + m);
    for (int j = 1; j <= m; j++) {
      of2.print(sent.f.getWord(j).toNameStringF() + " " + "(" + j + ")" + " ");
    }
    of2.println();

    for (int i = 0; i <= l; i++) { // these loops are not efficient
      of2.print(sent.e.getWord(i).toNameStringE() + " ({ ");
      for (int j = 1; j <= m; j++) {
        if ((viterbi_alignment[j] == i) || ((i == 0) && viterbi_alignment[j] > l)) {
          //for (WordIndex j = 0 ; j < translations[i].size() ; j++)
          of2.print(j + " ");
        }
      }
      of2.print("}) ");
    }
    of2.println();
  }


}
