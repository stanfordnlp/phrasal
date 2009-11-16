package mt.base;

import java.util.*;


/**
 * 
 * @author Michel Galley
 *
 * @param <T>
 */
public class DTUOption<T> extends TranslationOption<T> {

  public final RawSequence<T>[] dtus;

	public DTUOption(float[] scores, String[] phraseScoreNames, RawSequence<T>[] dtus, RawSequence<T> foreign, PhraseAlignment alignment) {
		super(scores, phraseScoreNames, null, foreign, alignment);
    this.dtus = dtus;
    // TODO: check dtus
  }
	
	public DTUOption(float[] scores, String[] phraseScoreNames, RawSequence<T>[] dtus, RawSequence<T> foreign, PhraseAlignment alignment, boolean forceAdd) {
		super(scores, phraseScoreNames, null, foreign, alignment, forceAdd);
    this.dtus = dtus;
	}
	
	@Override
	public String toString() {
		StringBuffer sbuf = new StringBuffer();
		sbuf.append(String.format("TranslationOption: \"%s\" scores: %s\n", translation, Arrays.toString(scores)));
		return sbuf.toString();
	}
}
