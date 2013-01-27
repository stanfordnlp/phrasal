package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TranslationOption;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * 
 */
public class PhraseTranslationWordPairs implements
    IncrementalFeaturizer<IString, String>,
    IsolatedPhraseFeaturizer<IString, String> {

  public static String FEATURE_PREFIX = "PTWP";
  final Map<TranslationOption<IString>, List<FeatureValue<String>>> featureCache = new HashMap<TranslationOption<IString>, List<FeatureValue<String>>>();

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return getFeatureList(f.option.abstractOption);
  }

  public List<FeatureValue<String>> getFeatureList(
      TranslationOption<IString> opt) {

    List<FeatureValue<String>> blist = featureCache.get(opt);

    if (blist != null)
      return blist;

    blist = new LinkedList<FeatureValue<String>>();

    for (IString srcWord : opt.foreign) {
      for (IString trgWord : opt.translation) {
        blist.add(new FeatureValue<String>(FEATURE_PREFIX + ":" + srcWord
            + "=>" + trgWord, 1.0));
      }
    }

    featureCache.put(opt, blist);
    return blist;
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return featurize(f);
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<IString, String> f) {
    return listFeaturize(f);
  }

  public void reset() {
    featureCache.clear();
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {
  }
}
