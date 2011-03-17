package edu.stanford.nlp.mt.train.discrimdistortion;

import java.util.Arrays;

public class Datum {

  private final float target;
  private final float[] features;

  public Datum(float target, float[] features) {
    this.target = target;
    this.features = features;
  }

  public float getTarget() {
    return target;
  }

  public float get(int i) {
    if (i < 0 || i >= features.length)
      throw new RuntimeException(String.format(
          "%s: Index %d out-of-bounds (%d dimensions)\n", this.getClass()
              .getName(), i, features.length));
    return features[i];
  }

  public int numFeatures() {
    return features.length;
  }

  // Only for the equals method
  public float[] getFeatures() {
    return features;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("target: %f [ ", target));
    for (float feature : features)
      sb.append(String.format("%f ", feature));
    sb.append("]");
    return sb.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other)
      return true;
    if (!(other instanceof Datum))
      return false;

    Datum otherDatum = (Datum) other;

    return (target == otherDatum.getTarget())
        && Arrays.equals(features, otherDatum.getFeatures());
  }
}
