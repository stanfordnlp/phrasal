package edu.stanford.nlp.mt.decoder.efeat;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.CacheableFeatureValue;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * @author Spence Green
 * 
 */
public class DiscriminativePhraseTable implements IncrementalFeaturizer<IString,String>, IsolatedPhraseFeaturizer<IString, String> {
  public static final String FEATURE_NAME = "DiscPT";
  public static final String SOURCE = "src";
  public static final String TARGET = "trg";
  public static final String SOURCE_AND_TARGET = "s+t";

  private final boolean doSource;
  private final boolean doTarget;
  
  public DiscriminativePhraseTable() {
    doSource = true;
    doTarget = true;
  }

  public DiscriminativePhraseTable(String... args) {
    doSource = args.length > 0 ? Boolean.parseBoolean(args[0]) : true;
    doTarget = args.length > 1 ? Boolean.parseBoolean(args[1]) : true;
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {}

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> fvalues = new LinkedList<FeatureValue<String>>();

    String srcPhrase = f.foreignPhrase.toString("_");
    String tgtPhrase = f.translatedPhrase.toString("_");
    
    if (doSource && doTarget) {
      String suffix = srcPhrase + ">"
          + tgtPhrase;
      fvalues.add(new CacheableFeatureValue<String>(
          makeFeatureString(FEATURE_NAME, SOURCE_AND_TARGET, suffix, f.foreignPhrase.size()), 
          1.0));

    } else if (doSource) {
      fvalues.add(new CacheableFeatureValue<String>(
          makeFeatureString(FEATURE_NAME, SOURCE, srcPhrase, f.foreignPhrase.size()), 
          1.0));

    } else if (doTarget) {
      fvalues.add(new CacheableFeatureValue<String>(
          makeFeatureString(FEATURE_NAME, TARGET, tgtPhrase, f.foreignPhrase.size()), 
          1.0));
    }
    return fvalues;
  }

  private String makeFeatureString(String featurePrefix, String featureType, String value, 
      int length) {
    return String.format("%s.%s:%s", featurePrefix, featureType, value);
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void initialize(
      List<ConcreteTranslationOption<IString, String>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public void reset() {
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

}
