package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * XXX - in progress
 * 
 * @author danielcer
 * 
 */
public class PhraseBreakEffectiveEntropy implements
    IncrementalFeaturizer<IString, String> {
  IString[] possibleNextTargetWords = new IString[0];

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
    Set<IString> possibleNextTargetWords = new HashSet<IString>();
    for (ConcreteTranslationOption<IString> opt : options) {
      possibleNextTargetWords.add(opt.abstractOption.translation.get(0));
    }

    this.possibleNextTargetWords = possibleNextTargetWords
        .toArray(this.possibleNextTargetWords);
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  public void reset() {
  }
}
