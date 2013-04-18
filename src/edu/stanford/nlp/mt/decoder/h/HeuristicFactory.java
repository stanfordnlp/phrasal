package edu.stanford.nlp.mt.decoder.h;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;

/**
 * 
 * @author Daniel Cer
 * 
 */
public class HeuristicFactory {

  public static final String NULL_HEURISTIC = "nullheuristic";
  public static final String OPTIMISTIC_FOREIGN_COVERAGE = "optimisticcoverage";
  public static final String ISOLATED_PHRASE_SOURCE_COVERAGE = "isophrase";
  public static final String ISOLATED_DTU_SOURCE_COVERAGE = "isodtu";
  public static final String DEFAULT_HEURISTIC = ISOLATED_PHRASE_SOURCE_COVERAGE;

  public static SearchHeuristic<IString, String> factory(
      IsolatedPhraseFeaturizer<IString, String> featurizer,
      String... hSpecs) {
    String hName;
    if (hSpecs.length == 0) {
      hName = DEFAULT_HEURISTIC;
    } else {
      hName = hSpecs[0].toLowerCase();
    }

    if (hName.equals(NULL_HEURISTIC)) {
      return new NullHeuristic<IString, String>();
    } else if (hName.equals(ISOLATED_PHRASE_SOURCE_COVERAGE)) {
      return new IsolatedPhraseForeignCoverageHeuristic<IString, String>(
          featurizer);
    } else if (hName.equals(ISOLATED_DTU_SOURCE_COVERAGE)) {
      return new DTUIsolatedPhraseForeignCoverageHeuristic<IString, String>(
          featurizer);
    } else if (hName.equals(OPTIMISTIC_FOREIGN_COVERAGE)) {
      return new OptimisticForeignCoverageHeuristic<IString, String>();
    }

    throw new RuntimeException(String.format("Unknown search heuristic '%s'\n",
        hName));
  }

}
