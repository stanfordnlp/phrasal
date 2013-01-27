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
public class LogSourceLengthToTargetLengthRatio implements
    IncrementalFeaturizer<IString, String>,
    IsolatedPhraseFeaturizer<IString, String> {
  public static final String FEATURE_NAME = "LLR";
  public static final String SRC_TAG = ":s";
  public static final String TRG_TAG = ":t";
  final Map<TranslationOption<IString>, List<FeatureValue<String>>> featureCache = new HashMap<TranslationOption<IString>, List<FeatureValue<String>>>();

  public final boolean lexicalized;
  public static final boolean DEFAULT_LEXICALIZED = false;

  public LogSourceLengthToTargetLengthRatio() {
    lexicalized = DEFAULT_LEXICALIZED;
  }

  public LogSourceLengthToTargetLengthRatio(String... args) {
    lexicalized = (args.length >= 1 ? Boolean.parseBoolean(args[0])
        : DEFAULT_LEXICALIZED);
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    if (lexicalized)
      return null;
    return new FeatureValue<String>(FEATURE_NAME, Math.log(f.foreignPhrase
        .size()) - Math.log(f.translatedPhrase.size()));
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    if (!lexicalized)
      return null;
    List<FeatureValue<String>> blist = featureCache
        .get(f.option.abstractOption);

    if (f.hyp != null && blist == null)
      throw new RuntimeException();
    if (blist != null)
      return blist;

    blist = new LinkedList<FeatureValue<String>>();

    double logRatio = Math.log(f.foreignPhrase.size())
        - Math.log(f.translatedPhrase.size());
    for (IString srcToken : f.option.abstractOption.foreign) {
      blist.add(new FeatureValue<String>(FEATURE_NAME + SRC_TAG + ":"
          + srcToken, logRatio));
    }

    for (IString trgToken : f.option.abstractOption.translation) {
      blist.add(new FeatureValue<String>(FEATURE_NAME + TRG_TAG + ":"
          + trgToken, logRatio));
    }

    featureCache.put(f.option.abstractOption, blist);
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
