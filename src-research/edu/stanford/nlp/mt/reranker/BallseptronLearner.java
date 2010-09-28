package edu.stanford.nlp.mt.reranker;

import java.util.List;
import java.util.Arrays;

import edu.stanford.nlp.math.ArrayMath;

/**
 * BallseptronLearner
 * 
 * @author Dan Cer (Daniel.Cer@colorado.edu)
 */

public class BallseptronLearner extends AbstractOneOfManyClassifier {
  public enum OptConstraint {
    oneZero, marginRescale, slackScale
  };

  public static final double DEFAULT_RADIUS = 2.0;
  public static final double DEFAULT_LRATE = 0.1;
  public static final int DEFAULT_EPOCHS = 10000;
  public static final double DEFAULT_MIN_PERCENT_DELTA = 0.01;
  public static final OptConstraint DEFAULT_OPT_CONSTRAINT = OptConstraint.marginRescale;
  public static String OPT_CONSTRAINT_PROP = "OptConstraint";

  private double radius = DEFAULT_RADIUS;
  private double lrate = DEFAULT_LRATE;
  private int maxEpochs = DEFAULT_EPOCHS;
  private double minPercentDelta = DEFAULT_MIN_PERCENT_DELTA;
  OptConstraint optConstraint = DEFAULT_OPT_CONSTRAINT;

  public void displayConfig() {
    System.out.println("Ballseptron Configuration");
    System.out.printf("\tradius: %f\n", radius);
    System.out.printf("\tlrate: %f\n", lrate);
    System.out.printf("\tmax epochs: %d\n", maxEpochs);
    System.out.printf("\topt constraint: %s\n", optConstraint);
    System.out.println();
  }

  public BallseptronLearner() {
    String optConstraintProp = System.getProperty(OPT_CONSTRAINT_PROP);
    if (optConstraintProp != null) {
      boolean hasMatch = false;
      for (OptConstraint val : OptConstraint.values()) {
        if (val.toString().equals(optConstraintProp)) {
          optConstraint = val;
          hasMatch = true;
        }
      }
      if (!hasMatch) {
        throw new RuntimeException("Error: Invalid optimization constraint '"
            + optConstraintProp + "'");
      }
    }
  }

  public BallseptronLearner(double radius, double lrate, int maxEpochs,
      double minPercentDelta, OptConstraint optConstraint) {
    this.radius = radius;
    this.lrate = lrate;
    this.maxEpochs = maxEpochs;
    this.minPercentDelta = minPercentDelta;
    this.optConstraint = optConstraint;
  }

  public int computeMaxViolation(CompactHypothesisList chl) {
    double wtLen = norm2Wt();
    double[] v = getAllScores(chl);
    double[] bleus = chl.getScores();
    for (int i = 0; i < v.length; i++) {
      double deltaW = v[0] / wtLen - v[i] / wtLen;
      double deltaBleu = bleus[0] - bleus[i];
      switch (optConstraint) {
      case oneZero:
        v[i] = 1 - deltaW;
        break;
      case marginRescale:
        v[i] = deltaBleu - deltaW;
        break;
      case slackScale:
        v[i] = (1 - deltaW) * deltaBleu;
        break;
      }
    }
    v[0] = Double.NEGATIVE_INFINITY;
    return ArrayMath.argmax(v);
  }

  private double weightUpdate(CompactHypothesisList chl, int worstViolator) {

    double[] bleus = chl.getScores();
    float[][] fValues = chl.getFValues();
    int[][] fIndices = chl.getFIndices();

    float[] actualPt = fValues[0];
    int[] actualIndicies = fIndices[0];

    float[] worstVPt = fValues[worstViolator];
    int[] worstVIn = fIndices[worstViolator];

    double deltaBleu = bleus[0] - bleus[worstViolator];

    double actValue = 0, worstVioValue = 0, margin, wtLen = norm2Wt();

    for (int i = 0; i < actualPt.length; i++) {
      actValue += wts[actualIndicies[i]] * actualPt[i];
    }

    for (int i = 0; i < worstVPt.length; i++) {
      worstVioValue += wts[worstVIn[i]] * worstVPt[i];
    }

    margin = (actValue - worstVioValue) / wtLen;

    double effectiveRadius = radius;
    if (optConstraint == OptConstraint.marginRescale) {
      effectiveRadius *= deltaBleu;
    }

    if (margin > effectiveRadius)
      return margin;

    for (int i = 0; i < actualPt.length; i++) {
      wts[actualIndicies[i]] += eLrate
          * (actualPt[i] - effectiveRadius * wts[actualIndicies[i]] / wtLen);
    }
    for (int i = 0; i < worstVPt.length; i++) {
      wts[worstVIn[i]] -= eLrate
          * (worstVPt[i] + effectiveRadius * wts[worstVIn[i]] / wtLen);
    }

    double newActValue = 0;
    for (int i = 0; i < actualPt.length; i++) {
      newActValue += wts[actualIndicies[i]] * actualPt[i];
    }
    double newOldBest = 0;
    for (int i = 0; i < worstVPt.length; i++) {
      newOldBest += wts[worstVIn[i]] * worstVPt[i];
    }
    return margin;
  }

  @Override
  public boolean isLogLinear() {
    return false;
  }

  double eLrate;

  @Override
  public void learn(List<CompactHypothesisList> lchl) {
    super.learn(lchl);
    displayConfig();

    Arrays.fill(wts, 1.0);
    int sz = lchl.size();
    double lastAvgMargin = Double.NEGATIVE_INFINITY;
    double runningDelta = 0;
    eLrate = lrate;
    for (int epoch = 0; epoch < maxEpochs; epoch++) {
      // primitive learning rate schedule
      if (epoch > 0 && (epoch % 500) == 0)
        eLrate *= 0.10;
      double avgMargin = 0;
      double avgPosMargin = 0;
      int correct = 0;
      for (CompactHypothesisList chl : lchl) {
        int best = getBestPrediction(chl);
        if (best == 0)
          correct++;

        int maxViolation = computeMaxViolation(chl);
        double margin = weightUpdate(chl, maxViolation);
        avgMargin += margin;
        if (margin > 0) {
          avgPosMargin += margin;
        }
      }
      double marginDelta = Math.abs((avgMargin - lastAvgMargin) / avgMargin);
      lastAvgMargin = avgMargin;
      System.out.printf(
          "Epoch %d: Avg Margin: %.3f(delta %.5f%%) Avg Pos Margin: %.3f "
              + "Accuracy: %.3f (%d/%d)\n", epoch, avgMargin / sz, marginDelta,
          avgPosMargin / sz, correct / (double) sz, correct, sz);
      if (runningDelta < Double.POSITIVE_INFINITY) {
        runningDelta *= 0.75;
        runningDelta += marginDelta;
      } else
        runningDelta = marginDelta;

      // Only exit if we're consistently getting a small enough delta
      if (runningDelta < minPercentDelta) {
        System.out.printf("Done. Min delta, %f, reached.\n", minPercentDelta);
        return;
      }
    }
    System.out.printf("Done. Max epochs, %d, reached.\n", maxEpochs);
  }
}
