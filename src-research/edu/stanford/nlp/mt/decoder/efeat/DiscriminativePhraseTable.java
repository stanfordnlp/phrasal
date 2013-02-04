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
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ThreadsafeCounter;
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

  private static final double DEFAULT_UNSEEN_THRESHOLD = 2.0;

  private final boolean doSource;
  private final boolean doTarget;
  private final double unseenThreshold;

  private Counter<String> featureCounter;
  private Index<String> featureIndex;
  
  private final boolean createOOVClasses;

  public DiscriminativePhraseTable() {
    doSource = true;
    doTarget = true;
    unseenThreshold = DEFAULT_UNSEEN_THRESHOLD;
    createOOVClasses = unseenThreshold > 0.0;
  }

  public DiscriminativePhraseTable(String... args) {
    doSource = args.length > 0 ? Boolean.parseBoolean(args[0]) : true;
    doTarget = args.length > 1 ? Boolean.parseBoolean(args[1]) : true;
    unseenThreshold = args.length > 2 ? Double.parseDouble(args[2]) : DEFAULT_UNSEEN_THRESHOLD;
    createOOVClasses = unseenThreshold > 0.0;
  }

  @Override
  public void initialize(Index<String> featureIndex) {
    this.featureIndex = featureIndex;
    featureCounter = !featureIndex.isLocked() && createOOVClasses ? 
        new ThreadsafeCounter<String>(100*featureIndex.size()) : null;
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(Featurizable<IString, String> f) {
    return featurizePhrase(f, true);
  }

  private List<FeatureValue<String>> featurizePhrase(
      Featurizable<IString, String> f, boolean incrementCount) {
    List<FeatureValue<String>> fvalues = new LinkedList<FeatureValue<String>>();

    if (doSource && doTarget) {
      String suffix = f.foreignPhrase.toString("_") + ">"
          + f.translatedPhrase.toString("_");
      fvalues.add(new CacheableFeatureValue<String>(
          makeFeatureString(FEATURE_NAME, SOURCE_AND_TARGET, suffix, f.foreignPhrase.size(), incrementCount), 
          1.0));

    } else if (doSource) {
      fvalues.add(new CacheableFeatureValue<String>(
          makeFeatureString(FEATURE_NAME, SOURCE, f.foreignPhrase.toString("_"), f.foreignPhrase.size(), incrementCount), 
          1.0));

    } else if (doTarget) {
      fvalues.add(new CacheableFeatureValue<String>(
          makeFeatureString(FEATURE_NAME, TARGET, f.translatedPhrase.toString("_"), f.foreignPhrase.size(), incrementCount), 
          1.0));
    }
    return fvalues;
  }

  private String makeFeatureString(String featurePrefix, String featureType, String value, 
      int length, boolean incrementCount) {
    String featureString = String.format("%s.%s:%s", featurePrefix, featureType, value);
    if ( ! createOOVClasses) return featureString;
    
    // Collect statistics and detect unseen events
    if (featureCounter == null) {
      // Test time
      if (featureIndex.indexOf(featureString) < 0) {
        // TODO(spenceg): Elaborate?
        featureString = String.format("%s.%s:UNK%d", featurePrefix, featureType, length);
      }

    } else {
      // Training time
      double count = incrementCount ? featureCounter.incrementCount(featureString) :
        featureCounter.getCount(featureString);
      if (count <= unseenThreshold) {
        // TODO(spenceg): Elaborate?
        featureString = String.format("%s.%s:UNK%d", featurePrefix, featureType, length);
      }
    }
    return featureString;
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
