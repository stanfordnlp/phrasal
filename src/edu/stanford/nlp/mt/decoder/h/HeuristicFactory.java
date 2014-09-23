package edu.stanford.nlp.mt.decoder.h;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.IString;

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
      RuleFeaturizer<IString, String> featurizer,
      String... hSpecs) {
    String hName;
    if (hSpecs.length == 0) {
      hName = DEFAULT_HEURISTIC;
    } else {
      hName = hSpecs[0].toLowerCase();
    }

    switch (hName) {
      case NULL_HEURISTIC:
        return new NullHeuristic<IString, String>();
      case ISOLATED_PHRASE_SOURCE_COVERAGE:
        return new IsolatedPhraseForeignCoverageHeuristic<IString, String>(
            featurizer);
      case ISOLATED_DTU_SOURCE_COVERAGE:
        return new DTUIsolatedPhraseForeignCoverageHeuristic<IString, String>(
            featurizer);
      case OPTIMISTIC_FOREIGN_COVERAGE:
        return new OptimisticForeignCoverageHeuristic<IString, String>();
    }

    throw new RuntimeException(String.format("Unknown search heuristic '%s'\n",
        hName));
  }

}
