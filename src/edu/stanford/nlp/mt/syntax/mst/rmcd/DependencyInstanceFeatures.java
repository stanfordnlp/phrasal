package edu.stanford.nlp.mt.syntax.mst.rmcd;

import edu.stanford.nlp.stats.Counter;

import java.util.Collection;

public class DependencyInstanceFeatures {

  // Attachment probabilities:
  final double[][][] probs;
  final double[][][][] nt_probs;

  // Feature vectors:
  private final FeatureVector[][][] fvs;
  private final FeatureVector[][][][] nt_fvs;

  private final int length;
  private final int numTypes;

  public DependencyInstanceFeatures(int length, int numTypes) {
    this.length = length;
    this.numTypes = numTypes;
    fvs = new FeatureVector[length][length][2];
    probs = new double[length][length][2];
    nt_fvs = new FeatureVector[length][numTypes][2][2];
    nt_probs = new double[length][numTypes][2][2];
  }

  public void setFVS(int w1, int w2, int direction, FeatureVector fv) {
    fvs[w1][w2][direction] = fv;
  }

  public FeatureVector getFVS(int w1, int w2, int direction) {
    return fvs[w1][w2][direction];
  }

  public FeatureVector getFVS(int w1, int w2, boolean attR) {
    return fvs[w1][w2][attR ? 0 : 1];
  }

  public Counter<Integer> getCounter(int w1, int w2, boolean attR) {
    return fvs[w1][w2][attR ? 0 : 1].toCounter();
  }

  public Collection<Integer> getCollection(int w1, int w2, boolean attR) {
    return fvs[w1][w2][attR ? 0 : 1].toCollection();
  }

  public FeatureVector getFVS(int wi1, int wi2) {
    return fvs[wi1 < wi2 ? wi1 : wi2][wi1 < wi2 ? wi2 : wi1][wi1 < wi2 ? 1 : 0];
  }

  public FeatureVector getNT_FVS(int w1, int w2, int direction, int dominance) {
    return nt_fvs[w1][w2][direction][dominance];
  }

  public void setNT_FVS(int w1, int w2, int direction, int dominance, FeatureVector fv) {
    nt_fvs[w1][w2][direction][dominance] = fv;
  }

  public int length() {
    return fvs.length;
  }

  public int[][] getTypes() {
    int[][] static_types = new int[length][length];
    for (int i = 0; i < length; i++) {
      for (int j = 0; j < length; j++) {
        if (i == j) {
          static_types[i][j] = 0;
          continue;
        }
        int wh = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int t = 0; t < numTypes; t++) {
          double score;
          if (i < j)
            score = nt_probs[i][t][0][1] + nt_probs[j][t][0][0];
          else
            score = nt_probs[i][t][1][1] + nt_probs[j][t][1][0];

          if (score > best) {
            wh = t;
            best = score;
          }
        }
        static_types[i][j] = wh;
      }
    }
    return static_types;
  }
}
