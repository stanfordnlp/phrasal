package mt.base;

import java.util.*;

import mt.decoder.feat.IsolatedPhraseFeaturizer;
import mt.decoder.util.Scorer;

import edu.stanford.nlp.util.IString;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public class IdentityPhraseGenerator<TK,FV> extends AbstractPhraseGenerator<TK,FV> implements DynamicPhraseGenerator<TK> {
	static public final String PHRASE_TABLE_NAMES = "IdentityPhraseGenerator(Dyn)";
	static public final String DEFAULT_SCORE_NAMES[] = {"p_i(t|f)"};
	static public final float SCORE_VALUES[] = {(float)1.0};
	
	private final String[] scoreNames;
	private final SequenceFilter<TK> filter;
	
	/**
	 * 
	 * @param filter
	 */
	public IdentityPhraseGenerator(IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer, SequenceFilter<TK> filter ) {
		super(phraseFeaturizer, scorer);
		this.filter = filter; 
		scoreNames = DEFAULT_SCORE_NAMES;
	}
	
	/**
	 * 
	 * @param filter
	 * @param scoreName
	 */
	public IdentityPhraseGenerator(IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer, SequenceFilter<TK> filter, String scoreName) {
		super(phraseFeaturizer, scorer);
		this.filter = filter;
		scoreNames = new String[]{scoreName};
	}
	
	/**
	 * 
	 */
	public IdentityPhraseGenerator(IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer) {
		super(phraseFeaturizer, scorer);
		this.filter = null;
		scoreNames = DEFAULT_SCORE_NAMES;
	}
	
	public IdentityPhraseGenerator(IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer, String scoreName) {
		super(phraseFeaturizer, scorer);
		this.filter = null;
		scoreNames = new String[]{scoreName};
	}
	
	@Override
	public String getName() {
		return PHRASE_TABLE_NAMES;
	}

	@Override
	public String[] getPhrasalScoreNames() {
		return scoreNames;
	}

	@Override
	public List<TranslationOption<TK>>  getTranslationOptions(Sequence<TK> sequence) {
		List<TranslationOption<TK>> list = new LinkedList<TranslationOption<TK>>();
		RawSequence<TK> raw = new RawSequence<TK>(sequence);
		if (filter == null || filter.accepts(raw)) {
			list.add(new TranslationOption<TK>(SCORE_VALUES, scoreNames, raw, raw, new IString("I-I")));
		}
		return list;
	}

	@Override
	public int longestForeignPhrase() {
		return Integer.MAX_VALUE;
	}
}
