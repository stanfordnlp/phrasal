package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.objectbank.ObjectBank;

import java.io.*;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.StringTokenizer;

import edu.stanford.nlp.lm.SWIGTYPE_p_Ngram;
import edu.stanford.nlp.lm.SWIGTYPE_p_Vocab;
import edu.stanford.nlp.lm.SWIGTYPE_p_unsigned_int;
import edu.stanford.nlp.lm.srilm;

/**
 * Language model class using SRILM native code.
 * 
 * @author Michel Galley
 */
public class SRILanguageModel implements LanguageModel<IString> {

  public static boolean addVocabToIStrings = false;

  private static final int lm_start_sym_id = 11; // 1-10 reserved for special
                                                 // symbols
  private static final int lm_end_sym_id = 5000001; // max vocab 16M

  static {
    System.loadLibrary("srilm");
  }

  private static final int MAX_NGRAM_ORDER = 16;
  private static final ThreadLocal<SWIGTYPE_p_unsigned_int> threadHist = new ThreadLocal<SWIGTYPE_p_unsigned_int>() {
    @Override
    protected SWIGTYPE_p_unsigned_int initialValue() {
      SWIGTYPE_p_unsigned_int p = srilm.new_unsigned_array(MAX_NGRAM_ORDER + 1);
      for (int i = 0; i <= MAX_NGRAM_ORDER; ++i) {
        srilm.unsigned_array_setitem(p, i, srilm.getVocab_None());
      }
      return p;
    }
  };

  static boolean verbose = false;

  protected final String name;

  private static final IString START_TOKEN = new IString("<s>");
  private static final IString END_TOKEN = new IString("</s>");
  private static int NONE_TOKEN;
  private static final double LOG10 = Math.log(10);
  private final SWIGTYPE_p_Ngram p_srilm;
  private final SWIGTYPE_p_Vocab p_vocab;
  private int[] ids;

  private final int order;

  public String getName() {
    return name;
  }

  public IString getStartToken() {
    return START_TOKEN;
  }

  public IString getEndToken() {
    return END_TOKEN;
  }

  protected static final WeakHashMap<String, LanguageModel<IString>> lmStore = new WeakHashMap<String, LanguageModel<IString>>();

  public static LanguageModel<IString> load(String filename,
      String vocabFilename) throws IOException {
    // Check if we've already created and cached lm
    File f = new File(filename);
    String filepath = f.getAbsolutePath();
    if (lmStore.containsKey(filepath))
      return lmStore.get(filepath);

    // Otherwise, create a new one and add it to the cache
    LanguageModel<IString> newLM = new SRILanguageModel(filename, vocabFilename);
    lmStore.put(filepath, newLM);
    NONE_TOKEN = srilm.getVocab_None();
    return newLM;
  }

  protected SRILanguageModel(String filename, String vocabFilename)
      throws IOException {
    this.order = getOrder(filename);
    name = String.format("APRA(%s)", filename);
    System.gc();
    Runtime rt = Runtime.getRuntime();
    long preLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long startTimeMillis = System.currentTimeMillis();

    p_vocab = srilm.initVocab(lm_start_sym_id, lm_end_sym_id);
    p_srilm = srilm.initLM(order, p_vocab);
    if (vocabFilename != null) {
      for (String w : IString.index.keySet())
        srilm.getIndexForWord(p_vocab, w);
      System.err.println("SRILM: closed vocabulary.");
      srilm.readLM_limitVocab(p_srilm, p_vocab, filename, vocabFilename);
    } else {
      System.err.println("SRILM: open vocabulary.");
      srilm.readLM(p_srilm, filename);
    }
    // Needed by LM truecaser requires this:
    if (addVocabToIStrings)
      addVocabToIStrings(filename);

    ids = new int[lm_end_sym_id];
    Arrays.fill(ids, -1);

    long postLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
    System.err
        .printf(
            "Done loading arpa lm: %s (order: %d) (mem used: %d MiB time: %.3f s)\n",
            filename, order, (postLMLoadMemUsed - preLMLoadMemUsed)
                / (1024 * 1024), loadTimeMillis / 1000.0);
  }

  @Override
  public String toString() {
    return getName();
  }

  public double score(Sequence<IString> sequence) {
    if (isBoundaryWord(sequence))
      return 0.0;
    Sequence<IString> ngram;
    int sequenceSz = sequence.size();
    int maxOrder = (order < sequenceSz ? order : sequenceSz);

    if (sequenceSz == maxOrder) {
      ngram = sequence;
    } else {
      ngram = sequence.subsequence(sequenceSz - maxOrder, sequenceSz);
    }
    return scoreR(ngram);
  }

  public int order() {
    return order;
  }

  public boolean releventPrefix(Sequence<IString> prefix) {
    if (prefix.size() > order - 1)
      return false;
    int hist_size = prefix.size();
    SWIGTYPE_p_unsigned_int hist = threadHist.get();

    for (int i = 0; i < hist_size; i++)
      srilm.unsigned_array_setitem(hist, i, id(prefix.get(i)));
    long depth = srilm.getDepth(p_srilm, hist, hist_size);
    return (depth == prefix.size());
  }

  private int id(IString str) {
    int lm_id, p_id = str.id;
    if ((lm_id = ids[p_id]) > 0)
      return lm_id;
    return ids[p_id] = (int) srilm.getIndexForWord(p_vocab, str.word());
  }

  private double scoreR(Sequence<IString> ngram_wrds) {
    int hist_size = ngram_wrds.size() - 1;
    SWIGTYPE_p_unsigned_int hist = threadHist.get();

    srilm.unsigned_array_setitem(hist, hist_size, NONE_TOKEN);
    for (int i = 0; i < hist_size; ++i)
      srilm.unsigned_array_setitem(hist, hist_size - 1 - i,
          id(ngram_wrds.get(i)));

    double res = srilm
        .getWordProb(p_srilm, id(ngram_wrds.get(hist_size)), hist);

    return res * LOG10;
  }

  /**
   * Determines whether we are computing p( <s> | <s> ... ) or p( w_n=</s> |
   * w_n-1=</s> ..), in which case log-probability is zero. This function is
   * only useful if the translation hypothesis contains explicit <s> and </s>,
   * and always returns false otherwise.
   */
  private boolean isBoundaryWord(Sequence<IString> sequence) {
    if (sequence.size() == 2 && sequence.get(0).equals(getStartToken())
        && sequence.get(1).equals(getStartToken())) {
      return true;
    }
    if (sequence.size() > 1) {
      int last = sequence.size() - 1;
      IString endTok = getEndToken();
      if (sequence.get(last).equals(endTok)
          && sequence.get(last - 1).equals(endTok)) {
        return true;
      }
    }
    return false;
  }

  private int getOrder(String filename) {
    int maxOrder = -1;
    try {
      LineNumberReader lmReader = IOTools.getReaderFromFile(filename);
      String line;
      for (int lineNb = 0; lineNb < 100 && (line = lmReader.readLine()) != null; ++lineNb) {
        if (line.matches("ngram \\d+=\\d+")) {
          int order = Integer.parseInt(line.substring(6, line.indexOf("=")));
          if (order > maxOrder)
            maxOrder = order;
        } else if (line.matches("^\\1-grams:")) {
          break;
        }
      }
      lmReader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (maxOrder < 1) {
      System.err.printf("Could not determine order of %s. Assuming order=5.\n",
          filename);
      return 5;
    }
    return maxOrder;
  }

  private void addVocabToIStrings(String filename) {
    try {
      LineNumberReader lmReader = IOTools.getReaderFromFile(filename);
      String line;
      boolean uni = false;
      while ((line = lmReader.readLine()) != null) {
        if (line.matches("^\\\\1-grams:")) {
          uni = true;
        } else if (line.matches("^\\\\2-grams:")) {
          break;
        } else if (uni) {
          // System.err.println("line: "+line);
          StringTokenizer tok = new StringTokenizer(line);
          if (tok.hasMoreTokens()) {
            tok.nextToken(); // skip logprob
            if (tok.hasMoreTokens()) {
              String n = tok.nextToken();
              // System.err.println("tok: "+n);
              new IString(n);
            }
          }
        }
      }
      lmReader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err
          .printf("Usage:\n\tjava ...ARPALanguageModel (arpa model) \"sentence or file to score\"\n");
      System.exit(-1);
    }

    verbose = true;
    String modelStr = args[0];
    String file = args[1];
    for (String model : modelStr.split(":")) {
      System.out.printf("Loading lm: %s...\n", model);
      LanguageModel<IString> lm = SRILanguageModel.load(model, null);
      System.out.printf("done loading lm.\n");

      long startTimeMillis = System.currentTimeMillis();
      for (String sent : ObjectBank.getLineIterator(file)) {
        sent = sent.toLowerCase();
        System.out.printf("Sentence: %s\n", sent);
        Sequence<IString> seq = new SimpleSequence<IString>(
            IStrings.toIStringArray(sent.split("\\s")));
        double score = LanguageModels.scoreSequence(lm, seq);
        System.out.printf("Sequence score: %f score_log10: %f\n", score, score
            / Math.log(10));
      }
      double totalSecs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
      System.err.printf("secs = %.3f\n", totalSecs);
    }
  }
}
