package mt.reranker;

import edu.stanford.nlp.math.ArrayMath;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * @author Dan Cer (Daniel.Cer@colorado.edu)
 */

public abstract class AbstractOneOfManyClassifier {
  List<CompactHypothesisList> lchl;
  double[] wts;

  static final String PROPERTY_NAME = "learner";
  static final String DEFAULT_LEARNER = "LogLinearMCL";
  static final String LEARNER_SUFFIX = "Learner";
  static final String LEARNER_PACKAGE = "mt.reranker";

  FeatureIndex featureIndex;

  double norm2Wt() {
    double wtSum = 0;
    for (int i = 0; i < wts.length; i++) { wtSum += wts[i] * wts[i]; }
    return Math.sqrt(wtSum);
  }

  /**
   * Are weights trained in such a way that they can be interpreted
   * as parametrizing a log linear model?
   */
  abstract public boolean isLogLinear();

  public void learn(DataSet dataSet) {
    learn(dataSet.getTrainingSet());
  }

  public void learn(List<CompactHypothesisList> lchl) {
    this.lchl = lchl;
    System.out.println("Learning using: "+getClass().getName());
    // While this method should be overriden by all subclasses,
    // the corresponding subclass methods should call this
    // method during initialization.

    //if (lchl.isEmpty()) {
    if (lchl.size() == 0) {
       System.err.printf("lchl.size: "+lchl.size());
       throw new RuntimeException(getClass().getName()+".learn(): " +
          " called with empty training set\n");
    }
    // We shouldn't have to do (/shouldn't do) things in this way -
    // but, all things considered, this will due for now
    featureIndex = lchl.get(0).getFeatureIndex();
    for (CompactHypothesisList chl : lchl) {
      if (featureIndex != chl.getFeatureIndex()) {
        throw new RuntimeException(getClass().getName()+".learn(): " +
          " The same featureIndex must be used for every example in " +
          " the training set.");
      }
    }
    wts = new double[featureIndex.size()];
  }

  static public AbstractOneOfManyClassifier factory() {
    String selectedClassifier =
      System.getProperty(PROPERTY_NAME, DEFAULT_LEARNER);
    return factory(selectedClassifier);
  }

  static public AbstractOneOfManyClassifier
    factory(String classifierName) {
    String learnerClass=LEARNER_PACKAGE+"."+classifierName+"."+LEARNER_SUFFIX;
    try {
      try {
        return (AbstractOneOfManyClassifier)
                ClassLoader.getSystemClassLoader().loadClass(
                LEARNER_PACKAGE+"."+classifierName+LEARNER_SUFFIX).
                newInstance();
      } catch (ClassNotFoundException e) {
        // allow LEARNER_SUFFIX to be optional
        return (AbstractOneOfManyClassifier)
                ClassLoader.getSystemClassLoader().loadClass(
                LEARNER_PACKAGE+"."+classifierName).
                newInstance();
      }
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Learner '"+classifierName+"' not found. "+
        " (attempted loading "+learnerClass +")");
    } catch (InstantiationException e) {
      throw new RuntimeException("Can not create learner '"+classifierName+"' "+
        " since corresponding class is either an interface or abstract ");
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Can not create learner '"+classifierName+"' "+
        " - IllegalAccessException:\n"+e);
    }
  }

  final public double getLogLikelihood(List<CompactHypothesisList> chls) {
    double[][] allProbs = getProbs(chls); double logLikelihood = 0;
    for (int i = 0; i < allProbs.length; i++) {
      double[] bleus = chls.get(i).getScores();
      int bestBleu = ArrayMath.argmax(bleus);
      logLikelihood -= Math.log(allProbs[i][bestBleu]);
    }
    return logLikelihood;
  }

  final public double getLogLikelihood(List<CompactHypothesisList> chls, int[] indices) {
    double[][] allProbs = getProbs(chls); double logLikelihood = 0;
    for (int i = 0; i < allProbs.length; i++) {
      logLikelihood -= Math.log(allProbs[i][indices[i]]); }
    return logLikelihood;
  }

  final public double[][] getProbs(List<CompactHypothesisList> chls) {
    double[][] scores = new double[chls.size()][];
    for (int i = 0, sz = chls.size(); i < sz; i++) {
       scores[i] = getProbs(chls.get(i));
    }
    return scores;
  }

  protected double[] getExpandedFeatureVector(CompactHypothesisList chl,
    int idx) {
    double[] vec = new double[wts.length];
    int[] fIndex = chl.getFIndices()[idx];
    float[] fValue = chl.getFValues()[idx];
    for (int fI = 0; fI < fIndex.length; fI++) vec[fIndex[fI]] = fValue[fI];
    return vec;
  }


  double[] getPairScores(CompactHypothesisList chl, int p0, int p1) {
    int[][] fIndicies = chl.getFIndices();
    double[]  scores = new double[2];
    float[][] fValues = chl.getFValues();

    int[] fIndex = fIndicies[p0];
    float[] fValue = fValues[p0];
    scores[0] = 0;
    for (int fI = 0; fI < fIndex.length; fI++) {
      int idx = fIndex[fI];
      if (idx < wts.length) { // it could be out of weights in testing phase
        scores[0] += wts[idx] * fValue[fI];
      }
    }

    fIndex = fIndicies[p1];
    fValue = fValues[p1];
    scores[1] = 0;
    for (int fI = 0; fI < fIndex.length; fI++) {
      int idx = fIndex[fI];
      if (idx < wts.length) { // it could be out of weights in testing phase
        scores[1] += wts[idx] * fValue[fI];
      }
    }
    return scores;
  }

  double[] getAllScores(CompactHypothesisList chl) {
    int[][] fIndicies = chl.getFIndices(); int nbestSize = chl.size();
    double[]  scores = new double[nbestSize];
    float[][] fValues = chl.getFValues();

    for (int i = 0; i < nbestSize; i++) {
      int[] fIndex = fIndicies[i];
      float[] fValue = fValues[i];
      double sum = 0;
      for (int fI = 0; fI < fIndex.length; fI++) {
        int idx = fIndex[fI];
        if (idx < wts.length) { // it could be out of weights in testing phase
          sum += wts[idx] * fValue[fI];
        }
      }
      scores[i] = sum;
    }
    return scores;
  }


  public static int[] getRandPrediction(List<CompactHypothesisList> chls) {
    Random r = new Random();
    int[] best = new int[chls.size()];
    for (int i = 0; i < best.length; i++) {
      best[i] = r.nextInt(chls.get(i).size());
    }
    return best;
  }


  final public int[] getBestPrediction(List<CompactHypothesisList> chls) {
    return getBestPrediction(chls, true);
  }

  /**
   *
   * @param chls
   * @param tieLast when choosing the best one, should we take the last one (true), or the first one (false)
   * @return
   */
  final public int[] getBestPrediction(List<CompactHypothesisList> chls, boolean tieLast) {
    int[] best = new int[chls.size()];
    for (int i = 0; i < best.length; i++) best[i] = getBestPrediction(chls.get(i), tieLast);
    return best;
  }

  public int getBestPrediction(CompactHypothesisList chl) {
    return getBestPrediction(chl, true, -1);
  }

  public int getBestPrediction(CompactHypothesisList chl, boolean tieLast) {
    return getBestPrediction(chl, tieLast, -1);
  }

  public int getBestPrediction(CompactHypothesisList chl, boolean tieLast, int notAns) {
    double[] scores = getAllScores(chl);
    if (notAns >= 0) scores[notAns] = Double.NEGATIVE_INFINITY;
    if (tieLast)
      return ArrayMath.argmax_tieLast(scores);
    else
      return ArrayMath.argmax(scores);
  }

  public int getBestPrediction(CompactHypothesisList chl, int notAns) {
    return getBestPrediction(chl, true, notAns);
  }

  public double[] getPairProbs(CompactHypothesisList chl, int p0, int p1) {
    double[] scores = getPairScores(chl,p0,p1);
    double denom = ArrayMath.logSum(scores);
    double[] probs = ArrayMath.add(scores, -denom);
    ArrayMath.expInPlace(probs);
    return probs;
  }


  public double[] getProbs(CompactHypothesisList chl) {
    double[] scores = getAllScores(chl);
    double denom = ArrayMath.logSum(scores);
    double[] probs = ArrayMath.add(scores, -denom);
    ArrayMath.expInPlace(probs);
    return probs;
  }

  public void displayWeights() { displayWeights(false); }
  public void displayWeights(boolean sortByWeight) {
    displayWeights(new PrintWriter(System.out), sortByWeight);
  }

  public void displayWeights(String filename) {
    displayWeights(filename, false);
  }

  public void displayWeights(String filename, boolean sortByWeight) {
    PrintWriter pw = null;
    try {
      pw = new PrintWriter(new FileWriter(filename));
    } catch (IOException e) {
      System.err.printf("Warning: %s.displayWeights(): can't write out " +
        "weights to '%s', error opening file\n",
         getClass().getName(), filename);
    }
    displayWeights(pw, sortByWeight);
    pw.close();
  }

  public void displayWeights(PrintWriter pw) { displayWeights(pw, false); }
  public void displayWeights(PrintWriter pw, boolean sortByWeight) {
    Comparator<String> byValueSort = new Comparator<String>() {
       public int compare(String f1, String f2) {
         double wtdiff = (Math.abs(wts[featureIndex.indexOf(f2)])
          - Math.abs(wts[featureIndex.indexOf(f1)]));
         if (wtdiff == 0) return f1.compareTo(f2);
         if (wtdiff > 0) return 1; else return -1;
       }
       public boolean equals(Object obj) { return obj == this; }
    };
    List<String> featureList = featureIndex.objectsList();
    if (sortByWeight) Collections.sort(featureList, byValueSort);
    for (String f : featureList) {
      pw.printf("%s: %f\n", f, wts[featureIndex.indexOf(f)]);
    }
    pw.flush();
  }
}
