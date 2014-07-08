package edu.stanford.nlp.mt.decoder.feat;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.PhrasePenaltyFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TranslationModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.SourceGapFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.TargetGapFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.UnknownWordFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.WordPenaltyFeaturizer;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.mt.util.FactoryUtil;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.util.Generics;

/**
 * Load translation model feature extractors.
 * 
 * @author danielcer
 *
 */
public final class FeaturizerFactory {

  public static final String MOSES_DENSE_FEATURES = "mosesdense";
  public static final String BASELINE_FEATURES = "baseline";
  public static final String DEFAULT_FEATURIZERS = MOSES_DENSE_FEATURES;
  public static final String ARPA_LM_PARAMETER = "arpalm";
  public static final String LINEAR_DISTORTION_PARAMETER = "lineardistortion";
  public static final String GAP_PARAMETER = "gap";
  public static final String NUM_PHRASE_FEATURES = "numphrasefeatures";

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

    final int numPhraseFeatures = paramPairs.containsKey(NUM_PHRASE_FEATURES) ?
        Integer.valueOf(paramPairs.get(NUM_PHRASE_FEATURES)) : Integer.MAX_VALUE;
    
    // Model features
    if (featurizerName.equals(BASELINE_FEATURES)) {
      if (!paramPairs.containsKey(ARPA_LM_PARAMETER)) {
        throw new RuntimeException(
            String
                .format(
                    "Baseline featurizers requires that a language model is specificed using the parameter '%s'",
                    ARPA_LM_PARAMETER));
      }
      List<Featurizer<IString, String>> baselineFeaturizers = Generics.newLinkedList();
      baselineFeaturizers.addAll(gapFeaturizers);

      // ARPA LM
      DerivationFeaturizer<IString, String> arpaLmFeaturizer = null;
      String lm = paramPairs.get(ARPA_LM_PARAMETER);
      if (lm != null && ! lm.equals("")) {
        arpaLmFeaturizer = new NGramLanguageModelFeaturizer(
            LanguageModelFactory.load(lm));
        baselineFeaturizers.add(arpaLmFeaturizer);
      }

      // Precomputed phrase to phrase translation scores
      baselineFeaturizers.add(new TranslationModelFeaturizer(numPhraseFeatures));

      // Linear distortion
      baselineFeaturizers.add(linearDistortionFeaturizer);

      return new CombinedFeaturizer<IString, String>(baselineFeaturizers);
    
    } else if (featurizerName.equals(MOSES_DENSE_FEATURES)) {
      List<Featurizer<IString, String>> pharaohFeaturizers = Generics.newLinkedList();
      pharaohFeaturizers.addAll(gapFeaturizers);

      DerivationFeaturizer<IString, String> arpaLmFeaturizer;
      // ARPA LM
      String lm = paramPairs.get(ARPA_LM_PARAMETER);
      if (lm != null) {
        arpaLmFeaturizer = new NGramLanguageModelFeaturizer(
            LanguageModelFactory.load(lm));
        pharaohFeaturizers.add(arpaLmFeaturizer);
      }

      // Precomputed phrase to phrase translation scores
      pharaohFeaturizers.add(new TranslationModelFeaturizer(numPhraseFeatures));

      // Linear distortion
      pharaohFeaturizers.add(linearDistortionFeaturizer);

      // Word Penalty
      pharaohFeaturizers.add(new WordPenaltyFeaturizer<IString>());
      
      // Phrase Penalty
      pharaohFeaturizers.add(new PhrasePenaltyFeaturizer<IString>());
      
      // Unknown Word Featurizer
      pharaohFeaturizers.add(new UnknownWordFeaturizer<IString>());

      // return combined model
      return new CombinedFeaturizer<IString, String>(pharaohFeaturizers);
    }
    throw new RuntimeException(String.format("Unrecognized featurizer '%s'",
        featurizerName));
  }
}
