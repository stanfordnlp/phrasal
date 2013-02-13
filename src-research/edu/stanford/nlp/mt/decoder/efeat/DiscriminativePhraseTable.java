package edu.stanford.nlp.mt.decoder.efeat;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.CacheableFeatureValue;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IOTools;
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

  private int[] srcWordToClassMap;
  private boolean mapSrcWord = false;
  private int[] tgtWordToClassMap;
  private boolean mapTgtWord = false;

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
    String srcClassFile = args.length > 3 ? args[3] : null;
    String tgtClassFile = args.length > 4 ? args[4] : null;
    if (srcClassFile != null) {
      srcWordToClassMap = loadWordClassMap(srcClassFile);
      mapSrcWord = true;
    }
    if (tgtClassFile != null) {
      tgtWordToClassMap = loadWordClassMap(tgtClassFile);
      mapTgtWord = true;
    }
  }

  private static int[] loadWordClassMap(String wordClassFile) {
    int[] wordMap = null;
    try {
      LineNumberReader reader = IOTools.getReaderFromFile(wordClassFile);
      int maxIdx = -2;
      // Find the maximum index
      for(String line; (line = reader.readLine()) != null;) {
        String[] toks = line.split("\\s+");
        assert toks.length == 2;
        int idx = IString.index.indexOf(toks[0]);
        if (idx > maxIdx) maxIdx = idx;
      }
      reader.close();

      // Map all words to classes, if that word exists in the index
      wordMap = new int[maxIdx+1];
      for (String line; (line = reader.readLine()) != null;) {
        String[] toks = line.trim().split("\\s+");
        assert toks.length == 2;
        int idx = IString.index.indexOf(toks[0]);
        assert idx < wordMap.length;
        if (idx >= 0) wordMap[idx] = Integer.parseInt(toks[1]);
      }
      reader.close();

    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not load: " + DiscriminativePhraseTable.class.getName());
    }
    return wordMap;
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

    String srcPhrase = mapSrcWord ? mapPhrase(f.foreignPhrase, srcWordToClassMap) :
      f.foreignPhrase.toString("_");
    String tgtPhrase = mapTgtWord ? mapPhrase(f.translatedPhrase, tgtWordToClassMap) :
      f.translatedPhrase.toString("_");

    if (doSource && doTarget) {
      String suffix = srcPhrase + ">"
          + tgtPhrase;
      fvalues.add(new CacheableFeatureValue<String>(
          makeFeatureString(FEATURE_NAME, SOURCE_AND_TARGET, suffix, f.foreignPhrase.size(), incrementCount), 
          1.0));

    } else if (doSource) {
      fvalues.add(new CacheableFeatureValue<String>(
          makeFeatureString(FEATURE_NAME, SOURCE, srcPhrase, f.foreignPhrase.size(), incrementCount), 
          1.0));

    } else if (doTarget) {
      fvalues.add(new CacheableFeatureValue<String>(
          makeFeatureString(FEATURE_NAME, TARGET, tgtPhrase, f.foreignPhrase.size(), incrementCount), 
          1.0));
    }
    return fvalues;
  }

  private static String mapPhrase(Sequence<IString> phrase,
      int[] wordToClassMap) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < phrase.size(); ++i) {
      if (i > 0) sb.append("_");
      int idx = phrase.get(i).id;
      if (idx >= 0 && idx < wordToClassMap.length) {
        sb.append(wordToClassMap[idx]);
      } else {
        sb.append("U");
      }
    }
    return sb.toString();
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
