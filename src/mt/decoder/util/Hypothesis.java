package mt.decoder.util;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.CoverageSet;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.h.SearchHeuristic;

/**
 * 
 * Note: this class has a natural ordering that is
 * inconsistent with equals
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public class Hypothesis<TK,FV> implements Comparable<Hypothesis<TK,FV>>, State<Hypothesis<TK,FV>> {
	public static long nextId;
	
	// primitives
	public final long id;
	public final double score;
	public final double h;	
	public final int insertionPosition;
	public final int untranslatedTokens;
	public final int depth;
	public final int linearDistortion;
	public final int length;
	
	
	// non-primitives that already exist at the time of
	// hypothesis creation and just receive an additional
	// reference here
	public final ConcreteTranslationOption<TK> translationOpt;
	public final Sequence<TK> foreignSequence;
	
	// right now, translations are built up strictly in sequence.
	// however, we don't want to encourage people writing feature
	// functions to be dependent upon this fact.
	public final Hypothesis<TK,FV> preceedingHyp;
	
	// non-primitives created anew for each hypothesis 
	public final CoverageSet foreignCoverage;  		
	public final Featurizable<TK,FV> featurizable;
	
	public final List<FeatureValue<FV>> localFeatures;
	
	/**
	 * 
	 * @return
	 */
	public boolean isDone() {
		return untranslatedTokens == 0; 	
	}
	
	
	/**
	 * 
	 * @return
	 */
	public double finalScoreEstimate() {
		return score + h;
	}
	
	/**
	 * 
	 * @return
	 */
	public double score() {
		return score + h;
	}
	
	/**
	 * 
	 * @param foreignSequence
	 * @param heuristic
	 */
	public Hypothesis(int translationId, Sequence<TK> foreignSequence, SearchHeuristic<TK,FV> heuristic, List<ConcreteTranslationOption<TK>> options) {
		synchronized(this.getClass()) { id = nextId++; }
		score = 0; 
		h = heuristic.getInitialHeuristic(foreignSequence, options, translationId);
		insertionPosition = 0;
		length = 0;
		translationOpt = null;
		this.foreignSequence = foreignSequence;
		preceedingHyp = null;
		featurizable = null; 
		untranslatedTokens = foreignSequence.size();
		foreignCoverage = new CoverageSet(foreignSequence.size());
		localFeatures = null;
		depth = 0;
		linearDistortion = 0;
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
	public Hypothesis(int translationId,
			ConcreteTranslationOption<TK> translationOpt, 
			int insertionPosition,
			Hypothesis<TK,FV> baseHyp, 
			IncrementalFeaturizer<TK,FV> featurizer,
			Scorer<FV> scorer,
			SearchHeuristic<TK,FV> heuristic) {
		synchronized(this.getClass()) { this.id = nextId++; }
		this.insertionPosition = insertionPosition;
		this.translationOpt = translationOpt;
		this.preceedingHyp = baseHyp;
		this.foreignCoverage = baseHyp.foreignCoverage.clone();
		this.foreignCoverage.or(translationOpt.foreignCoverage);
		this.length = (insertionPosition < baseHyp.length ? 
				       baseHyp.length :  // internal insertion 
			           insertionPosition + translationOpt.abstractOption.translation.size()); // edge insertion
		foreignSequence = baseHyp.foreignSequence;
		untranslatedTokens = baseHyp.untranslatedTokens - translationOpt.abstractOption.foreign.size();
		linearDistortion = (baseHyp.translationOpt == null ? translationOpt.foreignPos : baseHyp.translationOpt.linearDistortion(translationOpt));
		featurizable = new Featurizable<TK,FV>(this, translationId);
		localFeatures = featurizer.listFeaturize(featurizable);
		score = (scorer.randomizeTag() ? r.nextDouble()*100 : baseHyp.score + scorer.getIncrementalScore(localFeatures));
		h = baseHyp.h + heuristic.getHeuristicDelta(this, translationOpt.foreignCoverage);
		depth = baseHyp.depth + 1;
	}
		
	static final Random r = new Random(1);
	/**
	 * 
	 * @param sbuf
	 * @param hyp
	 */
	private void injectSegmentationBuffer(StringBuffer sbuf, Hypothesis<TK,FV> hyp) {
		if (hyp.preceedingHyp != null) injectSegmentationBuffer(sbuf, hyp.preceedingHyp);
		sbuf.append("\t").append(hyp.translationOpt.abstractOption.translation).append(" ");
		sbuf.append(hyp.translationOpt.foreignCoverage).append(" ");
		sbuf.append(Arrays.toString(hyp.translationOpt.abstractOption.scores));
		sbuf.append("\n");
	}
	
	/**
	 * 
	 * @param verbosity
	 * @return
	 */
	public String toString(int verbosity) {
		StringBuffer sbuf = new StringBuffer();
		if (featurizable != null) {
			sbuf.append(featurizable.partialTranslation);
		} else {
			sbuf.append("<NONE>");
		}
		sbuf.append("  ").append(foreignCoverage);
		sbuf.append(String.format(" [%.3f h: %.3f]", score+h, h));
		if (verbosity > 0) {
			sbuf.append("\nSegmentation:\n");
			injectSegmentationBuffer(sbuf, this);
		}
		return sbuf.toString();
	}
	
	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public int compareTo(Hypothesis<TK, FV> competitor) {
		int cmp = (int) Math.signum(competitor.finalScoreEstimate() - finalScoreEstimate());
		if (cmp != 0) {
			return cmp;
		}
		return (int)(id - competitor.id);
	}


	@Override
	public State<Hypothesis<TK, FV>> parent() {
		return preceedingHyp;
	}


	@Override
	public double partialScore() {
		return score;
	}


	@Override
	public int depth() {
		return depth;
	}
	
	@Override
	public int hashCode() {
		return (int)id;
	}
}
