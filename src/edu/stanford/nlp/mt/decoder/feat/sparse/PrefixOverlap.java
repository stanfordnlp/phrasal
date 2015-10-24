package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

public class PrefixOverlap extends DerivationFeaturizer<IString, String> {

  private static final String FEATURE_PREFIX = "POV";
  
  @Override
  public void initialize(int sourceInputId, Sequence<IString> source) {
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = new ArrayList<>();
    // The prefix supplied with the source segment.
    final int prefixLength = f.derivation.prefixLength;
    // The length of the partial translation
    final int hypLength = f.targetSequence.size();

    // Overlap
    if (f.targetPosition < prefixLength && hypLength > prefixLength) {
      features.add(new FeatureValue<>(FEATURE_PREFIX, 1.0));
      if (f.targetPhrase.size() > 2) {
        // Reward for phrases that do more than span the join point
        features.add(new FeatureValue<>(FEATURE_PREFIX + ":lg", 1.0));
      }
//        // Compute left and right overlap
//        int leftOverlap = prefixLength - f.targetPosition;
//        int rightOverlap = hypLength - prefixLength;
//        if (leftOverlap > rightOverlap) {
//          features.add(new FeatureValue<>(FEATURE_PREFIX + ":left", 1.0));
//
//        } else if (leftOverlap < rightOverlap) {
//          features.add(new FeatureValue<>(FEATURE_PREFIX + ":right", 1.0));
//
//        } else {
//          features.add(new FeatureValue<>(FEATURE_PREFIX + ":equal", 1.0));
//        }
//      }

    } else if (hypLength == prefixLength) {
      // Right edge of rule at split point
      features.add(new FeatureValue<String>(FEATURE_PREFIX + ":pnl", 1.0));
    }
    return features;
  }

}
