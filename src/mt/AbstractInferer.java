package mt;

import java.util.LinkedList;
import java.util.List;

abstract public class AbstractInferer<TK, FV> implements Inferer<TK,FV> {
	final IncrementalFeaturizer<TK, FV> featurizer;
	final PhraseGenerator<TK> phraseGenerator;
	final Scorer<FV> scorer;
	final SearchHeuristic<TK,FV> heuristic;
	final RecombinationFilter<Hypothesis<TK,FV>> filter;


	abstract public List<RichTranslation<TK, FV>> nbest(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, int size);


	abstract public RichTranslation<TK, FV> translate(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace);

	protected AbstractInferer(AbstractInfererBuilder<TK, FV> builder) {
		featurizer = builder.incrementalFeaturizer;
		phraseGenerator = builder.phraseGenerator;
		scorer = builder.scorer;
		heuristic = builder.heuristic;
		filter = builder.filter;
	}

	/**
	 *
	 * @param hyp
	 * @return
	 */
	List<FeatureValue<FV>> collectFeatureValues(Hypothesis<TK,FV> hyp) {
		List<FeatureValue<FV>> features = new LinkedList<FeatureValue<FV>>();
		for ( ; hyp != null; hyp = hyp.preceedingHyp) {
			List<FeatureValue<FV>> localFeatures = hyp.localFeatures;
			if (localFeatures != null) {
				features.addAll(localFeatures);
			}
		}
		return features;
	}
}
