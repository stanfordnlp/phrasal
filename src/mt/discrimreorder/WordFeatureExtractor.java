package mt.discrimreorder;

import java.util.*;
import java.io.*;

public class WordFeatureExtractor implements FeatureExtractor {
  boolean SRCJ, TGT, SRCJ2, SRCJ_TGT, SRCJ_SRCJ2;
  int WINDOW;


  static final Set<String> OPTS = new HashSet<String>();
  static {
    OPTS.addAll(
      Arrays.asList(
        "src_j",
        "tgt_i",
        "src_j2",
        "src_j_tgt",
        "src_j_src_j2",
        "window"
        ));
  }


  public WordFeatureExtractor(Properties props) throws Exception {
    SRCJ  = Boolean.parseBoolean(props.getProperty("src_j", "false"));
    TGT  = Boolean.parseBoolean(props.getProperty("tgt_i", "false"));
    SRCJ2  = Boolean.parseBoolean(props.getProperty("src_j2", "false"));
    SRCJ_TGT = Boolean.parseBoolean(props.getProperty("src_j_tgt", "false"));
    SRCJ_SRCJ2 = Boolean.parseBoolean(props.getProperty("src_j_src_j2", "false"));
    WINDOW  = Integer.parseInt(props.getProperty("window", "1"));

    System.out.println();
    System.out.println("WordFeatureExtractor stats:");
    System.out.println("SRCJ = " + SRCJ);
    System.out.println("TGT = " + TGT);
    System.out.println("SRCJ2 = " + SRCJ2);
    System.out.println("SRCJ_TGT = " + SRCJ_TGT);
    System.out.println("SRCJ_SRCJ2 = " + SRCJ_SRCJ2);
    System.out.println("WINDOW = " + WINDOW);
    System.out.println("---------------------------");
  }

  public List<String> extractFeatures(AlignmentMatrix matrix, TrainingExample example) {
    List<String> features = new ArrayList<String>();
    // 1. source words within a window around the current source position j
    //    the window is: d \in {-1, 0, -1}
    if (SRCJ)
      for (int d = -WINDOW; d <= WINDOW; d++) {
        StringBuilder feature = new StringBuilder("SRCJ_");
        feature.append(d).append(":").append(matrix.getSourceWord(example.src_j+d));
        features.add(feature.toString());
      }

    // 2. target words within a window around the current target position i
    if (TGT)
      for (int d = -WINDOW; d <= WINDOW; d++) {
        StringBuilder feature = new StringBuilder("TGT_");
        feature.append(d).append(":").append(matrix.getTargetWord(example.tgt_i+d));
        features.add(feature.toString());
      }

    if (SRCJ2)
      for (int d = -WINDOW; d <= WINDOW; d++) {
        StringBuilder feature = new StringBuilder("SRCJ2_");
        feature.append(d).append(":").append(matrix.getSourceWord(example.src_jprime+d));
        features.add(feature.toString());
      }

    // some conjunction features
    if (SRCJ_TGT) {
      StringBuilder feature = new StringBuilder("SRCJ_TGT:");
      feature.append(matrix.getSourceWord(example.src_j)).append("-").append(matrix.getTargetWord(example.tgt_i));
      features.add(feature.toString());
    }

    if (SRCJ_SRCJ2) {
      StringBuilder feature = new StringBuilder("SRCJ_SRCJ2:");
      feature.append(matrix.getSourceWord(example.src_j)).append("-").append(matrix.getSourceWord(example.src_jprime));
      features.add(feature.toString());
    }

    return features;
  }
}
