package edu.stanford.nlp.mt.syntax.discrimreorder;

import java.util.*;

interface FeatureExtractor {
  public List<String> extractFeatures(AlignmentMatrix matrix, TrainingExample example);
}
