package edu.stanford.nlp.mt.reranker;

import java.util.*; import java.io.*;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.classify.km.*;
import edu.stanford.nlp.classify.km.kernels.Kernel;
import edu.stanford.nlp.classify.km.sparselinearalgebra.SparseVector;
import edu.stanford.nlp.util.ArrayUtils;

/**
 * SSVM
 *
 * @author Dan Cer (Daniel.Cer@colorado.edu)
 */

public class SSVMLearner extends AbstractOneOfManyClassifier {
  public static final StructuredSVM.StructLoss
    DEFAULT_STRUCT_LOSS = StructuredSVM.StructLoss.MarginRescale;
  public static final double DEFAULT_C = 10;

  public static final String DEFAULT_KERNEL = "linear";

  StructuredSVM.StructLoss structLoss = DEFAULT_STRUCT_LOSS;
  double C = DEFAULT_C;
  String kernelName = DEFAULT_KERNEL;
  StructuredSVM ssvm;

  SparseVector[] truePsi;
  SparseVector[][] riskyPsi;
  double[][] deltaLoss;

  double[] computeViolations(int idx, StructuredSVM pSsvm) {
    double correctScore = pSsvm.score(truePsi[idx]);
    double[] v = new double[riskyPsi[idx].length];

    for (int i = 0; i < v.length; i++) {
       if (Math.abs(truePsi[idx].dotProduct(riskyPsi[idx][i]) -
           truePsi[idx].dotProduct(truePsi[idx])) < 0.01) {
           v[i] = Double.NEGATIVE_INFINITY;
           continue;
       }
       double deltaPsi = correctScore - pSsvm.score(riskyPsi[idx][i]);
       double dLoss = deltaLoss[idx][i];

       if (dLoss == 0) { v[i] = Double.NEGATIVE_INFINITY; continue; }

       switch (structLoss) {
         case OneZero:
              v[i] = 1 - deltaPsi;
            break;
         case MarginRescale:
              v[i] = dLoss - deltaPsi;
            break;
         case SlackRescale:
              v[i] = (1 - deltaPsi)*dLoss;
            break;
       }
    }
    return v;
  }

  HashMap<CompactHypothesisList, ArrayList<SparseVector>> svCache =
    new HashMap<CompactHypothesisList, ArrayList<SparseVector>>();

  @Override
	double[] getAllScores(CompactHypothesisList chl) {
    ArrayList<SparseVector> vecs = svCache.get(chl);
    if (vecs == null) {
      vecs = new ArrayList<SparseVector>();
      svCache.put(chl, vecs);
      int[][] fIndices = chl.getFIndices();
      // int nbestSize = chl.size();
      float[][] fValues = chl.getFValues();

      for (int i = 0; i < fIndices.length &&
                      fIndices[i] != null; i++) {
        SparseVector sv = new SparseVector(fIndices[i], ArrayUtils.toDouble(fValues[i]));
        vecs.add(sv);
      }
    }
    double[] scores = new double[vecs.size()];
    for (int i = 0; i < vecs.size(); i++) {
      scores[i] = ssvm.score(vecs.get(i));
    }
    return scores;
  }

  private class BestOfManyRLF implements RiskyLabelingFinder {
    public RiskyLabelingRecord find(int dataPointIdx, StructuredSVM pSsvm) {
       if (riskyPsi[dataPointIdx].length == 0) { return null; }
       double[] v = computeViolations(dataPointIdx, pSsvm);
       int worstIdx = ArrayMath.argmax(v);
       double riskyDeltaLoss = deltaLoss[dataPointIdx][worstIdx];
       double deltaScore = pSsvm.scoreDelta(truePsi[dataPointIdx],
          riskyPsi[dataPointIdx][worstIdx]);
       return new RiskyLabelingRecord(truePsi[dataPointIdx],
         riskyPsi[dataPointIdx][worstIdx], riskyDeltaLoss, deltaScore);
    }
  }

  @Override
	public boolean isLogLinear() { return true; } // for now we fake it

  static public final double SCORE_REWT = 10;

  @Override
	public double[] getProbs(CompactHypothesisList chl) {
    double[] scores = getAllScores(chl);
    for (int i = 0; i < scores.length; i++) scores[i] *= SCORE_REWT;
    double denom = ArrayMath.logSum(scores);
    double[] probs = ArrayMath.add(scores, -denom);
    ArrayMath.expInPlace(probs);
    return probs;
  }

  @Override
  public void displayWeights(PrintWriter pw, boolean sortByWeight) {
    pw.printf("Display Weights not implemented yet for SSVM\n"); }

  @Override
	public void learn(List<CompactHypothesisList> lchl) {
    super.learn(lchl); wts = null;


    int instanceCnt = 0;
    int[] cntBestBleu = new int[lchl.size()];
    for (int i = 0; i < lchl.size(); i++) {
      CompactHypothesisList chl = lchl.get(i);
      double[] bleus = chl.getScores();
      int[][] fIndices   = chl.getFIndices();
      int bestBleu = ArrayMath.argmax(bleus);
      cntBestBleu[i] = 0;
      int cntNonNull;
      for (cntNonNull = 0; cntNonNull < fIndices.length &&
                            fIndices[cntNonNull] != null; cntNonNull++);

      for (int j = 0; j < cntNonNull; j++) {
        if (bleus[j] == bleus[bestBleu]) cntBestBleu[i]++;
      }
      instanceCnt += cntBestBleu[i];
    }

    truePsi = new SparseVector[instanceCnt];
    riskyPsi = new SparseVector[instanceCnt][];
    deltaLoss = new double[instanceCnt][];
    int truePsiIdx = 0;
    for (int i = 0; i < lchl.size(); i++) {
      CompactHypothesisList chl = lchl.get(i);
      float[][] fValues = chl.getFValues();
      int[][] fIndices   = chl.getFIndices();
      double[] bleus = chl.getScores();

      int bestBleu = ArrayMath.argmax(bleus);

      int cntNonNull;
      for (cntNonNull = 0; cntNonNull < fIndices.length &&
                            fIndices[cntNonNull] != null; cntNonNull++);

      int subOptEntries = 0;
      for (int j = 0; j < cntNonNull; j++) {
        //if (bleus[j] == 0) continue; // ignore 0 bleu scores for now
        if (bleus[j] == bleus[bestBleu]) continue;
        subOptEntries++;
      }

      SparseVector[] sharedRiskyPsi = new SparseVector[subOptEntries];
      double[] sharedDeltaLoss = new double[subOptEntries];

      for (int j = 0, k = 0; j < cntNonNull; j++) {
        //if (bleus[j] == 0) continue;
        if (bleus[j] == bleus[bestBleu]) continue;
        sharedDeltaLoss[k] = bleus[bestBleu] - bleus[j];
        sharedRiskyPsi[k] =
          new SparseVector(fIndices[j], ArrayUtils.toDouble(fValues[j]));
        k++;
      }

      for (int subIdx = 0; subIdx < cntNonNull; subIdx++) {
         if (bleus[subIdx] != bleus[bestBleu]) continue;
         truePsi[truePsiIdx] = new SparseVector(fIndices[subIdx],
                               ArrayUtils.toDouble(fValues[subIdx]));
         riskyPsi[truePsiIdx] = sharedRiskyPsi;
         deltaLoss[truePsiIdx] = sharedDeltaLoss;
         truePsiIdx++;
      }
    }

    Kernel k = Kernel.factory(kernelName);
    System.out.printf("Training using k: %s\n", k);
    System.out.printf("               C: %f\n", C);
    ssvm = StructuredSVM.trainMCSVM(k, C, structLoss, new BestOfManyRLF(),
     truePsi.length);

  }
}
