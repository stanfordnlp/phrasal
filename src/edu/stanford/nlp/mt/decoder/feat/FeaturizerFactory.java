package edu.stanford.nlp.mt.decoder.feat;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.mt.base.FactoryUtil;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.lm.LanguageModels;
import edu.stanford.nlp.util.Generics;

/**
 * @author danielcer
 *
 */
public class FeaturizerFactory {

  public static final String PSEUDO_PHARAOH_GENERATOR = "pseudopharaoh";
  public static final String BASELINE_FEATURIZERS = "baseline";
  public static final String DEFAULT_WEIGHTING_BASELINE_FEATURIZERS = "weightedbaseline";
  public static final String DEFAULT_FEATURIZERS = DEFAULT_WEIGHTING_BASELINE_FEATURIZERS;
  public static final String ARPA_LM_PARAMETER = "arpalm";
  public static final String NUM_THREADS = "nthreads";
  public static final String LINEAR_DISTORTION_PARAMETER = "lineardistortion";
  public static final String GAP_PARAMETER = "gap";


  private FeaturizerFactory() { } // static class


  public enum GapType {
    none, source, target, both
  }

  // Legacy stuff
  public static final Map<String, Double> DEFAULT_TM_FEATURE_WEIGHTS_MAP = new HashMap<String, Double>();
  public static final double[] DEFAULT_BASELINE_WTS;
  public static final double DEFAULT_LINEAR_DISTORTION_WT = -2.0;
  public static final double DEFAULT_ARPALM_WT = 3.0;
  public static final double DEFAULT_COLLAPSE_TM_WT = 1.0;

  static {
    /*
    Map<String, Double> m = DEFAULT_TM_FEATURE_WEIGHTS_MAP;
    m.put(PhraseTableScoresFeaturizer.PREFIX
        + FlatPhraseTable.FIVESCORE_PHI_e_f, 0.2);
    m.put(PhraseTableScoresFeaturizer.PREFIX
        + FlatPhraseTable.FIVESCORE_LEX_e_f, 1.0);
    m.put(PhraseTableScoresFeaturizer.PREFIX
        + FlatPhraseTable.FIVESCORE_PHI_f_e, 2.0);
    m.put(PhraseTableScoresFeaturizer.PREFIX
        + FlatPhraseTable.FIVESCORE_LEX_f_e, 1.0);
    m.put(PhraseTableScoresFeaturizer.PREFIX
        + FlatPhraseTable.FIVESCORE_PHRASE_PENALTY, -1.0);
    m.put(PhraseTableScoresFeaturizer.PREFIX + FlatPhraseTable.ONESCORE_P_t_f,
        4.0);
  */
    DEFAULT_BASELINE_WTS = new double[] { DEFAULT_LINEAR_DISTORTION_WT,
        DEFAULT_ARPALM_WT, DEFAULT_COLLAPSE_TM_WT };
  }


  @SuppressWarnings("unchecked")
  public static <TK, FV> Class<Featurizer<TK, FV>> loadFeaturizer(
      String name) throws ClassNotFoundException {
    return (Class<Featurizer<TK, FV>>) ClassLoader.getSystemClassLoader().loadClass(name);
  }

  /**
	 */
  @SuppressWarnings("unchecked")
  public static CombinedFeaturizer<IString, String> factory(
      String...featurizerSpecs) throws IOException {
    final String featurizerName = featurizerSpecs.length == 0 ? DEFAULT_FEATURIZERS :
      featurizerSpecs[0].toLowerCase();
    final Map<String, String> paramPairs = FactoryUtil.getParamPairs(featurizerSpecs);
    final int numThreads = paramPairs.containsKey(NUM_THREADS) ? Integer.valueOf(paramPairs.get(NUM_THREADS)) : 1;

    // Linear distortion
    final DerivationFeaturizer<IString, String> linearDistortionFeaturizer;
    try {
      linearDistortionFeaturizer = (DerivationFeaturizer<IString, String>) Class
          .forName(paramPairs.get(LINEAR_DISTORTION_PARAMETER)).newInstance();
      System.err.println("Linear distortion featurizer: "
          + linearDistortionFeaturizer);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // Gaps:
    List<DerivationFeaturizer<IString, String>> gapFeaturizers = new LinkedList<DerivationFeaturizer<IString, String>>();
    GapType gapType = GapType.valueOf(paramPairs.get(GAP_PARAMETER));
    if (gapType == GapType.source || gapType == GapType.both)
      gapFeaturizers.add(new SourceGapFeaturizer());
    if (gapType == GapType.target || gapType == GapType.both)
      gapFeaturizers.add(new TargetGapFeaturizer());

    if (featurizerName.equals(BASELINE_FEATURIZERS)
        || featurizerName.equals(DEFAULT_WEIGHTING_BASELINE_FEATURIZERS)) {

      if (!paramPairs.containsKey(ARPA_LM_PARAMETER)) {
        throw new RuntimeException(
            String
                .format(
                    "Baseline featurizers requires that a language model is specificed using the parameter '%s'",
                    ARPA_LM_PARAMETER));
      }
      List<Featurizer<IString, String>> baselineFeaturizers = Generics.newLinkedList();
      baselineFeaturizers.addAll(gapFeaturizers);

      DerivationFeaturizer<IString, String> arpaLmFeaturizer = null;
      Featurizer<IString,String> phraseTableScoresFeaturizer;

      // ARPA LM
      String lm = paramPairs.get(ARPA_LM_PARAMETER);
      if (lm != null && ! lm.equals("")) {
        arpaLmFeaturizer = new NGramLanguageModelFeaturizer(
            LanguageModels.load(lm, numThreads));
        baselineFeaturizers.add(arpaLmFeaturizer);
      }

      // Precomputed phrase to phrase translation scores
      phraseTableScoresFeaturizer = new PhraseTableScoresFeaturizer<IString>();
      baselineFeaturizers.add(phraseTableScoresFeaturizer);

      // Linear distortion
      baselineFeaturizers.add(linearDistortionFeaturizer);

      if (featurizerName.equals(BASELINE_FEATURIZERS)) {
        return new CombinedFeaturizer<IString, String>(baselineFeaturizers);
      } else {
        DerivationFeaturizer<IString, String> collapsedTmFeaturizer = new CollapsedFeaturizer<IString, String>(
            "comboBaselineTM:", DEFAULT_TM_FEATURE_WEIGHTS_MAP,
            phraseTableScoresFeaturizer);
        CollapsedFeaturizer<IString, String> fullModel = new CollapsedFeaturizer<IString, String>(
            "comboBaselineModel", DEFAULT_BASELINE_WTS,
            linearDistortionFeaturizer, arpaLmFeaturizer, collapsedTmFeaturizer);
        return new CombinedFeaturizer<IString, String>(fullModel);
      }
    
    } else if (featurizerName.equals(PSEUDO_PHARAOH_GENERATOR)) {
      List<Featurizer<IString, String>> pharaohFeaturizers = Generics.newLinkedList();
      pharaohFeaturizers.addAll(gapFeaturizers);

      DerivationFeaturizer<IString, String> arpaLmFeaturizer;
      Featurizer<IString,String> phraseTableScoresFeaturizer, wordPenaltyFeaturizer, unknownWordFeaturizer;
      // ARPA LM
      String lm = paramPairs.get(ARPA_LM_PARAMETER);
      if (lm != null) {
        arpaLmFeaturizer = new NGramLanguageModelFeaturizer(
            LanguageModels.load(lm, numThreads));
        pharaohFeaturizers.add(arpaLmFeaturizer);
      }

      // Precomputed phrase to phrase translation scores
      phraseTableScoresFeaturizer = new PhraseTableScoresFeaturizer<IString>();
      pharaohFeaturizers.add(phraseTableScoresFeaturizer);

      // Linear distortion
      pharaohFeaturizers.add(linearDistortionFeaturizer);

      // Word Penalty
      wordPenaltyFeaturizer = new WordPenaltyFeaturizer<IString>();
      pharaohFeaturizers.add(wordPenaltyFeaturizer);

      // Unknown Word Featurizer
      unknownWordFeaturizer = new UnknownWordFeaturizer<IString>();
      pharaohFeaturizers.add(unknownWordFeaturizer);

      // return combined model
      return new CombinedFeaturizer<IString, String>(pharaohFeaturizers);
    }
    throw new RuntimeException(String.format("Unrecognized featurizer '%s'",
        featurizerName));
  }
}
