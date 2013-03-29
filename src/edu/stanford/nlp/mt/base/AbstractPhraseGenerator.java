package edu.stanford.nlp.mt.base;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
abstract public class AbstractPhraseGenerator<TK, FV> implements
    PhraseGenerator<TK,FV>, PhraseTable<TK> {
  protected final IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer;

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public List<ConcreteTranslationOption<TK,FV>> translationOptions(
      Sequence<TK> sequence, List<Sequence<TK>> targets, int translationId, Scorer<FV> scorer) {
    List<ConcreteTranslationOption<TK,FV>> opts = new LinkedList<ConcreteTranslationOption<TK,FV>>();
    int sequenceSz = sequence.size();
    int longestForeignPhrase = this.longestForeignPhrase();
    if (longestForeignPhrase < 0)
      longestForeignPhrase = -longestForeignPhrase;
    for (int startIdx = 0; startIdx < sequenceSz; startIdx++) {
      for (int len = 1; len <= longestForeignPhrase; len++) {
        int endIdx = startIdx + len;
        if (endIdx > sequenceSz)
          break;
        CoverageSet foreignCoverage = new CoverageSet(sequenceSz);
        foreignCoverage.set(startIdx, endIdx);
        Sequence<TK> foreignPhrase = sequence.subsequence(startIdx, endIdx);
        List<TranslationOption<TK>> abstractOpts = this
            .getTranslationOptions(foreignPhrase);
        if (abstractOpts == null)
          continue;
        for (TranslationOption<TK> abstractOpt : abstractOpts) {
          opts.add(new ConcreteTranslationOption<TK,FV>(abstractOpt,
              foreignCoverage, phraseFeaturizer, scorer, sequence, this
                  .getName(), translationId));
        }
      }
    }
    return opts;
  }

  public AbstractPhraseGenerator(
      IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer) {
    this.phraseFeaturizer = phraseFeaturizer;
  }

  @Override
  abstract public String getName();

  @Override
  abstract public List<TranslationOption<TK>> getTranslationOptions(
      Sequence<TK> sequence);

  @Override
  abstract public int longestForeignPhrase();

}
