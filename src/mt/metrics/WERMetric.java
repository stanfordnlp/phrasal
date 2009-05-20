package mt.metrics;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.EditDistance;
import mt.base.NBestListContainer;
import mt.base.RawSequence;
import mt.base.ScoredFeaturizedTranslation;
import mt.base.Sequence;
import mt.decoder.recomb.RecombinationFilter;
import mt.decoder.util.State;

public class WERMetric<TK, FV> extends AbstractMetric<TK, FV> {
	final List<List<Sequence<TK>>> referencesList;
	
	public WERMetric(List<List<Sequence<TK>>> referencesList) {
		this.referencesList = referencesList;
	}
	
	@Override
	public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric() {
		return new WERIncrementalMetric();
	}

	@Override
	public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric(
			NBestListContainer<TK, FV> nbestList) {
		throw new UnsupportedOperationException();
	}

	@Override
	public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double maxScore() {		
		return 1.0;
	}
	
	public class WERIncrementalMetric implements IncrementalEvaluationMetric<TK,FV> {
		List<Double> wers = new ArrayList<Double>();  
		EditDistance editDistance = new EditDistance();
		double sum = 0;
		
		private double minimumEditDistance(int id, Sequence<TK> seq) {
			Object[] outArr = (new RawSequence<TK>(seq)).elements;
			double minEd = Double.POSITIVE_INFINITY;
			for (Sequence<TK> ref : referencesList.get(id)) {
				Object[] refArr =  (new RawSequence<TK>(ref)).elements;
				double ed = editDistance.score(outArr, refArr);
				if (minEd > ed) minEd = ed;
			}
			return minEd;
		}
		
		@Override
		public IncrementalEvaluationMetric<TK, FV> add(
				ScoredFeaturizedTranslation<TK, FV> trans) {
			int id = wers.size();
			double minEd = minimumEditDistance(id,trans.translation);
			wers.add(-minEd);
			sum += -minEd;
			
			return this;
		}

		@Override
		public double maxScore() {
			return 1.0;
		}

		@Override
		public IncrementalEvaluationMetric<TK, FV> replace(int id,
				ScoredFeaturizedTranslation<TK, FV> trans) {
			double newMinEd = minimumEditDistance(id,trans.translation);
			sum -= wers.get(id);
			sum += newMinEd;
			wers.set(id, newMinEd);
			return this;
		}

		@Override
		public double score() {
			int wersSz = wers.size();
			if (wersSz == 0) return 0;
			return (sum/(wersSz+1));
		}

		@Override
		public int size() {
			return wers.size();
		}

		@Override
		public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int depth() {
			throw new UnsupportedOperationException();
		}

		@Override
		public State<IncrementalEvaluationMetric<TK, FV>> parent() {
			throw new UnsupportedOperationException();
		}

		@Override
		public double partialScore() {
			throw new UnsupportedOperationException();
		}
		
		public WERIncrementalMetric clone() {
      return new WERIncrementalMetric();
    }
	}
}