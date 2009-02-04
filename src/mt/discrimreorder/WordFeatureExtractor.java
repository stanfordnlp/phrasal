package mt.discrimreorder;

import java.util.*;

public class WordFeatureExtractor implements FeatureExtractor {
  public List<String> extractFeatures(AlignmentMatrix matrix, TrainingExample example) {
    List<String> features = new ArrayList<String>();
    // 1. source words within a window around the current source position j
    //    the window is: d \in {-1, 0, -1}
    for (int d = -1; d <= 1; d++) {
      StringBuilder feature = new StringBuilder("SRC_");
      feature.append(d).append(":").append(matrix.getSourceWord(example.src_j+d));
      features.add(feature.toString());
  }

    // 2. target words within a window around the current target position i
    for (int d = -1; d <= 1; d++) {
      StringBuilder feature = new StringBuilder("TGT_");
      feature.append(d).append(":").append(matrix.getTargetWord(example.tgt_i+d));
      features.add(feature.toString());
    }

    // (the word-class features are not used for translation experiments.)

    return features;
  }
}
