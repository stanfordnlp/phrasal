package mt.decoder.util;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.CoverageSet;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.decoder.feat.CombinedFeaturizer;
import mt.decoder.h.SearchHeuristic;

/**
 * @author Michel Galley
 *
 * @param <TK>
 */
public class DTUHypothesis<TK,FV> extends Hypothesis<TK,FV> {

  // TODO: complete

  /**
	 * 
	 * @param foreignSequence
	 * @param heuristic
	 */
  public DTUHypothesis(int translationId, Sequence<TK> foreignSequence, SearchHeuristic<TK,FV> heuristic,
                         List<List<ConcreteTranslationOption<TK>>> options) {
    super(translationId, foreignSequence, heuristic, options);
	}

  	/**
	 *
	 * @param translationOpt
	 * @param insertionPosition
	 * @param baseHyp
	 * @param featurizer
	 * @param scorer
	 * @param heuristic
	 */
	public DTUHypothesis(int translationId,
			ConcreteTranslationOption<TK> translationOpt,
			int insertionPosition,
			Hypothesis<TK,FV> baseHyp,
			CombinedFeaturizer<TK,FV> featurizer,
			Scorer<FV> scorer,
			SearchHeuristic<TK,FV> heuristic) {
    super(translationId, translationOpt, insertionPosition, baseHyp, featurizer, scorer, heuristic);
    /*
		this.length = (insertionPosition < baseHyp.length ?
				       baseHyp.length :  // internal insertion
			           insertionPosition + translationOpt.abstractOption.translation.size()); // edge insertion
		featurizable = new Featurizable<TK,FV>(this, translationId, featurizer.getNumberStatefulFeaturizers());
    localFeatures = featurizer.listFeaturize(featurizable);
		score = baseHyp.score + scorer.getIncrementalScore(localFeatures);
		h = baseHyp.h + heuristic.getHeuristicDelta(this, translationOpt.foreignCoverage);
		depth = baseHyp.depth + 1;
     */
	}


}
