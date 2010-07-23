package edu.stanford.nlp.mt.tune;

import java.util.*;

import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.metrics.*;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class GreedyMultiTranslationMetricMax<TK, FV> implements MultiTranslationMetricMax<TK, FV> {
	final EvaluationMetric<TK,FV> metric;
	
	/**
	 * 
	 */
	public GreedyMultiTranslationMetricMax(EvaluationMetric<TK,FV> metric) {
		this.metric = metric;
	}
	
	@Override
	public List<ScoredFeaturizedTranslation<TK, FV>> maximize(
			NBestListContainer<TK, FV> nbest) {
		List<ScoredFeaturizedTranslation<TK,FV>> selected = new LinkedList<ScoredFeaturizedTranslation<TK,FV>>();
		List<List<ScoredFeaturizedTranslation<TK,FV>>> nbestLists = nbest.nbestLists();
		IncrementalEvaluationMetric<TK,FV> incrementalMetric = metric.getIncrementalMetric();
		
		for (List<ScoredFeaturizedTranslation<TK,FV>> nbestList : nbestLists) {
			ScoredFeaturizedTranslation<TK,FV> localBest = null;
			double bestScore = Double.NaN;
			if (!nbestList.isEmpty()) incrementalMetric.add(nbestList.get(0));
			for (ScoredFeaturizedTranslation<TK,FV> featurizedTranslation : nbestList) {
				incrementalMetric.replace(incrementalMetric.size()-1, featurizedTranslation);
				double score = incrementalMetric.score();
				if (bestScore != bestScore || bestScore < score) {
					bestScore = score;
					localBest = featurizedTranslation;
				}
			}
			//System.out.printf("selected: %s\n", localBest);
			selected.add(localBest);
		}	
		return selected;
	}
}
