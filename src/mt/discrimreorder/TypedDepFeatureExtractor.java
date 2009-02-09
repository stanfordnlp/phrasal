package mt.discrimreorder;

import java.util.*;
import java.io.*;

public class TypedDepFeatureExtractor implements FeatureExtractor {
  boolean HEAD_DEP_DIR;
  
  static final Set<String> OPTS = new HashSet<String>();
  static {
    OPTS.addAll(
      Arrays.asList(
        "head_dep_dir"
        ));
  }


  public TypedDepFeatureExtractor(Properties props) throws Exception {
    HEAD_DEP_DIR  = Boolean.parseBoolean(props.getProperty("head_dep_dir", "false"));

    System.out.println();
    System.out.println("TypedDepFeatureExtractor stats:");
    System.out.println("HEAD_DEP_DIR = " + HEAD_DEP_DIR);
    System.out.println("---------------------------");
  }

  public List<String> extractFeatures(AlignmentMatrix matrix, TrainingExample example) {
    List<String> features = new ArrayList<String>();
    int gIdx = -1;
    String headDep = null;
    
    if (HEAD_DEP_DIR) {

      // skip the boundary cases
      if (example.src_j > 0 && example.src_j < matrix.f.length-1) {
        String srcj_word = matrix.getSourceWord(example.src_j);

        gIdx = matrix.d2g[example.src_j];
        headDep = matrix.tDeps[gIdx][example.src_j];
        System.err.println("SRC_J="+example.src_j);
        System.err.println("mapped Head="+gIdx);

        // 1. "DEP_TO_HEAD" : from the src word j, find the type of dependency to the head
        StringBuilder feature = new StringBuilder("HEADDEP:");
        feature.append(headDep);
        features.add(feature.toString());
        System.err.println(feature.toString());

        // 2. "HEAD_DIR" : from the src word j, which way does the head go
        feature = new StringBuilder("HEADDIR:");
        String direction = "";
        if (gIdx > 0 && gIdx < matrix.f.length-1) {
          direction = (gIdx > example.src_j) ? "forward":"backward";
          feature.append(direction);
          features.add(feature.toString());
          System.err.println(feature.toString());
        }

        // conjunction
        feature = new StringBuilder("HEAD_DEP_DIR:");
        if (gIdx > 0 && gIdx < matrix.f.length-1) {
          feature.append(headDep).append("-").append(direction);
          features.add(feature.toString());
        System.err.println(feature.toString());
        }
      }
    }

    return features;
  }
}
