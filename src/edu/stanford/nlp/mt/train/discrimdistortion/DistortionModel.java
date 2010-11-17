package edu.stanford.nlp.mt.train.discrimdistortion;

import java.io.Serializable;
import java.util.Map;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.MutablePair;

public class DistortionModel implements Serializable {
  private static final long serialVersionUID = 8119388926319744131L;

  public static enum Feature {
    Word, RelPosition, CurrentTag, SourceLen, LeftTag, RightTag, ArcTag
  }

  public static enum FeatureType {
    Binary
  } // , Real }

  public static enum Class {
    C1, C2, C3, C4, C5, C6, C7, C8, C9
  }

  public Index<DistortionModel.Feature> featureIndex = null;
  public Index<DistortionModel.Class> classIndex = null;
  public Index<String> wordIndex = null;
  public Index<String> tagIndex = null;
  public double[] weights = null;
  public Map<DistortionModel.Feature, Integer> featureOffsets = null;
  public Map<DistortionModel.Feature, Integer> featureDimensions = null;
  public Map<DistortionModel.Feature, DistortionModel.FeatureType> featureTypes = null;

  public boolean isOutbound = false;

  public boolean useBeginEndMarkers = false;
  public String START_OF_SENTENCE = "<S>"; // Some defaults
  public String END_OF_SENTENCE = "</S>";
  public String DELIM_POS = "SS";

  // WSGDEBUG - Should make this configurable, eventually
  // NOTE the stylistic justification: you probably wouldn't use the same word
  // twice
  // within a 20% span of a sentence (even for sentences with 100 words)
  public static final int NUM_SLEN_BINS = 4;
  public static final int NUM_SLOC_BINS = 5;

  public int getFeatureDimension() {
    return (featureIndex == null) ? 0 : featureIndex.size();
  }

  /**
   * Returns the logprob from the model for this datum and class
   */
  public double logProb(Datum datum, DistortionModel.Class thisC) {
    double[] logScores = new double[DistortionModel.Class.values().length];
    for (DistortionModel.Class c : DistortionModel.Class.values())
      logScores[c.ordinal()] = modelScore(datum, c);

    double denom = ArrayMath.logSum(logScores);

    return logScores[thisC.ordinal()] - denom; // Division in real space
  }

  public MutablePair<Double, DistortionModel.Class> argmax(Datum datum) {
    DistortionModel.Class maxClass = null;
    double maxScore = -1.0;
    for (DistortionModel.Class c : DistortionModel.Class.values()) {
      double modelScore = modelScore(datum, c);
      if (modelScore > maxScore) {
        maxScore = modelScore;
        maxClass = c;
      }
    }

    return new MutablePair<Double, DistortionModel.Class>(maxScore, maxClass);
  }

  private double modelScore(Datum datum, DistortionModel.Class c) {
    double score = 0.0;
    for (int i = 0; i < datum.numFeatures(); i++) {
      if (datum.get(i) < 0.0)
        continue; // Unseen strings
      DistortionModel.Feature feat = featureIndex.get(i);
      DistortionModel.FeatureType type = featureTypes.get(feat);

      int j = indexOf(feat, datum.get(i), c.ordinal());

      score += (type == DistortionModel.FeatureType.Binary) ? weights[j]
          : weights[j] * datum.get(i);
    }

    return score;
  }

  public int indexOf(DistortionModel.Feature feat, float value, int c) {
    Integer offset = featureOffsets.get(feat);
    if (offset == null)
      throw new RuntimeException("Bad feature value\n");

    DistortionModel.FeatureType type = featureTypes.get(feat);

    int featIndex = (type == DistortionModel.FeatureType.Binary) ? offset
        + (int) value : offset;

    return featIndex * classIndex.size() + c;
  }

  public static Class discretizeDistortion(int relMovement) {
    if (relMovement <= -7)
      return Class.C1;
    else if (relMovement <= -4)
      return Class.C2;
    else if (relMovement <= -2)
      return Class.C3;
    else if (relMovement == -1)
      return Class.C4;
    else if (relMovement == 0)
      return Class.C5;
    else if (relMovement == 1)
      return Class.C6;
    else if (relMovement <= 3)
      return Class.C7;
    else if (relMovement <= 6)
      return Class.C8;
    return Class.C9;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Distortion Model:\n");
    if (featureIndex != null)
      sb.append(String.format(" Features: %d\n", featureIndex.size()));
    if (classIndex != null) {
      sb.append(String.format(" Classes : %d\n", classIndex.size()));
      sb.append(String.format(" > %s\n", classIndex.toString()));
    }
    if (tagIndex != null) {
      sb.append(String.format(" Tags    : %d\n", tagIndex.size()));
      sb.append(String.format(" > %s\n", tagIndex.toString()));
    }
    if (wordIndex != null)
      sb.append(String.format(" Words   : %d\n", wordIndex.size()));

    return sb.toString();
  }

  public static int getSlenBin(final int slen) {
    if (slen <= 21)
      return 0;
    else if (slen <= 30)
      return 1;
    else if (slen <= 39)
      return 2;
    return 3;
  }

  public static int getSlocBin(float normalizedLoc) {
    if (normalizedLoc < 0.2f)
      return 0;
    else if (normalizedLoc < 0.4f)
      return 1;
    else if (normalizedLoc < 0.6f)
      return 2;
    else if (normalizedLoc < 0.8f)
      return 3;
    else
      return 4;
  }

}
