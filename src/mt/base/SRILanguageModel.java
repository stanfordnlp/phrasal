package mt.base;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.io.*;
import java.util.Arrays;

import mt.srilm.srilm;
import mt.srilm.SWIGTYPE_p_Ngram;
import mt.srilm.SWIGTYPE_p_unsigned_int;

/**
 * Language model class using SRILM native code.
 *
 * @author Michel Galley
 */
public class SRILanguageModel implements LanguageModel<IString> {

  static { System.loadLibrary("srilm"); }

  static boolean verbose = false;

  protected final String name;

  private static final IString START_TOKEN = new IString("<s>");
  private static final IString END_TOKEN = new IString("</s>");
  private static final double LOG10 = Math.log(10); 
  private static final int lm_start_sym_id = 11; //1-10 reserved for special symbols
	private static final int lm_end_sym_id = 5000001; //max vocab 5M
  private SWIGTYPE_p_Ngram p_srilm;
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

  public static LanguageModel<IString> load(String filename) throws IOException {
    return new SRILanguageModel(filename);
  }

  protected SRILanguageModel(String filename) throws IOException {
    this.order = getOrder(filename);
    name = String.format("APRA(%s)",filename);
    System.gc();
    Runtime rt = Runtime.getRuntime();
    long preLMLoadMemUsed = rt.totalMemory()-rt.freeMemory();
    long startTimeMillis = System.currentTimeMillis();

		p_srilm = srilm.initLM(order, lm_start_sym_id, lm_end_sym_id );
		srilm.readLM(p_srilm, filename);

    ids = new int[lm_end_sym_id];
    Arrays.fill(ids,-1);

    long postLMLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
    System.err.printf("Done loading arpa lm: %s (order: %d) (mem used: %d MiB time: %.3f s)\n", filename, order,
            (postLMLoadMemUsed - preLMLoadMemUsed)/(1024*1024), loadTimeMillis/1000.0);
  }

  @Override
  public String toString() {
    return getName();
  }

  public double score(Sequence<IString> sequence) {
    if(isBoundaryWord(sequence)) return 0.0;
    Sequence<IString> ngram;
    int sequenceSz = sequence.size();
    int maxOrder   = (order < sequenceSz ? order : sequenceSz);

    if (sequenceSz == maxOrder) {
      ngram = sequence;
    } else {
      ngram = sequence.subsequence(sequenceSz-maxOrder, sequenceSz);
    }
    return scoreR(ngram);
  }

  public int order() {
    return order;
  }

  /*public boolean releventPrefix(Sequence<IString> prefix) {
    if (prefix.size() > order-1) return false;
    // TODO
    return true;
  }*/

  public boolean releventPrefix(Sequence<IString> prefix) {
    if (prefix.size() > order-1) return false;
    int hist_size = prefix.size();
    SWIGTYPE_p_unsigned_int hist;
    hist = srilm.new_unsigned_array(hist_size);
    for(int i=0; i< hist_size; i++)
    srilm.unsigned_array_setitem(hist, i, id(prefix.get(i)));
    long depth = srilm.getBOW_depth(p_srilm, hist, hist_size);
    srilm.delete_unsigned_array(hist);
    //System.err.printf("(3) prefix={{{%s}}} siz=%d depth=%d\n", prefix.toString(), prefix.size(), depth);
    return (depth == prefix.size());
  }

	private int id(IString str) {
    int lm_id, p_id = str.id;
    if((lm_id = ids[p_id]) > 0)
      return lm_id;
		return ids[p_id] = (int)srilm.getIndexForWord(str.word());
	}

	private double scoreR(Sequence<IString> ngram_wrds) {
    int hist_size = ngram_wrds.size()-1;
    SWIGTYPE_p_unsigned_int hist;
    hist = srilm.new_unsigned_array(hist_size);
    for(int i=0; i< hist_size; i++)
      srilm.unsigned_array_setitem(hist, i, id(ngram_wrds.get(i)));
    double res = srilm.getProb(p_srilm, hist, hist_size, id(ngram_wrds.get(hist_size)));
    srilm.delete_unsigned_array(hist);
    return res*LOG10;
  }
 
  /**
   * Determines whether we are computing p( <s> | <s> ... ) or p( w_n=</s> | w_n-1=</s> ..),
   * in which case log-probability is zero. This function is only useful if the translation
   * hypothesis contains explicit <s> and </s>, and always returns false otherwise.
   */
  private boolean isBoundaryWord(Sequence<IString> sequence) {
    if(sequence.size() == 2 && sequence.get(0).equals(getStartToken()) && sequence.get(1).equals(getStartToken())) {
      return true;
    }
    if(sequence.size() > 1) {
      int last = sequence.size()-1;
      IString endTok = getEndToken();
      if(sequence.get(last).equals(endTok) && sequence.get(last-1).equals(endTok)) {
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
      for(int lineNb = 0; lineNb < 100 && (line = lmReader.readLine()) != null; ++lineNb) {
        if(line.matches("ngram \\d+=\\d+")) {
          int order = Integer.parseInt(line.substring(6,line.indexOf("=")));
          if(order > maxOrder)
            maxOrder = order;
        } else if(line.matches("^\\1-grams:")) {
          break;
        }
      }
      lmReader.close();
    } catch(IOException e) {
      e.printStackTrace();
    }
    if(maxOrder < 1) {
      System.err.printf("Could not determine order of %s. Assuming order=5.\n",filename);
      return 5;
    }
    return maxOrder;
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.printf("Usage:\n\tjava ...ARPALanguageModel (arpa model) \"sentence or file to score\"\n");
      System.exit(-1);
    }

    verbose = true;
    String model = args[0]; String file = args[1];
    System.out.printf("Loading lm: %s...\n", model);
    LanguageModel<IString> lm = SRILanguageModel.load(model);
    System.out.printf("done loading lm.\n");

    long startTimeMillis = System.currentTimeMillis();
    for(String sent : ObjectBank.getLineIteratorObjectBank(file)) {
      sent = sent.toLowerCase();
      System.out.printf("Sentence: %s\n", sent);
      Sequence<IString> seq = new SimpleSequence<IString>(IStrings.toIStringArray(sent.split("\\s")));
      double score = LanguageModels.scoreSequence(lm, seq);
      System.out.printf("Sequence score: %f score_log10: %f\n", score, score/Math.log(10));
    }
    double totalSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
    System.err.printf("secs = %.3f\n", totalSecs);
  }
}
