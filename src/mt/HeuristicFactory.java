package mt;

/**
 * 
 * @author Daniel Cer
 *
 */
public class HeuristicFactory {

	public static final String NULL_HEURISTIC = "nullheuristic";
	public static final String OPTIMISTIC_FOREIGN_COVERAGE = "optimisticcoverage";
	public static final String ISOLATED_PHRASE_FOREIGN_COVERAGE = "isophrase";
	public static final String DEFAULT_HEURISTIC = ISOLATED_PHRASE_FOREIGN_COVERAGE;

	public static SearchHeuristic<IString,String> factory(IsolatedPhraseFeaturizer<IString, String> featurizer, Scorer<String> scorer, String... hSpecs) {
		String hName;
		if (hSpecs.length == 0) {
			hName = DEFAULT_HEURISTIC;
		} else {
			hName = hSpecs[0].toLowerCase();
		}
		
		if (hName.equals(NULL_HEURISTIC)) {
			return new NullHeuristic<IString,String>();
		} else if (hName.equals(ISOLATED_PHRASE_FOREIGN_COVERAGE)) {
			return new IsolatedPhraseForeignCoverageHeuristic<IString, String>(featurizer, scorer);
		} else if (hName.equals(OPTIMISTIC_FOREIGN_COVERAGE)) {
			return new OptimisticForeignCoverageHeuristic<IString, String>();
		}
		
		throw new RuntimeException(String.format(
				"Unknown search heuristic '%s'\n", hName));	
	}

}
