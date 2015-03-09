package edu.stanford.nlp.mt.tm;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;


/**
 * Implements an abstract method for querying rules from a phrase
 * table given a source sequence.
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
abstract public class AbstractPhraseGenerator<TK, FV> implements
    TranslationModel<TK,FV> {
  
  protected RuleFeaturizer<TK, FV> phraseFeaturizer;

  /**
   * Constructor.
   * 
   * @param phraseFeaturizer
   */
  public AbstractPhraseGenerator(RuleFeaturizer<TK, FV> phraseFeaturizer) {
    this.phraseFeaturizer = phraseFeaturizer;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public List<ConcreteRule<TK,FV>> getRules(
      Sequence<TK> source, InputProperties sourceInputProperties, List<Sequence<TK>> targets, 
      int sourceInputId, Scorer<FV> scorer) {
    if (source == null || source.size() == 0) return new ArrayList<>(1);
    List<ConcreteRule<TK,FV>> opts = new ArrayList<>(source.size() * source.size() * 100);
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
        List<Rule<TK>> abstractOpts = this.query(foreignPhrase);
        if (abstractOpts != null) {
          for (Rule<TK> abstractOpt : abstractOpts) {
            opts.add(new ConcreteRule<TK,FV>(abstractOpt, 
                foreignCoverage, phraseFeaturizer, scorer, source, this
                .getName(), sourceInputId, sourceInputProperties));
          }
        }
      }
    }
    return opts;
  }

  /**
   * Return a list of rules for a source span.
   * 
   * @param sequence
   * @return
   */
  abstract public List<Rule<TK>> query(Sequence<TK> sequence);

  @Override
  abstract public int longestSourcePhrase();
  
  @Override
  abstract public int longestTargetPhrase();
  
  @Override
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer) {
    phraseFeaturizer = featurizer;
  }
}
