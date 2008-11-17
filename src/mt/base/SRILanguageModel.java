package mt.base;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.io.*;

import mt.srilm.srilm;
import mt.srilm.SWIGTYPE_p_Ngram;
import mt.srilm.SWIGTYPE_p_unsigned_int;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;


/**
 * Language model class using SRILM native code.
 * Not yet fully tested.
 *
 * @author Michel Galley
 */
public class SRILanguageModel implements LanguageModel<IString> {

  static boolean verbose = false;

  protected final String name;
  public static final IString START_TOKEN = new IString("<s>");
  public static final IString END_TOKEN = new IString("</s>");

  SWIGTYPE_p_Ngram p_srilm=null;

  Int2LongMap srilmVocab = new Int2LongOpenHashMap();

  private final int order=3;

  public String getName() {
    return name;
  }

  public IString getStartToken() {
    return START_TOKEN;
  }

  public IString getEndToken() {
    return END_TOKEN;
  }

  protected static final int MAX_GRAM = 10; // highest order ngram possible

  public static LanguageModel<IString> load(String filename) throws IOException {
    return new SRILanguageModel(filename);
  }

  protected SRILanguageModel(String filename) throws IOException {
    name = String.format("APRA(%s)",filename);
    System.gc();
    Runtime rt = Runtime.getRuntime();
    long preLMLoadMemUsed = rt.totalMemory()-rt.freeMemory();
    long startTimeMillis = System.currentTimeMillis();

		p_srilm = srilm.initLM(order, START_TOKEN.id, END_TOKEN.id);
		srilm.readLM(p_srilm, filename);

    // print some status information
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
    Sequence<IString> ngram;
    int sequenceSz = sequence.size();
    int maxOrder   = (order < sequenceSz ? order : sequenceSz);

    if (sequenceSz == maxOrder) {
      ngram = sequence;
    } else {
      ngram = sequence.subsequence(sequenceSz-maxOrder, sequenceSz);
    }

    int hist_size = order - 1;
    SWIGTYPE_p_unsigned_int hist = srilm.new_unsigned_array(hist_size);
    for(int i=0; i<hist_size; i++){
       srilm.unsigned_array_setitem(hist, i, getVocabIdx(ngram.get(i)));
    }

    double score = srilm.getProb_lzf(p_srilm, hist, hist_size, getVocabIdx(ngram.get(hist_size)));
    srilm.delete_unsigned_array(hist);
      
    if(verbose)
      System.err.printf("score: seq: %s logp: %f\n", sequence.toString(), score);
    return score;
  }

  private long getVocabIdx(IString w) {
    if(!srilmVocab.containsKey(w.id))
      srilmVocab.put(w.id,srilm.getIndexForWord(w.toString()));
    return srilmVocab.get(w.id);
  }

  public int order() {
    return order;
  }

  public boolean releventPrefix(Sequence<IString> prefix) {
    return true;
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.printf("Usage:\n\tjava ...ARPALanguageModel (arpa model) \"sentence or file to score\"\n");
      System.exit(-1);
    }

    //verbose = true;
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
