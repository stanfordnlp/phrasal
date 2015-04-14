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
    if (source == null || source.size() == 0) return new ArrayList<>(0);
    List<ConcreteRule<TK,FV>> concreteRules = new ArrayList<>(source.size() * source.size() * 100);
    int longestSourcePhrase = this.maxLengthSource();
    if (longestSourcePhrase < 0)
      longestSourcePhrase = -longestSourcePhrase;
    for (int i = 0, sz = source.size(); i < sz; i++) {
      for (int len = 1; len <= longestSourcePhrase; len++) {
        final int j = i + len;
        if (j > sz)
          break;
        CoverageSet sourceCoverage = new CoverageSet(sz);
        sourceCoverage.set(i, j);
        Sequence<TK> sourcePhrase = source.subsequence(i, j);
        List<Rule<TK>> rules = this.query(sourcePhrase);
        if (rules != null) {
          for (Rule<TK> ruleOpt : rules) {
            concreteRules.add(new ConcreteRule<TK,FV>(ruleOpt, 
                sourceCoverage, phraseFeaturizer, scorer, source, this
                .getName(), sourceInputId, sourceInputProperties));
          }
        }
      }
    }
    return concreteRules;
  }

  /**
   * Return a list of rules for a source span.
   * 
   * @param sequence
   * @return
   */
  abstract public List<Rule<TK>> query(Sequence<TK> sequence);

  @Override
  abstract public int maxLengthSource();
  
  @Override
  abstract public int maxLengthTarget();
  
  @Override
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer) {
    phraseFeaturizer = featurizer;
  }
}
