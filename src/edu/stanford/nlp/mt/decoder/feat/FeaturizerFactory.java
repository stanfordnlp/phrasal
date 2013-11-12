package edu.stanford.nlp.mt.decoder.feat;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.FactoryUtil;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.PhraseTableScoresFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.SourceGapFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TargetGapFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.UnknownWordFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.WordPenaltyFeaturizer;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.util.Generics;

/**
 * Load translation model feature extractors.
 * 
 * @author danielcer
 *
 */
public final class FeaturizerFactory {

  public static final String PSEUDO_PHARAOH_GENERATOR = "pseudopharaoh";
  public static final String BASELINE_FEATURIZERS = "baseline";
  public static final String DEFAULT_FEATURIZERS = PSEUDO_PHARAOH_GENERATOR;
  public static final String ARPA_LM_PARAMETER = "arpalm";
  public static final String LINEAR_DISTORTION_PARAMETER = "lineardistortion";
  public static final String GAP_PARAMETER = "gap";

  public enum GapType {
    none, source, target, both
  }

  private FeaturizerFactory() { } // static class

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

    // Model features
    if (featurizerName.equals(BASELINE_FEATURIZERS)) {
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
            LanguageModelFactory.load(lm));
        baselineFeaturizers.add(arpaLmFeaturizer);
      }

      // Precomputed phrase to phrase translation scores
      phraseTableScoresFeaturizer = new PhraseTableScoresFeaturizer<IString>();
      baselineFeaturizers.add(phraseTableScoresFeaturizer);

      // Linear distortion
      baselineFeaturizers.add(linearDistortionFeaturizer);

      return new CombinedFeaturizer<IString, String>(baselineFeaturizers);
    
    } else if (featurizerName.equals(PSEUDO_PHARAOH_GENERATOR)) {
      List<Featurizer<IString, String>> pharaohFeaturizers = Generics.newLinkedList();
      pharaohFeaturizers.addAll(gapFeaturizers);

      DerivationFeaturizer<IString, String> arpaLmFeaturizer;
      Featurizer<IString,String> phraseTableScoresFeaturizer, wordPenaltyFeaturizer, unknownWordFeaturizer;
      // ARPA LM
      String lm = paramPairs.get(ARPA_LM_PARAMETER);
      if (lm != null) {
        arpaLmFeaturizer = new NGramLanguageModelFeaturizer(
            LanguageModelFactory.load(lm));
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
