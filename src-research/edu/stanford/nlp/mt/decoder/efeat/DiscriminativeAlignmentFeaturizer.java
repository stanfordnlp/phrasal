package edu.stanford.nlp.mt.decoder.efeat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
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
  
  public DiscriminativeAlignmentFeaturizer() { 
    addUnalignedSourceWords = false;
    addUnalignedTargetWords = false;
    unseenThreshold = DEFAULT_UNSEEN_THRESHOLD;
  }
  
  public DiscriminativeAlignmentFeaturizer(String...args) {
    addUnalignedSourceWords = args.length > 0 ? Boolean.parseBoolean(args[0]) : false;
    addUnalignedTargetWords = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
    unseenThreshold = args.length > 2 ? Double.parseDouble(args[2]) : DEFAULT_UNSEEN_THRESHOLD;
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {
    this.featureIndex = featureIndex;
    featureCounter = featureIndex.isLocked() ? null : new ThreadsafeCounter<String>(100*featureIndex.size());
  }
  
  @Override
  public List<FeatureValue<String>> phraseListFeaturize(Featurizable<IString, String> f) {
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
      if (fIndices == null) {
        // Unaligned target word
        if (addUnalignedTargetWords) {
          String feature = FEATURE_NAME_TGT + ":" + eWord;
          features.add(new FeatureValue<String>(feature, 1.0));
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
      if ( ! fIsAligned[i]) {
        if (addUnalignedSourceWords) {
          String feature = makeFeatureString(FEATURE_NAME_SRC, fWord);
          features.add(new FeatureValue<String>(feature, 1.0));
        }
      } else if (eWords.size() > 0){
        List<String> alignedWords = new ArrayList<String>(eWords.size() + 1);
        alignedWords.add(fWord);
        alignedWords.addAll(eWords);
        Collections.sort(alignedWords);
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < alignedWords.size(); ++j) {
          if (j != 0) sb.append("-");
          sb.append(alignedWords.get(j));
        }
        String feature = makeFeatureString(FEATURE_NAME, sb.toString());
        features.add(new FeatureValue<String>(feature, 1.0));
      }
    }
    
    return features;
  }

  private String makeFeatureString(String featureName, String featureSuffix) {
    String featureString = String.format("%s:%s", featureName, featureSuffix);
    
    // Collect statistics and detect unseen events
    if (featureCounter == null) {
      // Test time
      if (featureIndex.indexOf(featureString) < 0) {
        // TODO(spenceg): Elaborate?
        featureString = featureName + ":UNK";
      }

    } else {
      // Training time
      double count = featureCounter.incrementCount(featureString);
      if (count <= unseenThreshold) {
        // TODO(spenceg): Elaborate?
        featureString = featureName + ":UNK";          
      }
    }
    return featureString;
  }
  
  
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void reset() {}

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    // TODO(spenceg) Return null if we re-factor the featurizer interface
    return phraseListFeaturize(f);
  }
  
  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return null;
  }
}
