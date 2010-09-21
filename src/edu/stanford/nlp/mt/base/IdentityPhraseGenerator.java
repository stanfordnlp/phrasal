package edu.stanford.nlp.mt.base;

import java.util.*;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class IdentityPhraseGenerator<TK, FV> extends
    AbstractPhraseGenerator<TK, FV> implements DynamicPhraseGenerator<TK> {
  static public final String PHRASE_TABLE_NAMES = "IdentityPhraseGenerator(Dyn)";
  static public final String DEFAULT_SCORE_NAMES[] = { "p_i(t|f)" };
  static public final float SCORE_VALUES[] = { (float) 1.0 };

  // do we need to account for "(0) (1)", etc?
  static public final PhraseAlignment DEFAULT_ALIGNMENT = PhraseAlignment
      .getPhraseAlignment("I-I");

  private final String[] scoreNames;
  private final SequenceFilter<TK> filter;

  /**
	 * 
	 */
  public IdentityPhraseGenerator(
      IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer,
      SequenceFilter<TK> filter) {
    super(phraseFeaturizer, scorer);
    this.filter = filter;
    scoreNames = DEFAULT_SCORE_NAMES;
  }

  /**
	 * 
	 */
  public IdentityPhraseGenerator(
      IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer,
      SequenceFilter<TK> filter, String scoreName) {
    super(phraseFeaturizer, scorer);
    this.filter = filter;
    scoreNames = new String[] { scoreName };
  }

  /**
	 * 
	 */
  public IdentityPhraseGenerator(
      IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer) {
    super(phraseFeaturizer, scorer);
    this.filter = null;
    scoreNames = DEFAULT_SCORE_NAMES;
  }

  public IdentityPhraseGenerator(
      IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer,
      String scoreName) {
    super(phraseFeaturizer, scorer);
    this.filter = null;
    scoreNames = new String[] { scoreName };
  }

  @Override
  public String getName() {
    return PHRASE_TABLE_NAMES;
  }

  @Override
  public List<TranslationOption<TK>> getTranslationOptions(Sequence<TK> sequence) {
    List<TranslationOption<TK>> list = new LinkedList<TranslationOption<TK>>();
    RawSequence<TK> raw = new RawSequence<TK>(sequence);
    if (filter == null || filter.accepts(raw)) {
      list.add(new TranslationOption<TK>(SCORE_VALUES, scoreNames, raw, raw,
          DEFAULT_ALIGNMENT));
    }
    return list;
  }

  @Override
  public int longestForeignPhrase() {
    return -Integer.MAX_VALUE;
  }

  @Override
  public void setCurrentSequence(Sequence<TK> foreign,
      List<Sequence<TK>> tranList) {
    // no op
  }
}
