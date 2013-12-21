package edu.stanford.nlp.mt.train;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.*;
import java.io.PrintStream;
import java.io.IOException;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.util.*;

/**
 * Prints a monolingual (source-language) phrase table instead of bilingual,
 * using the target language as a pivot to find correspondance between
 * source-language phrases.
 * 
 * @author Michel Galley
 */
public class ParaphraseExtractor extends PhraseExtract {

  public static final String NBEST_SZ_PROPERTY = "NBestSize";
  public static final int NBEST_SZ = Integer.parseInt(System.getProperty(
      NBEST_SZ_PROPERTY, "10"));

  public static final String BEAM_SZ_PROPERTY = "BeamSize";
  public static final int BEAM_SZ = Integer.parseInt(System.getProperty(
      BEAM_SZ_PROPERTY, "100"));

  public static final String SKIP_IDENTICAL_PROPERTY = "SkipIdenticalParaphrase";
  public static final boolean SKIP_IDENTICAL = Boolean.parseBoolean(System
      .getProperty(SKIP_IDENTICAL_PROPERTY, "false"));

  public static final String INV_MODEL_PROPERTY = "InvModel";
  public static final boolean INV_MODEL = Boolean.parseBoolean(System
      .getProperty(INV_MODEL_PROPERTY, "false"));

  public static final String PMI_PROPERTY = "PMI";
  public static final boolean PMI = Boolean.parseBoolean(System.getProperty(
      PMI_PROPERTY, "false"));

  static class PhraseHyp {
    int i;
    double d;

    public PhraseHyp(int i, double d) {
      this.i = i;
      this.d = d;
    }
  }

  static Comparator<PhraseHyp> beamCmp;

  public ParaphraseExtractor(Properties prop) throws IOException {
    super(prop);
    beamCmp = new Comparator<PhraseHyp>() {
      @Override
      public int compare(PhraseHyp p1, PhraseHyp p2) {
        return (Double.valueOf(p1.d).compareTo(p2.d));
      }
    };
  }

  private String keyToPlainString(int key) {
    StringBuilder buf = new StringBuilder();
    IString[] strs = IStrings.toIStringArray(alTemps.getF(key));
    for (int i = 0; i < strs.length; ++i) {
      if (i > 0)
        buf.append(" ");
      buf.append(strs[i].toString());
    }
    return buf.toString();
  }

  @Override
  public boolean write(boolean noAlign) {

    assert (extractors.size() == 1);
    MosesPharoahFeatureExtractor e = (MosesPharoahFeatureExtractor) extractors.get(0);

    Map<Integer, Beam<PhraseHyp>> kbest_ef = getKBest(BEAM_SZ, false);
    Map<Integer, Beam<PhraseHyp>> kbest_fe = getKBest(BEAM_SZ, true);

    PrintStream oStream = System.out;

    for (Map.Entry<Integer, Beam<PhraseHyp>> integerBeamEntry : kbest_ef
        .entrySet()) {

      if (alTemps.getF(integerBeamEntry.getKey()) == null)
        continue;

      String phrase = keyToPlainString(integerBeamEntry.getKey());
      oStream.print(phrase);
      oStream.print("\n");

      Map<Integer, Double> scores = new HashMap<Integer, Double>();

      double totalFCount = totalFCount();

      for (PhraseHyp eh : integerBeamEntry.getValue()) {
        for (PhraseHyp fh : kbest_fe.get(eh.i)) {
          int fKey2 = fh.i;
          double sum = (scores.get(fKey2) != null) ? scores.get(fh.i) : 0.0;
          // Compute (or update): P(f' | f) = \sum_c P(f' | e) P(e | f)
          double score = eh.d * fh.d;
          // Compute (or update): PMI(f,f') = P(f,f') / P(f)P(f') = P(f'|f)P(f)
          // / P(f)P(f') = P(f'|f)/P(f'),
          // i.e., above score divited by P(f'):
          if (PMI) {
            double denom = e.fCounts.get(fKey2) / totalFCount;
            score /= denom;
          }
          scores.put(fh.i, sum + score);
        }
      }

      Beam<PhraseHyp> nbest = new Beam<PhraseHyp>(NBEST_SZ, beamCmp);

      for (Map.Entry<Integer, Double> integerDoubleEntry : scores.entrySet()) {
        double s = integerDoubleEntry.getValue();
        nbest.add(new PhraseHyp(integerDoubleEntry.getKey(), PMI ? Math.log(s)
            : s));
      }

      for (PhraseHyp hyp : nbest.asSortedList()) {
        String paraphrase = keyToPlainString(hyp.i);
        if (SKIP_IDENTICAL && phrase.equals(paraphrase))
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

  /**
   * For each phrase f, get k-best e according P(e|f).
   * 
   * @param k
   *          Size of k-best list.
   * @param inv
   *          If true, return k-best f according to P(f|e).
   */
  private Map<Integer, Beam<PhraseHyp>> getKBest(int k, boolean inv) {
    Map<Integer, Beam<PhraseHyp>> m = new Int2ObjectOpenHashMap<Beam<PhraseHyp>>();

    assert (extractors.size() == 1);
    MosesPharoahFeatureExtractor e = (MosesPharoahFeatureExtractor) extractors.get(0);

    for (int idx = 0; idx < alTemps.size(); ++idx) {

      alTemps.reconstructAlignmentTemplate(alTemp, idx);

      int fKey = inv ? alTemp.getEKey() : alTemp.getFKey();
      int eKey = inv ? alTemp.getFKey() : alTemp.getEKey();

      if (m.get(fKey) == null)
        m.put(fKey, new Beam<PhraseHyp>(k, beamCmp));
      Beam<PhraseHyp> beam = m.get(fKey);

      Object scores = e.score(alTemp);
      assert (scores.getClass().isArray());
      double[] scoreArray = (double[]) scores;

      if (scoreArray.length != 2)
        throw new RuntimeException("Score array of unexpected length: "
            + scoreArray.length + " != 2.");

      double score = scoreArray[(inv ^ INV_MODEL) ? 0 : 1];
      beam.add(new PhraseHyp(eKey, score));
    }

    return m;
  }

  private int totalFCount() {
    assert (extractors.size() == 1);
    MosesPharoahFeatureExtractor e = (MosesPharoahFeatureExtractor) extractors.get(0);
    int total = 0;
    for (int c : e.fCounts)
      total += c;
    return total;
  }

  public static void main(String[] args) throws IOException {

    Properties prop = StringUtils.argsToProperties(args);
    prop.put(FEATURE_EXTRACTORS_OPT, "mt.train.MosesFeatureExtractor");
    prop.put(ONLY_ML_OPT, "true");
    prop.put(SPLIT_SIZE_OPT, "1");

    AbstractPhraseExtractor.setPhraseExtractionProperties(prop);

    try {
      ParaphraseExtractor e = new ParaphraseExtractor(prop);
      e.extractAll();
    } catch (Exception e) {
      e.printStackTrace();
      usage();
    }
  }
}
