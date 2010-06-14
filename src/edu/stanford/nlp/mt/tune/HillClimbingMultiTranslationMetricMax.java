package edu.stanford.nlp.mt.tune;

import java.util.*;

import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.*;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class HillClimbingMultiTranslationMetricMax<TK, FV> implements MultiTranslationMetricMax<TK, FV> {
	public static final String DEBUG_PROPERTY = "HillClimbingMultiTranslationMetricMaxDebug";
	public static final boolean DEBUG = true; // XXX Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	
	final EvaluationMetric<TK,FV> metric;
	final EvaluationMetric<TK,FV> subMetric;
	
	final GreedyMultiTranslationMetricMax<TK,FV> greedyMetrixMax;
	
	/**
	 * 
	 */
	public HillClimbingMultiTranslationMetricMax(EvaluationMetric<TK,FV> metric) {
		this.metric = metric;
		this.greedyMetrixMax = new GreedyMultiTranslationMetricMax<TK, FV>(metric);
		this.subMetric = null;
	}

  @SuppressWarnings("unused")
	public HillClimbingMultiTranslationMetricMax(EvaluationMetric<TK,FV> metric, EvaluationMetric<TK, FV> subMetric) {
		this.metric = metric;
		this.greedyMetrixMax = new GreedyMultiTranslationMetricMax<TK, FV>(metric);
		this.subMetric = subMetric;
	}
	
	@Override
	public List<ScoredFeaturizedTranslation<TK, FV>> maximize(
			NBestListContainer<TK, FV> nbest) {
		
		List<ScoredFeaturizedTranslation<TK,FV>> selected = greedyMetrixMax.maximize(nbest);
		List<List<ScoredFeaturizedTranslation<TK,FV>>> nbestLists = nbest.nbestLists();
		
		IncrementalEvaluationMetric<TK,FV> incrementalMetric = metric.getIncrementalMetric();
		IncrementalEvaluationMetric<TK,FV> incrementalSubMetric = (subMetric != null ? subMetric.getIncrementalMetric() : null);
		for (ScoredFeaturizedTranslation<TK,FV> featurizedTranslation : selected) {
			incrementalMetric.add(featurizedTranslation);
			if (incrementalSubMetric != null) incrementalSubMetric.add(featurizedTranslation);
		}
		
		int nbestListsSize = nbestLists.size();
		int iter = 0;
		for (int changes = nbestListsSize; changes != 0 && iter < 25; iter++) { // XXX
			changes = 0;
			for (int i = 0; i < nbestListsSize; i++) {
				List<ScoredFeaturizedTranslation<TK,FV>> nbestList = nbestLists.get(i);
				ScoredFeaturizedTranslation<TK,FV> bestFTrans = null;
				double bestScore = Double.NaN;
				double bestScoreSub = Double.NaN;
				//int tI = -1;
				for (ScoredFeaturizedTranslation<TK,FV> ftrans : nbestList) { //tI++;
					incrementalMetric.replace(i, ftrans);
					if (subMetric != null) {
            assert(incrementalSubMetric != null);
						incrementalSubMetric.replace(i, ftrans);
					}
					double score = incrementalMetric.score();
				//	System.err.printf("bestScore(%d): %f score: %f \n", bestI, bestScore,  score);
					if (bestScore != bestScore || bestScore < score) {
						bestFTrans = ftrans;
						bestScore = score;
						if (subMetric != null) {
							bestScoreSub = incrementalSubMetric.score();
						}
					} else if (bestScore == score && subMetric != null) {
						double subMetricScore = incrementalSubMetric.score();
						if (bestScoreSub < subMetricScore) {
							bestScoreSub = subMetricScore;
							bestFTrans = ftrans;
						}
					}
				}
				incrementalMetric.replace(i, bestFTrans);
				if (incrementalSubMetric != null) incrementalSubMetric.replace(i, bestFTrans);
				if  (selected.get(i) != bestFTrans) {
					changes++;
					selected.set(i, bestFTrans);
				}
			}
			if (DEBUG) {
  			/*IncrementalEvaluationMetric<TK,FV> iMetric = metric.getIncrementalMetric();
  			for (ScoredFeaturizedTranslation<TK, FV> trans : selected) {
  				iMetric.add(trans);				
  			}
  			if (iMetric.score() != incrementalMetric.score()) {

  				/*System.err.printf("cnt: %d null cnt: %d\n", ((TERMetric.TERIncrementalMetric)incrementalMetric).cnt, ((TERMetric.TERIncrementalMetric)incrementalMetric).nullCnt);
  				System.err.printf("cnt: %d null cnt: %d\n", ((TERMetric.TERIncrementalMetric)iMetric).cnt, ((TERMetric.TERIncrementalMetric)iMetric).nullCnt);
  				 * /
  				throw new RuntimeException(String.format("%f!=%f\n", incrementalMetric.score(), iMetric.score()));
  			} */
  			
				System.err.printf("%d: score: %.5f changes: %d\n", iter, incrementalMetric.score(), changes);
				/*for (ScoredFeaturizedTranslation<TK,FV> t : selected) {
					System.err.printf("%s\n", (t == null ? t : t.translation));					
				}*/
			}
		}
		
		return selected;
	}

}
