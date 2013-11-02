package edu.stanford.nlp.mt.decoder.recomb;

import java.util.*;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.FactoryUtil;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizers;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.lm.LanguageModel;

/**
 * 
 * @author danielcer
 * 
 */
public class RecombinationFilterFactory {
  static public final String NO_RECOMBINATION = "norecombination";
  static public final String TRANSLATION_IDENTITY = "translationidentity";
  static public final String FOREIGN_COVERAGE = "foreigncoverage";
  static public final String LINEAR_DISTORTION = "lineardistortion";
  static public final String TRANSLATION_NGRAM = "translationngram";
  static public final String CLASSICAL_TRANSLATION_MODEL = "classicaltranslationmodel";
  static public final String CLASSICAL_TRANSLATION_MODEL_MSD = "msdtranslationmodel";
  static public final String CLASSICAL_TRANSLATION_MODEL_ALT = "ctm";
  static public final String CLASSICAL_TRANSLATION_MODEL_FINE = "fine";
  static public final String DTU_TRANSLATION_MODEL = "dtu";
  static public final String DTU_TRANSLATION_MODEL_MSD = "dtumsd";
  static public final String DEFAULT_RECOMBINATION_FILTER = TRANSLATION_IDENTITY;

  static public final String TRANSLATION_NGRAM_PARAMETER = "ngramsize";

  private RecombinationFilterFactory() {
  }

  /**
	 * 
	 */
  static public RecombinationFilter<Derivation<IString, String>> factory(
      List<Featurizer<IString, String>> featurizers,
      boolean msdRecombination, String... rfSpecs) {
    String rfName;
    if (rfSpecs.length == 0) {
      rfName = DEFAULT_RECOMBINATION_FILTER;
    } else {
      rfName = rfSpecs[0].toLowerCase();
    }
    System.err.println("Recombination name: " + rfName);

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

    Map<String, String> paramPairs = FactoryUtil.getParamPairs(rfSpecs);

    // default to a history window that is appropriate for the highest order lm
    List<LanguageModel<IString>> lgModels = Featurizers
        .extractNGramLanguageModels(featurizers);
    int ngramHistory = Integer.MAX_VALUE;
    String ngramParamStr = paramPairs.get(TRANSLATION_NGRAM_PARAMETER);
    if (ngramParamStr != null) {
      try {
        ngramHistory = Integer.parseInt(ngramParamStr);
      } catch (NumberFormatException e) {
        throw new RuntimeException(
            String
                .format(
                    "RecombinationFilter option %s:%s can not be converted into an integer value",
                    TRANSLATION_NGRAM_PARAMETER, ngramParamStr));
      }
    }

    if (rfName.equals(NO_RECOMBINATION)) {
      return new NoRecombination<Derivation<IString, String>>();
    } else if (rfName.equals(TRANSLATION_IDENTITY)) {
      // note that this is *surface* identity only
      return new TranslationIdentityRecombinationFilter<IString, String>();
    } else if (rfName.equals(FOREIGN_COVERAGE)) {
      return new ForeignCoverageRecombinationFilter<IString, String>();
    } else if (rfName.equals(LINEAR_DISTORTION)) {
      return new LinearDistortionRecombinationFilter<IString, String>();
    } else if (rfName.equals(TRANSLATION_NGRAM)) {
      return new TranslationNgramRecombinationFilter<IString, String>(lgModels,
          ngramHistory);
    } else if (rfName.equals(CLASSICAL_TRANSLATION_MODEL)
        || rfName.endsWith(CLASSICAL_TRANSLATION_MODEL_ALT)
        || rfName.equals(CLASSICAL_TRANSLATION_MODEL_MSD)) {
      List<RecombinationFilter<Derivation<IString, String>>> filters = new LinkedList<RecombinationFilter<Derivation<IString, String>>>();
      // maintain uniqueness of hypotheses that will result in different linear
      // distortion scores when extended
      // with future translation options.
      filters.add(new LinearDistortionRecombinationFilter<IString, String>());

      // maintain uniqueness of hypotheses that differ by the last N-tokens,
      // this being relevant to lg model scoring
      filters.add(new TranslationNgramRecombinationFilter<IString, String>(
          lgModels, ngramHistory));

      // maintain uniqueness of hypotheses that differ in terms of foreign
      // sequence coverage
      filters.add(new ForeignCoverageRecombinationFilter<IString, String>());

      if (rfName.equals(CLASSICAL_TRANSLATION_MODEL_MSD))
        filters.add(new MSDRecombinationFilter(featurizers));

      return new CombinedRecombinationFilter<Derivation<IString, String>>(
          filters);

    } else if (rfName.equals(CLASSICAL_TRANSLATION_MODEL_FINE)) {
      // Only recombine hypotheses that are identical, if coverage set and
      // linear distortion are the same:
      List<RecombinationFilter<Derivation<IString, String>>> filters = new LinkedList<RecombinationFilter<Derivation<IString, String>>>();
      filters
          .add(new TranslationIdentityRecombinationFilter<IString, String>());
      filters.add(new LinearDistortionRecombinationFilter<IString, String>());
      filters.add(new ForeignCoverageRecombinationFilter<IString, String>());
      return new CombinedRecombinationFilter<Derivation<IString, String>>(
          filters);
    } else if (rfName.equals(DTU_TRANSLATION_MODEL)
        || rfName.equals(DTU_TRANSLATION_MODEL_MSD)) {
      List<RecombinationFilter<Derivation<IString, String>>> filters = new LinkedList<RecombinationFilter<Derivation<IString, String>>>();
      filters.add(new LinearDistortionRecombinationFilter<IString, String>());
      filters.add(new TranslationNgramRecombinationFilter<IString, String>(
          lgModels, ngramHistory));
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
