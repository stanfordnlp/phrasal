package edu.stanford.nlp.mt.base;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.PhraseGenerator;
import edu.stanford.nlp.mt.decoder.util.Scorer;

/**
 * Implements an abstract method for querying rules from a phrase
 * table given a source sequence.
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
abstract public class AbstractPhraseGenerator<TK, FV> implements
    PhraseGenerator<TK,FV>, PhraseTable<TK> {
  protected RuleFeaturizer<TK, FV> phraseFeaturizer;

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public List<ConcreteRule<TK,FV>> getRules(
      Sequence<TK> source, List<Sequence<TK>> targets, int sourceInputId, Scorer<FV> scorer) {
    List<ConcreteRule<TK,FV>> opts = new LinkedList<ConcreteRule<TK,FV>>();
    int sequenceSz = source.size();
    int longestForeignPhrase = this.longestSourcePhrase();
    if (longestForeignPhrase < 0)
      longestForeignPhrase = -longestForeignPhrase;
    for (int startIdx = 0; startIdx < sequenceSz; startIdx++) {
      for (int len = 1; len <= longestForeignPhrase; len++) {
        int endIdx = startIdx + len;
        if (endIdx > sequenceSz)
          break;
        CoverageSet foreignCoverage = new CoverageSet(sequenceSz);
        foreignCoverage.set(startIdx, endIdx);
        Sequence<TK> foreignPhrase = source.subsequence(startIdx, endIdx);
        List<Rule<TK>> abstractOpts = this
            .query(foreignPhrase);
        if (abstractOpts == null)
          continue;
        for (Rule<TK> abstractOpt : abstractOpts) {
          opts.add(new ConcreteRule<TK,FV>(abstractOpt,
              foreignCoverage, phraseFeaturizer, scorer, source, this
                  .getName(), sourceInputId));
        }
      }
    }
    return opts;
  }

  public AbstractPhraseGenerator(
      RuleFeaturizer<TK, FV> phraseFeaturizer) {
    this.phraseFeaturizer = phraseFeaturizer;
  }

  @Override
  abstract public String getName();

  @Override
  abstract public List<Rule<TK>> query(
      Sequence<TK> sequence);

  @Override
  abstract public int longestSourcePhrase();
  
  @Override
  abstract public int longestTargetPhrase();

  @Override
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer) {
    phraseFeaturizer = featurizer;
  }
}
