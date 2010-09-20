package edu.stanford.nlp.mt.syntax.hiero;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

import edu.stanford.nlp.mt.syntax.hiero.HieroGrammarScorer_Hashtable.Rule;

import edu.stanford.nlp.mt.syntax.util.Alignment;
import edu.stanford.nlp.mt.syntax.util.FileUtility;

/* Zhifei Li, <zhifei.work@gmail.com>
 * Johns Hopkins University
 */
//TODO: (1) may ignore flat phrases in extract_rules; (2) the accummulation of the first feat; 
//(3) the accumulation of other feats; (4) weight calculation; (5) ignore phrase whose lexical weght is zero 

@SuppressWarnings({ "unchecked", "unused" })
public class HieroExtractor {

  public static int INVALID_POS = -1;
  public static int INVALID_WRD_ID = -1;// TODO must be different from terminal
                                        // and non-terminal symbols

  public static String NULL_ALIGN_WRD_SYM = "NULL";
  public static int NULL_ALIGN_WRD_SYM_ID = 0;

  public static String NON_TERMINAL_TAG_SYM = "PHRASE";// tag for [PHRASE] or
                                                       // [X]
  public static int NON_TERMINAL_TAG_SYM_ID = 0;// tag for [PHRASE] or [X]

  public static int max_init_phrase_size = 10;
  public static int max_final_phrase_size = 5;
  public static int min_sub_phrase_size = 2;
  public static int max_num_non_terminals = 2;

  public static String file_align = "";
  public static String file_zh = "";
  public static String file_en = "";
  public static String dir_grammar_out = "";

  public static String file_f2e_lexical_weights = "";
  public static String file_e2f_lexical_weights = "";

  public static Boolean allow_non_lexicial_rules = false;
  public static Boolean forbid_adjacent_nonterminals = true;// in french
  public static Boolean require_aligned_terminal = true;

  public static Boolean use_tight_phrase = true;

  public static Boolean remove_overlap_phrases = false;
  public static Boolean keep_alignment_infor = false;

  public static HashMap fweights_table;
  public static HashMap eweights_table;
  public static float[] fweights; // sentence-specific dynamical array
  public static float[] eweights; // sentence-specific dynamical array
  public static float[] fratios; // sentence-specific dynamical array

  public static int g_num_init_phrases = 0;
  public static int g_num_rules_and_phrases = 0;

  public static void main(String[] args) {
    // init symbol
    edu.stanford.nlp.mt.syntax.decoder.Symbol.add_global_symbols(true);
    NULL_ALIGN_WRD_SYM_ID = edu.stanford.nlp.mt.syntax.decoder.Symbol
        .add_terminal_symbol(NULL_ALIGN_WRD_SYM);
    NON_TERMINAL_TAG_SYM_ID = edu.stanford.nlp.mt.syntax.decoder.Symbol
        .add_non_terminal_symbol(NON_TERMINAL_TAG_SYM);

    // read weights files
    eweights_table = read_weight_file("C:\\data_disk\\java_work_space\\SyntaxMT\\mt.syntax.train.hiero\\lex.f2e.gz");
    fweights_table = read_weight_file("C:\\data_disk\\java_work_space\\SyntaxMT\\mt.syntax.train.hiero\\lex.e2f.gz");

    HieroGrammarScorer_Hashtable p_gram;
    if (fweights_table == null)
      p_gram = new HieroGrammarScorer_Hashtable(2);
    else
      p_gram = new HieroGrammarScorer_Hashtable(4);

    BufferedReader t_reader_tree = FileUtility
        .getReadFileStream(
            "C:\\data_disk\\java_work_space\\SyntaxMT\\mt.syntax.train.hiero\\parse.sync.berkeley1",
            "UTF8");
    // BufferedReader t_reader_tree =
    // FileUtility.getReadFileStream(args[1].trim(),"UTF8");

    BufferedReader t_reader_align = FileUtility
        .getReadFileStream(
            "C:\\data_disk\\java_work_space\\SyntaxMT\\mt.syntax.train.hiero\\aligned.ibm",
            "UTF8");
    // BufferedReader t_reader_tree =
    // FileUtility.getReadFileStream(args[1].trim(),"UTF8");

    BufferedReader t_reader_zh = FileUtility
        .getReadFileStream(
            "C:\\data_disk\\java_work_space\\SyntaxMT\\mt.syntax.train.hiero\\aligned.zh",
            "UTF8");
    // BufferedReader t_reader_tree =
    // FileUtility.getReadFileStream(args[1].trim(),"UTF8");

    BufferedReader t_reader_en = FileUtility
        .getReadFileStream(
            "C:\\data_disk\\java_work_space\\SyntaxMT\\mt.syntax.train.hiero\\aligned.en.tmp1",
            "UTF8");
    // BufferedReader t_reader_tree =
    // FileUtility.getReadFileStream(args[1].trim(),"UTF8");
    String line_align, line_fr, line_en;
    int n_line = 1;
    while ((line_align = FileUtility.read_line_lzf(t_reader_align)) != null) {
      line_fr = FileUtility.read_line_lzf(t_reader_zh);
      line_en = FileUtility.read_line_lzf(t_reader_en);
      // if(n_line++==1)continue;
      ArrayList p_rules_and_phrases = process_a_sentence(line_align, line_fr,
          line_en);
      if (p_rules_and_phrases != null) {// write into the grammar
        for (int t = 0; t < p_rules_and_phrases.size(); t++)
          p_gram.add_raw_rule((Rule) p_rules_and_phrases.get(t));
      }
      if (n_line >= 50)
        break;
      n_line++;
    }
    System.out.println("total lines: " + n_line + "; total ini phrases  "
        + g_num_init_phrases + "; total rules and phrases  "
        + g_num_rules_and_phrases);
    p_gram.score_grammar();
  }

  private static HashMap read_weight_file(String file) {
    // BufferedReader t_reader_tree =
    // FileUtility.getReadFileStream("C:\\data_disk\\java_work_space\\SyntaxMT\\mt.syntax.train.hiero\\parse.sync.berkeley1","UTF8");
    BufferedReader t_reader = FileUtility.getReadFileStream(file, "UTF8");
    HashMap res = new HashMap();
    String line;
    int n = 0;
    while ((line = FileUtility.read_line_lzf(t_reader)) != null) {
      n++;
      if (n % 500000 == 0)
        System.out.println("reading lines " + n);
      String[] fds = line.split("\\s+");// format: wrd1 wrd2 weight
      int id1 = edu.stanford.nlp.mt.syntax.decoder.Symbol
          .add_terminal_symbol(fds[0]);
      int id2 = edu.stanford.nlp.mt.syntax.decoder.Symbol
          .add_terminal_symbol(fds[1]);
      res.put(form_weight_key(id1, id2), new Double(fds[2]));
    }
    FileUtility.close_read_file(t_reader);
    return res;
  }

  private static String form_weight_key(int id1, int id2) {
    StringBuffer res = new StringBuffer();
    res.append(id1);
    res.append("-");
    res.append(id2);
    return res.toString();
  }

  private static double get_weight_from_matrix(HashMap weights_maxtrix, int f,
      int e) {
    String key = form_weight_key(f, e);
    return weights_maxtrix.containsKey(key) ? (Double) weights_maxtrix.get(key)
        : 0;
  }

  private static ArrayList process_a_sentence(String line_align,
      String line_fr, String line_en) {
    // #### create alignment datastructure
    Alignment align = new Alignment(line_fr, line_en, line_align);

    // #### compute weights
    if (fweights_table != null)
      fweights = compute_lexical_weights(align, fweights_table, false, false);
    if (eweights_table != null)
      eweights = compute_lexical_weights(align, eweights_table, true, false);// transpose

    // #### exphrase phrases
    int[] actual_max_init_phrase_size = new int[1];
    ArrayList l_init_phrases = extract_phrases(align, max_init_phrase_size,
        actual_max_init_phrase_size);// extract regular flat phrases;
    if (l_init_phrases.size() == 0) {
      System.out.println("warning: no init phrases are extracted");
      return null;
    }
    // #### test-set specific filtering, the extract_phrases can use
    // suffix-array architecture

    if (use_tight_phrase == false)
      loosen_phrases();

    if (remove_overlap_phrases == true)
      remove_overlap_phrases();

    // #### create index ??
    // #### add label to the flat phrases
    // done in extract_phrases, if we want to implement loosen_phrases() and
    // remove_overlap_phrases(), we should put it here

    // #### extract rules: the l_init_phrases is a list of phrases from a
    // specific training sentence-pair
    ArrayList l_rules_and_phrases = extract_rules(align, l_init_phrases,
        max_final_phrase_size, min_sub_phrase_size, max_num_non_terminals,
        actual_max_init_phrase_size[0]);

    return l_rules_and_phrases;
  }

  // sentence-specific
  // for each word, calculate the lexicalized weight based on the alighment and
  // the lexical weight tables
  private static float[] compute_lexical_weights(Alignment align,
      HashMap weights_maxtrix, Boolean transpose, Boolean swap) {
    int[] fwords, ewords, faligned;
    if (transpose == false) {
      fwords = align.french_wrds;
      ewords = align.english_wrds;
      faligned = align.num_alignments_infor_for_french;
    } else {
      fwords = align.english_wrds;
      ewords = align.french_wrds;
      faligned = align.num_alignments_infor_for_english;
    }
    float[] results = new float[fwords.length];
    for (int i = 0; i < fwords.length; i++) {
      float total = 0;
      int n = 0;
      if (faligned[i] > 0) {
        for (int j = 0; j < ewords.length; j++) {
          int flag;
          if (transpose == false)
            flag = align.alignment_matrix[i][j];
          else
            flag = align.alignment_matrix[j][i];
          if (flag == 1) {// aligned
            if (swap == false)
              total += get_weight_from_matrix(weights_maxtrix, fwords[i],
                  ewords[j]);
            else
              total += get_weight_from_matrix(weights_maxtrix, ewords[j],
                  fwords[i]);
            n++;
          }
        }
      } else {// unaligned
        if (swap == false)
          total += get_weight_from_matrix(weights_maxtrix, fwords[i],
              NULL_ALIGN_WRD_SYM_ID);
        else
          total += get_weight_from_matrix(weights_maxtrix,
              NULL_ALIGN_WRD_SYM_ID, fwords[i]);
        n++;
      }
      results[i] = total / n;
    }

    System.out.println("weights are ");
    for (int t = 0; t < results.length; t++)
      System.out.print(results[t]);
    System.out.print("\n");

    return results;
  }

  // extract flat phrases: i1,i2,j1,j2; who are the positions in the french and
  // english sentences
  private static ArrayList extract_phrases(Alignment align,
      int max_init_phrase_size, int[] actual_max_init_phrase_size) {
    actual_max_init_phrase_size[0] = 0;
    ArrayList l_phrases = new ArrayList();
    int i1, i2, j1, j2;// french span: [i1,i2]; eng span: [j1,j2]
    for (i1 = 0; i1 < align.french_wrds.length; i1++) {
      if (align.num_alignments_infor_for_french[i1] <= 0)
        continue;// skip unaligned wrd
      j1 = align.english_wrds.length;
      j2 = -1;
      for (i2 = i1; i2 < Alignment.min(i1 + max_init_phrase_size,
          align.french_wrds.length); i2++) {
        if (align.num_alignments_infor_for_french[i2] <= 0)
          continue;// skip unaligned wrd

        // j1 and j2: [j1,j2] is the "maximum" (thoug may be in-consistent) eng
        // span for the french span [i1,i2]
        j1 = Alignment.min(j1, align.min_pos_infor_for_french[i2]);
        j2 = Alignment.max(j2, align.max_pos_infor_for_french[i2]);

        if (j1 > j2)
          continue;// empty english span

        if (j2 - j1 + 1 > max_init_phrase_size)
          break; // go to next i1, since adding more wrds in french will
                 // increase the eng span

        int flag = 0;
        for (int j = j1; j <= j2; j++) {// for each english wrd in [j1,j2]
          if (align.min_pos_infor_for_english[j] < i1) {// must extend i1 to
                                                        // solve inconsistence
            flag = 1; // next i1
            break;
          }
          if (align.max_pos_infor_for_english[j] > i2) {// fix i1, but extending
                                                        // i2 may solve this
                                                        // inconsistence
            flag = 2; // next i2
            break;
          }
        }
        if (flag == 1)
          break; // next i1
        if (flag == 2)
          continue;// next i2

        // add the phrase
        l_phrases.add(new int[] { i1, i2, j1, j2, NON_TERMINAL_TAG_SYM_ID });
        actual_max_init_phrase_size[0] = Alignment.max(
            actual_max_init_phrase_size[0], i2 - i1 + 1);
      }
    }
    g_num_init_phrases += l_phrases.size();
    return l_phrases;
  }

  private static void loosen_phrases() {
    System.out.println("Error: un-implemented function");
    System.exit(0);
  }

  private static void remove_overlap_phrases() {
    System.out.println("Error: un-implemented function");
    System.exit(0);
  }

  // extract hiearchical rules
  private static ArrayList extract_rules(Alignment align,
      ArrayList l_init_phrases, int max_final_phrase_size,
      int min_sub_phrase_size, int max_num_non_terminals,
      int actual_max_init_phrase_size) {
    if (l_init_phrases.size() == 0) {
      System.out.println("warning: no flat phrases are extracted");
      return null;
    }
    int n = align.english_wrds.length;
    ArrayList[][][] bins = new ArrayList[n + 1][n + 1][max_num_non_terminals + 1];// bins[i][j]
                                                                                  // spans
                                                                                  // the
                                                                                  // french
                                                                                  // [i,j-1],
                                                                                  // each
                                                                                  // bin
                                                                                  // is
                                                                                  // a
                                                                                  // list
                                                                                  // of
                                                                                  // items
    ArrayList[] i2index = new ArrayList[n];// each is a list of init-phrases
                                           // ending with i2
    Hashtable i1s = new Hashtable();

    // get i2index and i1s
    for (int t = 0; t < l_init_phrases.size(); t++) {
      int[] phrase = (int[]) l_init_phrases.get(t);
      print_phrase(align, phrase);
      int t_i1 = phrase[0], t_i2 = phrase[1];
      if (i2index[t_i2] == null)
        i2index[t_i2] = new ArrayList();
      i2index[t_i2].add(phrase);
      if (i1s.containsKey(t_i1) == false) {
        i1s.put(t_i1, 1);
        // chart seeding
        bins[t_i1][t_i1][0] = new ArrayList();
        bins[t_i1][t_i1][0].add(new ArrayList());// add empty item
      }
    }
    System.out.println("num of init phrases is " + l_init_phrases.size()
        + "; i1s len: " + i1s.size() + "; french len "
        + align.french_wrds.length + " maxabslen; "
        + actual_max_init_phrase_size);
    // chart parsing: each item is an arraylist
    int loop1 = 0;
    int loop2 = 0;
    for (int k = 1; k <= Alignment.min(n, actual_max_init_phrase_size); k++) {
      loop1++;
      loop2 = 0;
      for (int i1 = 0; i1 + k <= n; i1++) {
        if (i1s.containsKey(i1) == false)
          continue;// because the phrases never start from this index; bug: this
                   // may skip the flat phrase
        loop2++;
        int i2 = i1 + k - 1;
        // extend the dot by a subphrase
        int tem1 = 0, tem2 = 0;
        if (i2index[i2] != null) {
          for (int t = 0; t < i2index[i2].size(); t++) {// for all sub-phrases
                                                        // ending at i2
            int[] sub_phrase = (int[]) i2index[i2].get(t);
            if (sub_phrase[1] - sub_phrase[0] + 1 >= min_sub_phrase_size) {
              for (int n_nts = 0; n_nts < max_num_non_terminals; n_nts++) {
                if (bins[i1][sub_phrase[0]][n_nts] != null) {// no ant-items
                  for (int t2 = 0; t2 < bins[i1][sub_phrase[0]][n_nts].size(); t2++) {// for
                                                                                      // all
                                                                                      // ant-items
                    ArrayList item = (ArrayList) bins[i1][sub_phrase[0]][n_nts]
                        .get(t2);
                    if (item.size() < max_final_phrase_size
                        && !(forbid_adjacent_nonterminals && item.size() > 0 && !(item
                            .get(item.size() - 1) instanceof Integer))) {
                      ArrayList new_item = new ArrayList(item);
                      new_item.add(sub_phrase);
                      if (bins[i1][i2 + 1][n_nts + 1] == null)
                        bins[i1][i2 + 1][n_nts + 1] = new ArrayList();
                      bins[i1][i2 + 1][n_nts + 1].add(new_item);
                      tem1++;
                    }
                  }
                }
              }
            }
          }
        }
        // extend the dot by a wrd
        for (int n_nts = 0; n_nts <= max_num_non_terminals; n_nts++) {
          if (bins[i1][i2][n_nts] != null) {
            for (int t2 = 0; t2 < bins[i1][i2][n_nts].size(); t2++) {
              ArrayList item = (ArrayList) bins[i1][i2][n_nts].get(t2);
              if (item.size() < max_final_phrase_size) {
                ArrayList new_item = new ArrayList(item);
                new_item.add(i2);
                if (bins[i1][i2 + 1][n_nts] == null)
                  bins[i1][i2 + 1][n_nts] = new ArrayList();
                bins[i1][i2 + 1][n_nts].add(new_item);
                tem2++;
              }
            }
          }
        }
        // tem3++;
        // System.out.println(loop1 + " "+ loop2 +
        // " number of subphrases textend is " + tem1 + " and " + tem2);
      }
    }

    // extract rules from the chart
    ArrayList l_phrases_and_rules = new ArrayList();
    for (int t = 0; t < l_init_phrases.size(); t++) {
      ArrayList local_results = new ArrayList();
      int[] phrase = (int[]) l_init_phrases.get(t);
      for (int n_nts = 0; n_nts <= max_num_non_terminals; n_nts++) {
        if (bins[phrase[0]][phrase[1] + 1][n_nts] != null) {
          for (int t2 = 0; t2 < bins[phrase[0]][phrase[1] + 1][n_nts].size(); t2++) {
            ArrayList item = (ArrayList) bins[phrase[0]][phrase[1] + 1][n_nts]
                .get(t2);
            Rule rule = make_and_score_rule(align, phrase, item);
            if (rule != null) {
              local_results.add(rule);
            }
          }
        }
      }
      // distribute the count, normalization
      for (int k = 0; k < local_results.size(); k++) {
        Rule rl = (Rule) local_results.get(k);
        for (int f = 0; f < rl.feat_scores.length; f++)
          rl.feat_scores[f] /= local_results.size();
        rl.print_info();
      }
      l_phrases_and_rules.addAll(local_results);
      // System.out.println("local size " + local_results.size() + " all size "
      // + l_phrases_and_rules.size());
    }
    System.out.println("num of rules and phrases is "
        + l_phrases_and_rules.size());
    g_num_rules_and_phrases += l_phrases_and_rules.size();
    return l_phrases_and_rules;
  }

  private static Rule make_and_score_rule(Alignment align, int[] phrase,
      ArrayList item) {
    // ignore ??
    if (item.size() == 1 && !(item.get(0) instanceof Integer))
      return null; // bug: this may skip the flat phrase

    int nt_index = 1;
    boolean have_alignment = false;

    int original_en_len = phrase[3] - phrase[2] + 1;
    // System.out.println("original en len " + original_en_len);
    // System.out.println("item is " + item.toString());
    int[] original_en_wrds = new int[original_en_len];
    for (int t = 0; t < original_en_len; t++)
      original_en_wrds[t] = align.english_wrds[t + phrase[2]];

    // get french words in the rule
    int[] rule_fwords = new int[item.size()];
    int[] fpos = new int[item.size()];// remember the original position in the
                                      // sentence
    for (int t = 0; t < item.size(); t++) {
      // System.out.println("size is " + item.size());
      if (item.get(t) instanceof Integer) {// terminal
        // System.out.println("terminal");
        fpos[t] = (Integer) item.get(t);
        if (align.num_alignments_infor_for_french[fpos[t]] > 0) {
          have_alignment = true;
        }
        rule_fwords[t] = align.french_wrds[fpos[t]];
      } else {// non-terminal
        // System.out.println("non-terminal");
        fpos[t] = INVALID_POS;
        int[] sub_phrase = (int[]) item.get(t);
        original_en_len -= sub_phrase[3] - sub_phrase[2];// reserved one slot
                                                         // for the NT symbol
        // System.out.println("e span: " + sub_phrase[0] +" - " + sub_phrase[1]
        // +" ; len " + original_en_len);
        int nt = edu.stanford.nlp.mt.syntax.decoder.Symbol
            .add_non_terminal_symbol(NON_TERMINAL_TAG_SYM + "," + nt_index);// get
                                                                            // [PHRASE,nt_index]
        rule_fwords[t] = nt;
        original_en_wrds[sub_phrase[2] - phrase[2]] = nt;
        for (int k = sub_phrase[2] - phrase[2] + 1; k <= sub_phrase[3]
            - phrase[2]; k++)
          original_en_wrds[k] = INVALID_WRD_ID;
        nt_index++;
      }
    }

    if (require_aligned_terminal && have_alignment == false)
      return null;

    // get English words in the rule
    int[] rule_ewords = new int[original_en_len];// original_en_len is changed
                                                 // to actual en len
    int[] epos = new int[original_en_len];
    for (int t = 0, k = 0; t < original_en_wrds.length; t++) {
      if (original_en_wrds[t] != INVALID_WRD_ID) {
        rule_ewords[k] = original_en_wrds[t];
        if (edu.stanford.nlp.mt.syntax.decoder.Symbol
            .is_nonterminal(original_en_wrds[t]) == true)
          epos[k] = INVALID_POS;
        else
          epos[k] = phrase[2] + t;
        k++;
      }
    }

    // create rule
    Rule rl = new Rule(phrase[4], rule_fwords, rule_ewords);

    // add alignment infor
    if (keep_alignment_infor == true) {
      ArrayList align_info = new ArrayList();
      for (int i = 0; i < fpos.length; i++)
        if (fpos[i] != INVALID_POS)
          for (int j = 0; j < epos.length; j++)
            if (epos[j] != INVALID_POS)
              if (align.alignment_matrix[fpos[i]][epos[j]] == 1)
                align_info.add(i + "-" + j);// add "i-j"
      rl.alignments = align_info;
    }

    score_rule(align, rl, fpos, epos);
    return rl;
  }

  // compute and add feat scores
  private static Rule score_rule(Alignment align, Rule r_in, int[] fpos,
      int[] epos) {
    int funaligned = 0, eunaligned = 0;
    float fweight = 1, eweight = 1, fratio = 0;

    // P_lex(eng|fr)
    for (int t = 0; t < r_in.french.length; t++) {
      if (edu.stanford.nlp.mt.syntax.decoder.Symbol
          .is_nonterminal(r_in.french[t]) == false) {
        if (align.num_alignments_infor_for_french[fpos[t]] <= 0)
          funaligned++;
        if (fweights != null)
          fweight *= fweights[fpos[t]];
        if (fratios != null)
          fratio += fratios[fpos[t]];
      }
    }

    // P_lex(fr|eng)
    for (int t = 0; t < r_in.english.length; t++) {
      if (edu.stanford.nlp.mt.syntax.decoder.Symbol
          .is_nonterminal(r_in.english[t]) == false) {
        if (align.num_alignments_infor_for_english[epos[t]] <= 0)
          eunaligned++;
        if (eweights != null)
          eweight *= eweights[epos[t]];
      }
    }

    // add the feat scores
    int num_feats = 1;
    if (fweights != null)
      num_feats++;
    if (eweights != null)
      num_feats++;
    if (fratios != null)
      num_feats++;
    float[] scores = new float[num_feats];
    int t_id = 0;
    scores[t_id++] = 1.0f;
    if (fweights != null)
      scores[t_id++] = fweight;
    if (eweights != null)
      scores[t_id++] = eweight;
    ;
    if (fratios != null)
      scores[t_id++] = fratio;
    ;
    r_in.feat_scores = scores;
    return r_in;
  }

  private static void print_phrase(Alignment align, int[] phrase) {
    String str = "zh: " + phrase[0] + "-" + phrase[1];
    for (int t = phrase[0]; t <= phrase[1]; t++)
      str += " "
          + edu.stanford.nlp.mt.syntax.decoder.Symbol
              .get_string(align.french_wrds[t]);
    str += " en: " + phrase[2] + "-" + phrase[3];
    for (int t = phrase[2]; t <= phrase[3]; t++)
      str += " "
          + edu.stanford.nlp.mt.syntax.decoder.Symbol
              .get_string(align.english_wrds[t]);
    str += " nt: "
        + edu.stanford.nlp.mt.syntax.decoder.Symbol.get_string(phrase[4]);
    System.out.println("phrase is = " + str);
  }

}
