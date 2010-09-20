package edu.stanford.nlp.mt.syntax.tune;

import java.util.HashMap;

import edu.stanford.nlp.mt.syntax.decoder.Chart;
import edu.stanford.nlp.mt.syntax.decoder.HyperGraph.Item;

/* Zhifei Li, <zhifei.work@gmail.com>
 * Johns Hopkins University
 */

//extract oracle tree from the hypergraph
//TODO assumption: we assume the LM order used for generating the hypergraph is >= order of the BLEU
@SuppressWarnings({ "unchecked", "unused" })
public class OracleExtractor {
  // int[] ref_sentence;//reference string (not tree)
  private static int src_sent_len = 0;
  private static int ref_sent_len = 0;
  private static HashMap tbl_ref_ngrams = new HashMap();

  private static int g_lm_order = 4;
  private static int g_bleu_order = 4;

  // key: item; value: best_deduction, best_bleu, best_len, # of n-gram match
  // where n is in [1,4]
  private static HashMap tbl_oracle_states = new HashMap();
  private static int num_elements_in_state = 3 + g_bleu_order;
  private static int STATE_BEST_DEDUCT = 0;
  private static int STATE_BEST_BLEU = 1;
  private static int STATE_BEST_LEN = 2;
  private static int STATE_START_NGRAM_NUM = 3;// the start position to store
                                               // the number of ngram mathes

  public static void oracle_decode(int lm_order, int src_len, int[] ref_sent,
      Chart p_chart, Item goal_item) {
    // TODO
  }

}
