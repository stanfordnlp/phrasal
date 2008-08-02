package mt.train;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.*;
import java.io.PrintStream;
import java.io.IOException;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Beam;
import edu.stanford.nlp.util.IStrings;
//import edu.stanford.nlp.util.Beam;

/**
 * Prints a monolingual (source-language) phrase table instead of bilingual,
 * using the target language as a pivot to find correspondance between
 * source-language phrases.
 *
 * @author Michel Galley
 */
public class ParaphraseExtractor extends CombinedFeatureExtractor {

  public static final String BEAM_SZ_PROPERTY = "BeamSize";
  public static final int BEAM_SZ = Integer.parseInt(System.getProperty(BEAM_SZ_PROPERTY, "100"));

  class PhraseHyp { int i; double d; public PhraseHyp(int i, double d) { this.i=i; this.d=d; } }

  static Comparator<PhraseHyp> beamCmp;

  public ParaphraseExtractor(Properties prop) {
    super(prop);
    beamCmp = new Comparator<PhraseHyp>() {
      public int compare(PhraseHyp p1, PhraseHyp p2) {
         return(Double.valueOf(p1.d).compareTo(p2.d));
      }
    };
  }

  @Override
  public boolean write(PrintStream oStream, boolean noAlign) {
    Map<Integer, Beam<PhraseHyp>> kbest_ef = getKBest(BEAM_SZ,false);
    Map<Integer,Beam<PhraseHyp>> kbest_fe = getKBest(BEAM_SZ,true);
    for(int fKey : kbest_ef.keySet()) {
      oStream.print(Arrays.toString(IStrings.toIStringArray(alTemps.getF(fKey))));
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
      Beam<PhraseHyp> beam_ff = new Beam<PhraseHyp>(BEAM_SZ,beamCmp);
      for(int fKey2 : scores.keySet())
        beam_ff.add(new PhraseHyp(fKey2,scores.get(fKey2)));
      for(PhraseHyp hyp : beam_ff.asSortedList()) {
        oStream.print(" ");
        oStream.print(Arrays.toString(IStrings.toIStringArray(alTemps.getF(hyp.i))));
        oStream.print(" ||| ");
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
      assert(scoreArray.length == 2);
      double score = scoreArray[inv ? 1 : 0];
      beam.add(new PhraseHyp(eKey,score));
    }
    return m;
  }

  public static void main(String[] args) throws IOException {
    Properties prop = StringUtils.argsToProperties(args);
    prop.put(CombinedFeatureExtractor.EXTRACTORS_OPT,"mt.train.PhiFeatureExtractor");
    prop.put(CombinedFeatureExtractor.SPLIT_SIZE_OPT,"1");
    checkProperties(prop);
    AbstractPhraseExtractor.setPhraseExtractionProperties(prop);
    printFeatureNames = Boolean.parseBoolean(prop.getProperty(PRINT_FEATURE_NAMES_OPT,"true"));
    if(!multiPassFeatureExtract(prop))
      usage();
  }
}
