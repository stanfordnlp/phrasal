package mt.base;

import java.util.*;


/**
 * 
 * @author danielcer
 *
 * @param <T>
 */
public class TranslationOption<T> {
	public final float[] scores;
	public final String[] phraseScoreNames;
	public final RawSequence<T> translation;
	public final RawSequence<T> foreign;
	public final PhraseAlignment alignment;
	public final boolean forceAdd;
	private int hashCode = -1;
	
	/**
	 * 
	 * @param scores
	 * @param translation
	 * @param foreign
	 */
	public TranslationOption(float[] scores, String[] phraseScoreNames, RawSequence<T> translation, RawSequence<T> foreign, PhraseAlignment alignment) {
		this.alignment = alignment;
		this.scores = Arrays.copyOf(scores, scores.length);
		this.translation = translation;
		this.foreign = foreign;
		this.phraseScoreNames = phraseScoreNames;
		this.forceAdd = false;
	}
	
	public TranslationOption(float[] scores, String[] phraseScoreNames, RawSequence<T> translation, RawSequence<T> foreign, PhraseAlignment alignment, boolean forceAdd) {
		this.alignment = alignment;
		this.scores = Arrays.copyOf(scores, scores.length);
		this.translation = translation;
		this.foreign = foreign;
		this.phraseScoreNames = phraseScoreNames;
		this.forceAdd = forceAdd;
	}
	
	@Override
	public String toString() {
		StringBuffer sbuf = new StringBuffer();
		sbuf.append(String.format("TranslationOption: \"%s\" scores: %s\n", translation, Arrays.toString(scores)));
		return sbuf.toString();
	}
	
	@Override
	public int hashCode() {
		if (hashCode == -1) hashCode = super.hashCode();
		return hashCode;
	}
}
