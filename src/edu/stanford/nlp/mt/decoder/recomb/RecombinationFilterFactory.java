package edu.stanford.nlp.mt.decoder.recomb;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.base.HierarchicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.LexicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Derivation;

/**
 * 
 * @author danielcer
 * 
 */
public final class RecombinationFilterFactory {
  static public final String CLASSICAL_TRANSLATION_MODEL = "classicaltranslationmodel";
  static public final String CLASSICAL_TRANSLATION_MODEL_MSD = "msdtranslationmodel";
  static public final String DTU_TRANSLATION_MODEL = "dtu";
  static public final String DTU_TRANSLATION_MODEL_MSD = "dtumsd";

  private RecombinationFilterFactory() {}

  /**
	 * 
	 */
  static public RecombinationFilter<Derivation<IString, String>> factory(
      List<Featurizer<IString, String>> featurizers, String rfName) {

    // TODO(spenceg) Migrate this code
    boolean msdRecombination = false;
    for (Featurizer<IString, String> featurizer : featurizers) {
      if (featurizer instanceof HierarchicalReorderingFeaturizer ||
          featurizer instanceof LexicalReorderingFeaturizer)
        msdRecombination = true;
    }
    if (msdRecombination) {
      if (rfName.equals(CLASSICAL_TRANSLATION_MODEL)
          || rfName.equals(CLASSICAL_TRANSLATION_MODEL_MSD)) {
        rfName = CLASSICAL_TRANSLATION_MODEL_MSD;
      } else if (rfName.equals(DTU_TRANSLATION_MODEL)
          || rfName.equals(DTU_TRANSLATION_MODEL_MSD)) {
        rfName = DTU_TRANSLATION_MODEL_MSD;
      } else {
        throw new UnsupportedOperationException(
            "Don't know how to handle recombination heuristic with MSD model: "
                + rfName);
      }
    }

    if (rfName.equals(CLASSICAL_TRANSLATION_MODEL)
        || rfName.equals(CLASSICAL_TRANSLATION_MODEL_MSD)) {
      List<RecombinationFilter<Derivation<IString, String>>> filters = new LinkedList<RecombinationFilter<Derivation<IString, String>>>();
      // maintain uniqueness of hypotheses that will result in different linear
      // distortion scores when extended
      // with future translation options.
      filters.add(new LinearDistortionRecombinationFilter<IString, String>());

      // maintain uniqueness of hypotheses that differ by the last N-tokens,
      // this being relevant to lg model scoring
      filters.add(new TranslationNgramRecombinationFilter(featurizers));

      // maintain uniqueness of hypotheses that differ in terms of foreign
      // sequence coverage
      filters.add(new ForeignCoverageRecombinationFilter<IString, String>());

      if (rfName.equals(CLASSICAL_TRANSLATION_MODEL_MSD))
        filters.add(new MSDRecombinationFilter(featurizers));

      return new CombinedRecombinationFilter<Derivation<IString, String>>(
          filters);

    } else if (rfName.equals(DTU_TRANSLATION_MODEL)
        || rfName.equals(DTU_TRANSLATION_MODEL_MSD)) {
      List<RecombinationFilter<Derivation<IString, String>>> filters = new LinkedList<RecombinationFilter<Derivation<IString, String>>>();
      filters.add(new LinearDistortionRecombinationFilter<IString, String>());
      filters.add(new TranslationNgramRecombinationFilter(featurizers));
      filters.add(new ForeignCoverageRecombinationFilter<IString, String>());
      filters.add(new DTURecombinationFilter<IString, String>());
      if (rfName.equals(DTU_TRANSLATION_MODEL_MSD))
        filters.add(new MSDRecombinationFilter(featurizers));
      return new CombinedRecombinationFilter<Derivation<IString, String>>(
          filters);
    }
    throw new RuntimeException(String.format(
        "Unrecognized recombination filter: %s", rfName));
  }
}
