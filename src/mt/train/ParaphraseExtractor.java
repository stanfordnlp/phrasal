package mt.train;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.*;
import java.io.PrintStream;
import java.io.IOException;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Beam;
import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

/**
 * Prints a monolingual (source-language) phrase table instead of bilingual,
 * using the target language as a pivot to find correspondance between
 * source-language phrases.
 *
 * @author Michel Galley
 */
public class ParaphraseExtractor extends CombinedFeatureExtractor {

  public static final String NBEST_SZ_PROPERTY = "NBestSize";
  public static final int NBEST_SZ = Integer.parseInt(System.getProperty(NBEST_SZ_PROPERTY, "10"));

  public static final String BEAM_SZ_PROPERTY = "BeamSize";
  public static final int BEAM_SZ = Integer.parseInt(System.getProperty(BEAM_SZ_PROPERTY, "100"));

  public static final String SKIP_IDENTICAL_PROPERTY = "SkipIdenticalParaphrase";
  public static final boolean SKIP_IDENTICAL = Boolean.parseBoolean(System.getProperty(SKIP_IDENTICAL_PROPERTY, "false"));

  public static final String INV_MODEL_PROPERTY = "InvModel";
  public static final boolean INV_MODEL = Boolean.parseBoolean(System.getProperty(INV_MODEL_PROPERTY, "false"));

  class PhraseHyp { int i; double d; public PhraseHyp(int i, double d) { this.i=i; this.d=d; } }

  static Comparator<PhraseHyp> beamCmp;

  public ParaphraseExtractor(Properties prop) throws IOException {
    super(prop);
    beamCmp = new Comparator<PhraseHyp>() {
      public int compare(PhraseHyp p1, PhraseHyp p2) {
         return(Double.valueOf(p1.d).compareTo(p2.d));
      }
    };
  }

  private String keyToPlainString(int key) {
    StringBuffer buf = new StringBuffer();
    IString[] strs = IStrings.toIStringArray(alTemps.getF(key));
    for(int i=0; i<strs.length; ++i) {
      if(i>0)
        buf.append(" ");
      buf.append(strs[i].toString());
    }
    return buf.toString();
  }

  @Override
  public boolean write(PrintStream oStream, boolean noAlign) {
    Map<Integer, Beam<PhraseHyp>> kbest_ef = getKBest(BEAM_SZ,false);
    Map<Integer,Beam<PhraseHyp>> kbest_fe = getKBest(BEAM_SZ,true);
    if(oStream == null)
        oStream = System.out;
    for(int fKey : kbest_ef.keySet()) {
      if(alTemps.getF(fKey) == null)
        continue;
      String phrase = keyToPlainString(fKey);
      oStream.print(phrase);
      oStream.print("\n");
      Map<Integer,Double> scores = new HashMap<Integer,Double>();
      for(PhraseHyp eh : kbest_ef.get(fKey)) {
        for(PhraseHyp fh : kbest_fe.get(eh.i)) {
          double score = 0.0;
          if(scores.get(fh.i) != null)
            score = scores.get(fh.i);
          score += eh.d*fh.d;
          scores.put(fh.i,score);
        }
      }
      Beam<PhraseHyp> nbest = new Beam<PhraseHyp>(NBEST_SZ,beamCmp);
      for(int fKey2 : scores.keySet())
        nbest.add(new PhraseHyp(fKey2,scores.get(fKey2)));
      for(PhraseHyp hyp : nbest.asSortedList()) { 
        String paraphrase = keyToPlainString(hyp.i);
        if(SKIP_IDENTICAL && phrase.equals(paraphrase))
          continue;
        oStream.print("\t");
        oStream.print(paraphrase);
        oStream.print("\t");
        oStream.print(hyp.d);
        oStream.print("\n");
      }
    }
    return true;
  }

  private Map<Integer,Beam<PhraseHyp>> getKBest(int k, boolean inv) {
    Map<Integer, Beam<PhraseHyp>> m = new Int2ObjectOpenHashMap<Beam<PhraseHyp>>();
    for(int idx=0; idx<alTemps.size(); ++idx) {
      alTemps.reconstructAlignmentTemplate(alTemp, idx);
      int fKey = inv ? alTemp.getEKey() : alTemp.getFKey();
      int eKey = inv ? alTemp.getFKey() : alTemp.getEKey();
      if(m.get(fKey) == null)
        m.put(fKey,new Beam<PhraseHyp>(k,beamCmp));
      Beam<PhraseHyp> beam = m.get(fKey);
      assert(extractors.size() == 1);
      AbstractFeatureExtractor e = extractors.get(0);
      Object scores = e.score(alTemp);
      assert(scores.getClass().isArray());
      double[] scoreArray = (double[]) scores;
      if(scoreArray.length != 2)
        throw new RuntimeException("Score array of unexpected length: "+scoreArray.length+" != 2.");
      double score = scoreArray[(inv ^ INV_MODEL) ? 0 : 1];
      beam.add(new PhraseHyp(eKey,score));
    }
    return m;
  }

  public static void main(String[] args) throws IOException {
    Properties prop = StringUtils.argsToProperties(args);
    prop.put(EXTRACTORS_OPT,"mt.train.PharaohFeatureExtractor");
    prop.put(ONLY_ML_OPT,"true");
    prop.put(SPLIT_SIZE_OPT,"1");
    AbstractPhraseExtractor.setPhraseExtractionProperties(prop);
    try {
      ParaphraseExtractor e = new ParaphraseExtractor(prop);
      e.extractAll();
    } catch(Exception e) {
      e.printStackTrace();
      usage();
    }
  }
}
