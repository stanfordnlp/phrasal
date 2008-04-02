package mt.tune;

import java.util.*;

import mt.base.IString;
import mt.base.NBestListContainer;
import mt.base.ScoredFeaturizedTranslation;
import mt.decoder.recomb.RecombinationFilter;
import mt.decoder.util.Scorer;
import mt.decoder.util.State;
import mt.metrics.*;
import mt.metrics.BLEUMetric.BLEUIncrementalMetric;

/**
 * 
 * @author danielcer
 *
 */
public class MarginRescaleEvaluationMetric implements EvaluationMetric<IString, String> {
	final EvaluationMetric<IString,String> lossMetric;
	final Scorer<String> scorer;
	final boolean inverted;
	final boolean maxScore; 
	double evalScoreCap = Double.POSITIVE_INFINITY;
	
	
	/**
	 * 
	 * @param lossMetric
	 * @param scorer
	 * @param inverted
	 */
	public MarginRescaleEvaluationMetric(EvaluationMetric<IString,String> lossMetric, Scorer<String> scorer, boolean inverted) {
		this.lossMetric = lossMetric;
		this.scorer = scorer;
		this.inverted = inverted;
		this.maxScore = false;
	}
	
	public MarginRescaleEvaluationMetric(EvaluationMetric<IString,String> lossMetric, Scorer<String> scorer, boolean inverted, boolean maxScore) {
		this.lossMetric = lossMetric;
		this.scorer = scorer;
		this.inverted = inverted;
		this.maxScore = maxScore;
	}
	
	public MarginRescaleEvaluationMetric(EvaluationMetric<IString,String> lossMetric, Scorer<String> scorer, boolean inverted, boolean maxScore, double scoreLimit) {
		this.lossMetric = lossMetric;
		this.scorer = scorer;
		this.inverted = inverted;
		this.maxScore = maxScore;
	}
	
	
	/**
	 * 
	 * @param lossMetric
	 * @param scorer
	 */
	public MarginRescaleEvaluationMetric(EvaluationMetric<IString,String> lossMetric, Scorer<String> scorer) {
		this.lossMetric = lossMetric;
		this.scorer = scorer;
		inverted = maxScore = false;
	}
	
	@Override
	public IncrementalEvaluationMetric<IString, String> getIncrementalMetric() {
		return new MarginRescaleEvaluationIncMetric();
	}

	@Override
	public IncrementalEvaluationMetric<IString, String> getIncrementalMetric(
			NBestListContainer<IString, String> nbestList) {
		return new MarginRescaleEvaluationIncMetric(nbestList);
	}
	
	private class MarginRescaleEvaluationIncMetric implements IncrementalEvaluationMetric<IString,String> {
		final IncrementalEvaluationMetric<IString, String> lossIncMetric;
		final List<ScoredFeaturizedTranslation<IString,String>> translations = new ArrayList<ScoredFeaturizedTranslation<IString,String>>();
		final double lossMaxScore;
		double classifierScore = 0;
		
		
		public MarginRescaleEvaluationIncMetric() {
			if (lossMetric != null) {
				lossIncMetric = lossMetric.getIncrementalMetric();
				lossMaxScore = lossMetric.maxScore();
			} else {
				lossIncMetric = null;
				lossMaxScore = 0;
			}
		}
		
		/**
		 * 
		 * @param nbestList
		 */
		public MarginRescaleEvaluationIncMetric(NBestListContainer<IString, String> nbestList) {
			if (lossMetric != null) {
				lossIncMetric = lossMetric.getIncrementalMetric(nbestList);
				lossMaxScore = lossMetric.maxScore();
			} else {
				lossIncMetric = null;
				lossMaxScore = 0;
			}
		}
		
		@Override
		public IncrementalEvaluationMetric<IString, String> add(
				ScoredFeaturizedTranslation<IString, String> trans) {
			if (lossIncMetric != null) lossIncMetric.add(trans);
			translations.add(trans);
			if (trans != null) {
				if (scorer != null) classifierScore += scorer.getIncrementalScore(trans.features);
		    }
			return this;
		}

		@Override
		public double maxScore() {
			return Double.POSITIVE_INFINITY;
		}

		@Override
		public IncrementalEvaluationMetric<IString, String> replace(int index,
				ScoredFeaturizedTranslation<IString, String> trans) {
			if (translations.get(index) != null) {
				if (scorer != null) classifierScore -= scorer.getIncrementalScore(translations.get(index).features);
			}
			translations.set(index, trans);
			if (trans != null) {
				if (scorer != null) classifierScore += scorer.getIncrementalScore(trans.features);
			}
			if (lossIncMetric != null) lossIncMetric.replace(index, trans);
			return this;
		}

		@Override
		public double score() {
			double score;
			if (maxScore) { 
				double mul= ((BLEUIncrementalMetric)lossIncMetric).getMultiplier();
				double evalScore = lossIncMetric.score();
				double pSlack = Math.min(evalScore, Math.max(evalScore - classifierScore,0));
				lossIncMetric.size();
				score = (lossIncMetric != null ? 10*evalScore + classifierScore : classifierScore);
				// score = (lossIncMetric != null ? Math.max(Math.min(classifierScore, mul), 0) + 3*lossIncMetric.score() : classifierScore);
			} else {
				
				double eScore = (lossIncMetric != null ? lossIncMetric.score() : 0);
				if (eScore >= evalScoreCap) return Double.NEGATIVE_INFINITY;
				score = (lossIncMetric != null ? classifierScore - eScore : classifierScore);
			}
			if (inverted) {
				return -score;
			} else {
				return score;
			}
		}

		
		
		@Override
		public int size() {
			return translations.size();
		}

		@Override
		public int compareTo(IncrementalEvaluationMetric<IString, String> o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public State<IncrementalEvaluationMetric<IString, String>> parent() {
			throw new UnsupportedOperationException();
		}

		@Override
		public double partialScore() {
			throw new UnsupportedOperationException();
		}
		
		public IncrementalEvaluationMetric<IString, String> clone() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int depth() {
			throw new UnsupportedOperationException();
		}
		
	}

	@Override
	public RecombinationFilter<IncrementalEvaluationMetric<IString, String>> getIncrementalMetricRecombinationFilter() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double maxScore() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double score(
			List<ScoredFeaturizedTranslation<IString, String>> sequences) {
		throw new UnsupportedOperationException();
	}

}
