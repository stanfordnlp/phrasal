package edu.stanford.nlp.mt.decoder.efeat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.decoder.feat.AlignmentFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * Indicator features for aligned and unaligned tokens in phrase pairs.
 * 
 * @author Spence Green
 *
 */
public class DiscriminativeAlignmentFeaturizer2 implements AlignmentFeaturizer,
RuleFeaturizer<IString,String> {

  private static final String FEATURE_NAME = "Align";
  
  public DiscriminativeAlignmentFeaturizer2() { 
  }

  @Override
  public void initialize(Index<String> featureIndex) {
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(Featurizable<IString, String> f) {
    PhraseAlignment alignment = f.rule.abstractOption.alignment;
    final int tgtLength = f.targetPhrase.size();
    final int srcLength = f.sourcePhrase.size();
    List<Set<String>> s2t = new ArrayList<Set<String>>(srcLength);
    for (int i = 0; i < srcLength; ++i) {
      s2t.add(new HashSet<String>());
    }
    boolean[] fIsAligned = new boolean[srcLength];
    List<FeatureValue<String>> features = new LinkedList<FeatureValue<String>>();

    // Iterate over target side of phrase
    for (int i = 0; i < tgtLength; ++i) {
      int[] fIndices = alignment.t2s(i);
      String tgtWord = f.targetPhrase.get(i).toString();
      
      if (fIndices != null) {
        // This is hairy. We want to account for many-to-one alignments efficiently.
        // Therefore, copy all foreign tokens into the bucket for the first aligned
        // foreign index. Then we can iterate once over the source.
        int fInsertionIndex = -1;
        for (int fIndex : fIndices) {
          if (fInsertionIndex < 0) {
            fInsertionIndex = fIndex;
            s2t.get(fInsertionIndex).add(tgtWord);
          } else {
            String srcWord = f.sourcePhrase.get(fIndex).toString();
            s2t.get(fInsertionIndex).add(srcWord);
          }
          fIsAligned[fIndex] = true;
        }
      }
    }

    // Iterate over source side of phrase
    for (int i = 0; i < srcLength; ++i) {
      Set<String> tgtWords = s2t.get(i);
      String srcWord = f.sourcePhrase.get(i).toString();
      if ( ! fIsAligned[i]) {
        // Source deletion
      } else if (tgtWords.size() > 0){
        List<String> alignedWords = new ArrayList<String>(tgtWords.size() + 1);
        alignedWords.add(srcWord);
        alignedWords.addAll(tgtWords);
        Collections.sort(alignedWords);
        StringBuilder sb = new StringBuilder();
        int len = alignedWords.size();
        for (int j = 0; j < len; ++j) {
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
    return String.format("%s:%s", featureName, featureSuffix);
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    return null;
  }
}
