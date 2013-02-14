package edu.stanford.nlp.mt.decoder.efeat;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.mt.base.CacheableFeatureValue;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.AlignmentFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ThreadsafeCounter;
import edu.stanford.nlp.util.Index;

/**
 * Indicator features for aligned and unaligned tokens in phrase pairs.
 * 
 * @author Spence Green
 *
 */
public class DiscriminativeAlignmentFeaturizer implements AlignmentFeaturizer,
IncrementalFeaturizer<IString, String>, IsolatedPhraseFeaturizer<IString,String> {

  private static final String FEATURE_NAME = "Align";
  private static final String FEATURE_NAME_TGT = "UnAlignTgt";
  private static final String FEATURE_NAME_SRC = "UnAlignSrc";

  private static final double DEFAULT_UNSEEN_THRESHOLD = 2.0;

  private final boolean addUnalignedSourceWords;
  private final boolean addUnalignedTargetWords;
  private final double unseenThreshold;

  private Counter<String> featureCounter;
  private Index<String> featureIndex;
  
  private final boolean createOOVClasses;

  private Map<String,String> srcWordToClassMap;
  private boolean mapSrcWord = false;
  private Map<String,String> tgtWordToClassMap;
  private boolean mapTgtWord = false;
  
  public DiscriminativeAlignmentFeaturizer() { 
    addUnalignedSourceWords = false;
    addUnalignedTargetWords = false;
    unseenThreshold = DEFAULT_UNSEEN_THRESHOLD;
    createOOVClasses = unseenThreshold > 0.0;
  }

  public DiscriminativeAlignmentFeaturizer(String...args) {
    addUnalignedSourceWords = args.length > 0 ? Boolean.parseBoolean(args[0]) : false;
    addUnalignedTargetWords = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
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

  private static Map<String, String> loadWordClassMap(String wordClassFile) {
    LineNumberReader reader = IOTools.getReaderFromFile(wordClassFile);
    Map<String,String> map = new HashMap<String,String>(60000);
    try {
      for (String line; (line = reader.readLine()) != null;) {
        String[] toks = line.trim().split("\\s+");
        if (toks.length == 2) {
          map.put(toks[0], toks[1]);
        } else {
          System.err.printf("%s: Ignoring line %s (line: %d)%n", DiscriminativeAlignmentFeaturizer.class.getName(),
              line.trim(), reader.getLineNumber());
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not load: " + wordClassFile);
    }
    return map;
  }

  @Override
  public void initialize(Index<String> featureIndex) {
    this.featureIndex = featureIndex;
    featureCounter = !featureIndex.isLocked() && createOOVClasses ? 
        new ThreadsafeCounter<String>(100*featureIndex.size()) : null;
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(Featurizable<IString, String> f) {
    return featurizeRule(f, true);
  }

  private List<FeatureValue<String>> featurizeRule(
      Featurizable<IString, String> f, boolean incrementCount) {
    PhraseAlignment alignment = f.option.abstractOption.alignment;
    final int eLength = f.translatedPhrase.size();
    final int fLength = f.foreignPhrase.size();
    List<Set<String>> f2e = new ArrayList<Set<String>>(fLength);
    for (int i = 0; i < fLength; ++i) {
      f2e.add(new HashSet<String>());
    }
    boolean[] fIsAligned = new boolean[fLength];
    List<FeatureValue<String>> features = new LinkedList<FeatureValue<String>>();

    // Iterate over target side of phrase
    for (int i = 0; i < eLength; ++i) {
      int[] fIndices = alignment.e2f(i);
      String eWord = f.translatedPhrase.get(i).toString();
      if (mapTgtWord) eWord = mapToClass(eWord, tgtWordToClassMap);
      
      if (fIndices == null) {
        // Unaligned target word
        if (addUnalignedTargetWords) {
          String feature = makeFeatureString(FEATURE_NAME_TGT, eWord, 0, incrementCount);
          features.add(new CacheableFeatureValue<String>(feature, 1.0));
        }

      } else {
        // This is hairy. We want to account for many-to-one alignments efficiently.
        // Therefore, copy all foreign tokens into the bucket for the first aligned
        // foreign index. Then we can iterate once over the source.
        int fInsertionIndex = -1;
        for (int fIndex : fIndices) {
          if (fInsertionIndex < 0) {
            fInsertionIndex = fIndex;
            f2e.get(fInsertionIndex).add(eWord);
          } else {
            String fWord = f.foreignPhrase.get(fIndex).toString();
            if (mapSrcWord) fWord = mapToClass(fWord, srcWordToClassMap);
            f2e.get(fInsertionIndex).add(fWord);
          }
          fIsAligned[fIndex] = true;
        }
      }
    }

    // Iterate over source side of phrase
    for (int i = 0; i < fLength; ++i) {
      Set<String> eWords = f2e.get(i);
      String fWord = f.foreignPhrase.get(i).toString();
      if (mapSrcWord) fWord = mapToClass(fWord, srcWordToClassMap);
      if ( ! fIsAligned[i]) {
        if (addUnalignedSourceWords) {
          String feature = makeFeatureString(FEATURE_NAME_SRC, fWord, 0, incrementCount);
          features.add(new CacheableFeatureValue<String>(feature, 1.0));
        }
      } else if (eWords.size() > 0){
        List<String> alignedWords = new ArrayList<String>(eWords.size() + 1);
        alignedWords.add(fWord);
        alignedWords.addAll(eWords);
        Collections.sort(alignedWords);
        StringBuilder sb = new StringBuilder();
        int len = alignedWords.size();
        for (int j = 0; j < len; ++j) {
          if (j != 0) sb.append("-");
          sb.append(alignedWords.get(j));
        }
        String feature = makeFeatureString(FEATURE_NAME, sb.toString(), fLength, incrementCount);
        features.add(new CacheableFeatureValue<String>(feature, 1.0));
      }
    }
    return features;
  }

  private static String mapToClass(String word, Map<String, String> wordToClassMap) {
    return wordToClassMap.containsKey(word) ? wordToClassMap.get(word) : "UNK";
  }

  private String makeFeatureString(String featureName, String featureSuffix, int fLength, boolean incrementCount) {
    String featureString = String.format("%s:%s", featureName, featureSuffix);
    if ( ! createOOVClasses) return featureString;
    
    // Collect statistics and detect unseen events
    if (featureCounter == null) {
      // Test time
      if (featureIndex.indexOf(featureString) < 0) {
        // TODO(spenceg): Elaborate?
        featureString = String.format("%s:UNK%d", featureName, fLength);
      }

    } else {
      // Training time
      double count = incrementCount ? featureCounter.incrementCount(featureString) 
          : featureCounter.getCount(featureString);
      if (count <= unseenThreshold) {
        // TODO(spenceg): Elaborate?
        featureString = String.format("%s:UNK%d", featureName, fLength);
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
