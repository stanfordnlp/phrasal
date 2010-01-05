package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.math.ArrayMath;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * LogLinear Max Conditional Likelihood (MCL) Trainer for Pair of examples
 * @author Pi-Chuan Chang
 */

public class MCLPairLearner extends AbstractOneOfManyClassifier {
  static final String PAIR_FILE_NAME = "pairList";
  static final String DEFAULT_PAIR_FILE = "";
  MCLPairLearner.ObjF objF = null;
  int loadPairMax = 0;

  class ObjF extends AbstractCachingDiffFunction {
    List<List<int[]>> pairList = new ArrayList<List<int[]>>();
    int pairListSize = 0;
    public ObjF(String pairDir)
            throws IOException {
      StringBuilder sb = new StringBuilder(pairDir);
      if (!pairDir.equals("")) {
        sb.append("/");
      }

      for(int i = 0; i <= loadPairMax; i++) {
        System.err.println("(DEBUG) pairs sent load: "+i);
        List<int[]> oneList = new ArrayList<int[]>();
        StringBuilder sb2 = new StringBuilder(sb);
        sb2.append(i).append(".edit");
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(sb2.toString())));
        for (String line; (line = br.readLine()) != null;) {
          String[] intsStr = line.split(",");
          int[] ints = new int[2];
          ints[0] = Integer.parseInt(intsStr[0]);
          ints[1] = Integer.parseInt(intsStr[1]);
          pairListSize++;
          oneList.add(ints);
        }
        br.close();
        pairList.add(oneList);
      }
      System.err.println("(DEBUG) pairs sent done");
      System.err.println("(DEBUG) # of pairs loaded: "+pairListSize);

    }

    @Override
		public int domainDimension() { return featureIndex.size(); }

    public int[] getAccuracy(List<CompactHypothesisList> examples, int offset, DataSet dataSet) {
      int all = 0;
      int acc = 0;

      for (int i = 0; i < examples.size(); i++) {
        List<int[]> pairs = pairList.get(i+offset);
        CompactHypothesisList chl = examples.get(i);
        double[] allscores = chl.getScores();
        for (int[] pair : pairs) {
          double[] scores = new double[2];
          scores[0] = allscores[pair[0]];
          scores[1] = allscores[pair[1]];
          if (scores[0]==scores[1]) continue;
          int bestScore = 0;
          if (scores[1]>scores[0]) bestScore=1;
          all++;
          double[] probs = getPairProbs(chl, pair[0], pair[1]);
          int predicted = 0;
          if (probs[1] > probs[0]) predicted=1;
          if (predicted==bestScore) acc++;
        }
      }
      int[] allStats = new int[2];
      allStats[0] = acc;
      allStats[1] = all;
      return allStats;
    }

    @Override
		public void calculate(double[] testWts) {
      wts = testWts;
      value = 0.0;
      double[] tV = new double[wts.length];
      double[] eV = new double[wts.length];
      double sigma2 = 2.0;

      double prior = 0;
      for (int i = 0; i < wts.length; i++) prior -= (wts[i]*wts[i])/(2*sigma2);
      //value *= lchl.size();

      int examples = 0;
      for (int sentIdx = 0; sentIdx < lchl.size(); sentIdx++) {
        List<int[]> oneList = pairList.get(sentIdx);
        //System.err.println("DEBUG: pairList["+sentIdx+"].size="+oneList.size());
        CompactHypothesisList chl = lchl.get(sentIdx);
        for(int[] pair : oneList) {
          double[] allBleu = chl.getScores();
          double[] bleus = new double[2];
          bleus[0] = allBleu[pair[0]];
          bleus[1] = allBleu[pair[1]];
          int bestBleu = 0;
          if (bleus[0] == bleus[1]) continue;
          examples++;
          if (bleus[0] < bleus[1]) bestBleu=1;
          double[] probs = getPairProbs(chl, pair[0],pair[1]);
          //System.err.println("DEBUG: 0: "+pair[0]+" has bleu: "+bleus[0]);
          //System.err.println("DEBUG: 1: "+pair[1]+" has bleu: "+bleus[1]);
          //System.err.println("DEBUG: probs="+probs[0]+", "+probs[1]);
          value += Math.log(probs[bestBleu]);

          // True values
          int[] fIndex = chl.getFIndices()[pair[bestBleu]];
          float[] fValue = chl.getFValues()[pair[bestBleu]];
          for (int fI = 0; fI < fIndex.length; fI++) {
            //System.err.println("(tV)sent="+sentIdx+"\tpairs="+pair[0]+","+pair[1]+"\tFeatures "+fI+"="+fIndex[fI]);
            tV[fIndex[fI]] += fValue[fI];
          }

          // Expected values
          fIndex = chl.getFIndices()[pair[0]];
          fValue = chl.getFValues()[pair[0]];
          for (int fI = 0; fI < fIndex.length; fI++) {
            //System.err.println("(eV)sent="+sentIdx+"\tpair[0]="+pair[0]+"\tFeatures "+fI+"="+fIndex[fI]);
            eV[fIndex[fI]] += probs[0]*fValue[fI];
          }

          fIndex = chl.getFIndices()[pair[1]];
          fValue = chl.getFValues()[pair[1]];
          for (int fI = 0; fI < fIndex.length; fI++) {
            //System.err.println("(eV)sent="+sentIdx+"\tpair[1]="+pair[1]+"\tFeatures "+fI+"="+fIndex[fI]);
            eV[fIndex[fI]] += probs[1]*fValue[fI];
          }

          if (probs[0]+probs[1]-1 > 0.001) {
            throw new RuntimeException("blaH");
          }

        }
        //System.err.println("DEBUG: "+skip+" out of "+all+" are skipped");
      }

      for (int i = 0; i < derivative.length; i++) {
        //derivative[i] = tV[i] - eV[i] - examples*wts[i]/sigma2;
        derivative[i] = tV[i] - eV[i] - wts[i]/sigma2;
        derivative[i] *= -1.0;
        //   if (i < 10)
        //   System.err.printf("\ti:%d t: %f e: %f\n", i, tV[i], eV[i]);
      }
      //value += prior*examples;
      value += prior;
      value *= -1.0;
    }
  }

  @Override
	public boolean isLogLinear() { return true; }


  public int[] getAccuracy(List<CompactHypothesisList> examples, int offset, DataSet dataSet) {
    return objF.getAccuracy(examples, offset, dataSet);
  }

  @Override
	public void learn(DataSet dataSet) {
    int max1 = ArrayMath.max(dataSet.getTrainRange());
    int max2 = ArrayMath.max(dataSet.getDevRange());
    loadPairMax = max1;
    if (max2 > loadPairMax) loadPairMax = max2;
    learn(dataSet.getTrainingSet());
  }


  @Override
	public void learn(List<CompactHypothesisList> lchl) {
    super.learn(lchl);
    String pairDir = System.getProperty(PAIR_FILE_NAME, DEFAULT_PAIR_FILE);
    try {
      System.out.println("MCLPairLearner opens files in "+pairDir);
      objF = new MCLPairLearner.ObjF(pairDir);
    } catch (IOException e) {
      e.printStackTrace();
    }
    Minimizer<DiffFunction> minim = new QNMinimizer(objF);
    //DiffFunctionTester.test(objF);
    wts = minim.minimize(objF, 1e-4, wts);
  }
}
