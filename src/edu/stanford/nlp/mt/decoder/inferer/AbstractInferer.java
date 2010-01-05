package edu.stanford.nlp.mt.decoder.inferer;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.ConstrainedOutputSpace;
import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;

abstract public class AbstractInferer<TK, FV> implements Inferer<TK,FV> {
	protected final CombinedFeaturizer<TK, FV> featurizer;
	protected final PhraseGenerator<TK> phraseGenerator;
	protected final Scorer<FV> scorer;
	protected final SearchHeuristic<TK,FV> heuristic;
	protected final RecombinationFilter<Hypothesis<TK,FV>> filter;


	abstract public List<RichTranslation<TK, FV>> nbest(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets, int size);
	abstract public RichTranslation<TK, FV> translate(Sequence<TK> foreign, int translationId, ConstrainedOutputSpace<TK,FV> constrainedOutputSpace, List<Sequence<TK>> targets);

	protected AbstractInferer(AbstractInfererBuilder<TK, FV> builder) {
		featurizer = builder.incrementalFeaturizer;
		phraseGenerator = builder.phraseGenerator;
		scorer = builder.scorer;
		heuristic = builder.heuristic;
		filter = builder.filter;
	}

	/**
	 *
	 */
	protected List<FeatureValue<FV>> collectFeatureValues(Hypothesis<TK,FV> hyp) {
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
