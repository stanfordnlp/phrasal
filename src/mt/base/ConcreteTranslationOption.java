package mt.base;

import java.util.*;

import mt.decoder.feat.IsolatedPhraseFeaturizer;
import mt.decoder.util.Scorer;
import mt.PseudoMoses;

/**
 * 
 * @author danielcer
 *
 * @param <T>
 */
public class ConcreteTranslationOption<T> implements Comparable<ConcreteTranslationOption<T>> {
	public final TranslationOption<T> abstractOption;
	public final CoverageSet foreignCoverage;
	public final String phraseTableName;
	public final int foreignPos;
	public final double isolationScore;
	
	/**
	 * 
	 * @param <FV>
	 * @param abstractOption
	 * @param foreignCoverage
	 * @param phraseFeaturizer
	 * @param scorer
	 * @param phraseTableName
	 */
	public <FV> ConcreteTranslationOption(TranslationOption<T> abstractOption, CoverageSet foreignCoverage, 
			IsolatedPhraseFeaturizer<T, FV> phraseFeaturizer, Scorer<FV> scorer, Sequence<T> foreignSequence, String phraseTableName, int translationId) {
		this.abstractOption = abstractOption;
		this.foreignCoverage = foreignCoverage;
		this.phraseTableName = phraseTableName;
		this.foreignPos = foreignCoverage.nextSetBit(0);
		Featurizable<T, FV> f = new Featurizable<T, FV>(foreignSequence, this, translationId);
    List<FeatureValue<FV>> features = phraseFeaturizer.phraseListFeaturize(f);
		this.isolationScore = scorer.getIncrementalScore(features);
	}
	
	@Override
	public String toString() {
		StringBuffer sbuf = new StringBuffer();
		sbuf.append("ConcreteTranslationOption:\n");
		sbuf.append(String.format("\tAbstractOption: %s\n", abstractOption.toString().replaceAll("\n", "\n\t")));
		sbuf.append(String.format("\tForeignCoverage: %s\n", foreignCoverage));
		sbuf.append(String.format("\tPhraseTable: %s\n", phraseTableName));
		return sbuf.toString();
	}
	
	public int linearDistortion(ConcreteTranslationOption<T> opt) {
		int nextForeignToken = PseudoMoses.withGaps ?
      foreignCoverage.length(): (foreignPos + abstractOption.foreign.size());
		return Math.abs(nextForeignToken - opt.foreignPos);
	}
	
	public int signedLinearDistortion(ConcreteTranslationOption<T> opt) {
		int nextForeignToken = foreignPos + abstractOption.foreign.size();
		return nextForeignToken - opt.foreignPos;
	}

	@Override
	public int compareTo(ConcreteTranslationOption<T> o) {
		return (int)Math.signum(o.isolationScore - this.isolationScore);
	}
	
}
