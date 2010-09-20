package edu.stanford.nlp.mt.syntax.mst.rmcd;

import edu.stanford.nlp.util.Index;

import java.util.Arrays;

public class Parameters {

  public double[] parameters;
  public final double[] total;
  public String lossType = "punc";

  public Parameters(int size) {
    parameters = new double[size];
    total = new double[size];
    for (int i = 0; i < parameters.length; i++) {
      parameters[i] = 0.0;
      total[i] = 0.0;
    }
    lossType = "punc";
  }

  public void setLoss(String lt) {
    lossType = lt;
  }

  public void averageParams(double avVal) {
    double multFact = 1.0 / avVal;
    for (int j = 0; j < total.length; j++)
      total[j] *= multFact;
    parameters = total;
  }

  public void updateParamsMIRA(DependencyInstance inst, Object[][] d, double upd) {

    String actParseTree = inst.getParseTree();
    FeatureVector actFV = inst.getFeatureVector();

    int K = 0;
    for (int i = 0; i < d.length && d[i][0] != null; i++) {
      K = i + 1;
    }

    double[] b = new double[K];
    double[] lam_dist = new double[K];
    FeatureVector[] dist = new FeatureVector[K];

    for (int k = 0; k < K; k++) {
      lam_dist[k] = getScore(actFV) - getScore((FeatureVector) d[k][0]);
      b[k] = numErrors(inst, (String) d[k][1], actParseTree);
      b[k] -= lam_dist[k];
      dist[k] = actFV.getDistVector((FeatureVector) d[k][0]);
    }

    double[] alpha = hildreth(dist, b);

    FeatureVector fv;
    for (int k = 0; k < K; k++) {
      fv = dist[k];
      fv.update(parameters, total, alpha[k], upd);
    }
  }

  public double getScore(FeatureVector fv) {
    return fv.getScore(parameters);
  }

  public void setWeights(Index<Integer> index, double[] weights) {
    Arrays.fill(parameters, 0.0f);
    for (int k : index) {
      parameters[k] = weights[index.indexOf(k)];
    }
  }

  private double[] hildreth(FeatureVector[] a, double[] b) {

    int i;
    int max_iter = 10000;
    double eps = 0.00000001;
    double zero = 0.000000000001;

    double[] alpha = new double[b.length];

    double[] F = new double[b.length];
    double[] kkt = new double[b.length];
    double max_kkt = Double.NEGATIVE_INFINITY;

    int K = a.length;

    double[][] A = new double[K][K];
    boolean[] is_computed = new boolean[K];
    for (i = 0; i < K; i++) {
      A[i][i] = a[i].dotProduct(a[i]);
      is_computed[i] = false;
    }

    int max_kkt_i = -1;

    for (i = 0; i < F.length; i++) {
      F[i] = b[i];
      kkt[i] = F[i];
      if (kkt[i] > max_kkt) {
        max_kkt = kkt[i];
        max_kkt_i = i;
      }
    }

    int iter = 0;
    double diff_alpha;
    double try_alpha;
    double add_alpha;

    while (max_kkt >= eps && iter < max_iter) {

      diff_alpha = A[max_kkt_i][max_kkt_i] <= zero ? 0.0 : F[max_kkt_i]
          / A[max_kkt_i][max_kkt_i];
      try_alpha = alpha[max_kkt_i] + diff_alpha;
      // add_alpha = 0.0;

      if (try_alpha < 0.0)
        add_alpha = -1.0 * alpha[max_kkt_i];
      else
        add_alpha = diff_alpha;

      alpha[max_kkt_i] = alpha[max_kkt_i] + add_alpha;

      if (!is_computed[max_kkt_i]) {
        for (i = 0; i < K; i++) {
          A[i][max_kkt_i] = a[i].dotProduct(a[max_kkt_i]); // for version 1
          is_computed[max_kkt_i] = true;
        }
      }

      for (i = 0; i < F.length; i++) {
        F[i] -= add_alpha * A[i][max_kkt_i];
        kkt[i] = F[i];
        if (alpha[i] > zero)
          kkt[i] = Math.abs(F[i]);
      }

      max_kkt = Double.NEGATIVE_INFINITY;
      max_kkt_i = -1;
      for (i = 0; i < F.length; i++)
        if (kkt[i] > max_kkt) {
          max_kkt = kkt[i];
          max_kkt_i = i;
        }

      iter++;
    }

    return alpha;
  }

  public double numErrors(DependencyInstance inst, String pred, String act) {
    if (lossType.equals("nopunc"))
      return numErrorsDepNoPunc(inst, pred, act)
          + numErrorsLabelNoPunc(inst, pred, act);
    return numErrorsDep(pred, act) + numErrorsLabel(pred, act);
  }

  public double numErrorsDep(String pred, String act) {

    String[] act_spans = act.split(" ");
    String[] pred_spans = pred.split(" ");

    int correct = 0;

    for (int i = 0; i < pred_spans.length; i++) {
      String p = pred_spans[i].split(":")[0];
      String a = act_spans[i].split(":")[0];
      if (p.equals(a)) {
        correct++;
      }
    }

    return ((double) act_spans.length - correct);

  }

  public double numErrorsLabel(String pred, String act) {

    String[] act_spans = act.split(" ");
    String[] pred_spans = pred.split(" ");

    int correct = 0;

    for (int i = 0; i < pred_spans.length; i++) {
      String p = pred_spans[i].split(":")[1];
      String a = act_spans[i].split(":")[1];
      if (p.equals(a)) {
        correct++;
      }
    }

    return ((double) act_spans.length - correct);

  }

  public double numErrorsDepNoPunc(DependencyInstance inst, String pred,
      String act) {

    String[] act_spans = act.split(" ");
    String[] pred_spans = pred.split(" ");

    int correct = 0;
    int numPunc = 0;

    for (int i = 0; i < pred_spans.length; i++) {
      String p = pred_spans[i].split(":")[0];
      String a = act_spans[i].split(":")[0];
      String pos = inst.getPOSTag(i + 1);
      if (pos.matches("[,:.'`]+")) {
        numPunc++;
        continue;
      }
      if (p.equals(a)) {
        correct++;
      }
    }

    return ((double) act_spans.length - numPunc - correct);

  }

  public double numErrorsLabelNoPunc(DependencyInstance inst, String pred,
      String act) {

    String[] act_spans = act.split(" ");
    String[] pred_spans = pred.split(" ");

    int correct = 0;
    int numPunc = 0;

    for (int i = 0; i < pred_spans.length; i++) {
      String p = pred_spans[i].split(":")[1];
      String a = act_spans[i].split(":")[1];
      String pos = inst.getPOSTag(i + 1);
      if (pos.matches("[,:.'`]+")) {
        numPunc++;
        continue;
      }
      if (p.equals(a)) {
        correct++;
      }
    }

    return ((double) act_spans.length - numPunc - correct);

  }

}
