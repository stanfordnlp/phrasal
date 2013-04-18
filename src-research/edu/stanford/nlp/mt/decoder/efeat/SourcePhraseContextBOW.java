package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;

import edu.stanford.nlp.mt.base.ARPALanguageModel;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.InsertedStartEndToken;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * 
 */
public class SourcePhraseContextBOW implements
    IncrementalFeaturizer<IString, String>,
    IsolatedPhraseFeaturizer<IString, String> {
  public static final String PREFIX = "CBOW";
  public static final String LEFT_CONTEXT = "l:";
  public static final String RIGHT_CONTEXT = "r:";
  public static final int DEFAULT_WINDOW_SIZE = 7;

  final int windowSize;

  final Map<ConcreteTranslationOption<IString,String>, List<FeatureValue<String>>> featureCache = new HashMap<ConcreteTranslationOption<IString,String>, List<FeatureValue<String>>>();

  public SourcePhraseContextBOW() {
    windowSize = DEFAULT_WINDOW_SIZE;
  }

  public SourcePhraseContextBOW(String... args) {
    windowSize = Integer.parseInt(args[0]);
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteTranslationOption<IString,String>> options, Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> fList = featureCache.get(f.option);

    if (fList != null)
      return fList;

    fList = new LinkedList<FeatureValue<String>>();
    Sequence<IString> wrappedSource = new InsertedStartEndToken<IString>(
        f.sourceSentence, ARPALanguageModel.START_TOKEN,
        ARPALanguageModel.END_TOKEN);
    int sz = wrappedSource.size();
    int startBoundry = f.sourcePosition + 1;
    int endBoundry = f.sourcePosition + f.sourcePhrase.size() + 1;
    String trans = f.sourcePhrase.toString("_") + ">"
        + f.targetPhrase.toString("_");

    for (int i = 0; i < windowSize; i++) {
      int lWordPos = startBoundry - i - 1;
      int rWordPos = endBoundry + i;
      if (lWordPos >= 0) {
        fList.add(new FeatureValue<String>(PREFIX + LEFT_CONTEXT + windowSize
            + ":" + wrappedSource.get(lWordPos) + "|" + trans, 1.0));
      }

      if (rWordPos < sz) {
        fList.add(new FeatureValue<String>(PREFIX + RIGHT_CONTEXT + windowSize
            + ":" + wrappedSource.get(rWordPos) + "|" + trans, 1.0));
      }
    }

    featureCache.put(f.option, fList);
    return fList;
  }

  @Override
  public void reset() {
    featureCache.clear();
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

  @Override
  public void initialize(Index<String> featureIndex) {
  }
}
