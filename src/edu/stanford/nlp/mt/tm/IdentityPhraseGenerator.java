package edu.stanford.nlp.mt.tm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;

/**
 * Generate identity translations for specific source spans.
 * 
 * @author Spence Green
 *
 */
public class IdentityPhraseGenerator<TK,FV> implements TranslationModel<TK, FV> {

  public static final String PHRASE_TABLE_NAME = IdentityPhraseGenerator.class.getName();

  private static final PhraseAlignment DEFAULT_ALIGNMENT = PhraseAlignment
      .getPhraseAlignment(PhraseAlignment.MONOTONE_ALIGNMENT);

  private final String[] featureNames = new String[0];
  private final float[] featureValues = new float[0];
  protected RuleFeaturizer<TK, FV> phraseFeaturizer;

  @Override
  public List<ConcreteRule<TK, FV>> getRules(Sequence<TK> source, InputProperties sourceInputProperties,
      int sourceInputId, Scorer<FV> scorer) {

    final List<ConcreteRule<TK,FV>> ruleList = new ArrayList<>(source.size());

    for (int i = 0, sz = source.size(); i < sz; ++i) {
      TK token = source.get(i);
      if (TokenUtils.isNumericOrPunctuationOrSymbols(token.toString())) {
        Sequence<TK> span = source.subsequence(i, i+1);
        Rule<TK> rule = new Rule<TK>(featureValues, featureNames, span, span,
            DEFAULT_ALIGNMENT, PHRASE_TABLE_NAME);
        CoverageSet sourceCoverage = new CoverageSet(sz);
        sourceCoverage.set(i, i+1);
        ruleList.add(new ConcreteRule<TK,FV>(rule, 
            sourceCoverage, phraseFeaturizer, scorer, source, sourceInputId, sourceInputProperties));
      }
    }

    return ruleList;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  @Override
  public int maxLengthSource() {
    return 1;
  }

  @Override
  public int maxLengthTarget() {
    return 1;
  }

  @Override
  public List<String> getFeatureNames() {
    return Arrays.asList(featureNames);
  }

  @Override
  public String getName() {
    return PHRASE_TABLE_NAME;
  }

  @Override
  public void setName(String name) {}

  @Override
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer) {
    this.phraseFeaturizer = featurizer;
  }
}
