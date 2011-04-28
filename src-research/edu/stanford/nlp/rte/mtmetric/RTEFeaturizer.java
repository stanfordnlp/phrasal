package edu.stanford.nlp.rte.mtmetric;

import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.rte.FeatureOccurrence;
import edu.stanford.nlp.rte.Global;
import edu.stanford.nlp.rte.MockProblem;
import edu.stanford.nlp.rte.NoLearningExperiment;
import edu.stanford.nlp.rte.RTEPipeline;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.OAIndex;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.CountersRealVectors;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.DecompositionSolver;
import org.apache.commons.math.linear.SingularValueDecompositionImpl;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.RealVector;

/**
 * 
 * @author John Bauer, Daniel Cer
 *
 */
public class RTEFeaturizer {
  RTEPipeline pipeline;
  NoLearningExperiment kbeTester;
  public static final String DEFAULT_WTS = "MT08Urdu.wts";
  
  /**
   * Since the RTE system is not re-entrant, and there is no
   * realistic way to create two of them because of the Global state,
   * we keep this private and make it a Singleton.
   */
  private RTEFeaturizer(String[] args) {
    Timing tim = new Timing();

    Global.setPropertiesFromArgs(args);
    pipeline = new RTEPipeline();
    kbeTester = new NoLearningExperiment();

    Global.reportMemory();

    long setupTime = tim.report();
    System.out.println("Setup time: " + setupTime);
  }

  static RTEFeaturizer featurizer = null;
  public static synchronized RTEFeaturizer initialize(String[] args) {
    Global.setProperty("requireInfo", "false");
    Global.setProperty("calculateResults", "false");
    Global.setProperty("aligner.numIterations", "10");
    Global.setProperty("aligner", "stochastic");
    Global.setProperty("parse.malt", "true");
    Global.setProperty("rsrc.malt", "engmalt.linear.mco");
    Global.setProperty("lex.Nominalization", "off");
    Global.setProperty("lex.InfoMap", "off");
    Global.setProperty("lex.WNHypernymy", "off"); 
    Global.setProperty("lex.JiangConrathWN", "off");
    Global.setProperty("lex.WNHyponymy", "off");
    Global.setProperty("lex.DekangLin", "off");
    Global.setProperty("lex.Country", "off");
    Global.setProperty("lex.WNSynonymy", "off");
    Global.setProperty("lex.Ordinal", "off");
    Global.setProperty("lex.LinWN", "off");
    Global.setProperty("lex.WNAntonymy", "off");

    if (featurizer != null) {
      throw new RuntimeException("Attempted to initialize the RTEFeaturizer " +
                                 "when it was already built");
    }
    featurizer = new RTEFeaturizer(args);
    return featurizer;
  }

  public Counter<String> featurize(String reference, String translation) {
    ClassicCounter<String> counter = new ClassicCounter<String>();
    addScores(counter, "r2t", reference, translation);
    addScores(counter, "t2r", translation, reference);
    return counter;
  }

  private void addScores(Counter<String> counter, String prefix, 
                         String text, String hypothesis) {
    MockProblem problem = new MockProblem(prefix + "-rte-scoring", 
                                          text, hypothesis);
    pipeline.processProblem(problem);

    kbeTester.doProblem(problem);

    counter.incrementCount(prefix + "-alignment", problem.getAlignmentScore());
    for (FeatureOccurrence feature : problem.getFeatureOccurrences()) {
      String name = feature.feature.name();     
      counter.incrementCount(prefix + "-" + name, feature.value);
    }
  }
  
  Counter<String> defaultWts;
  
  public double mtScore(String[] refs, String translation) {
     if (defaultWts == null) {
       defaultWts = new ClassicCounter<String>();
       try{
         BufferedReader reader = new BufferedReader(new FileReader(Global.getRTEResourcesDir()+"/"+DEFAULT_WTS));
         for (String line = reader.readLine(); line != null; line = reader.readLine()) {
           String[] fields = line.split(": ");
           defaultWts.incrementCount(fields[0], Double.parseDouble(fields[1]));
         }
       } catch (Exception e) {
         e.printStackTrace();
         System.exit(-1); // Z-Mert ignores some runtime exceptions and just hangs
       }
     }
     return mtScore(refs, translation, defaultWts);
  }
  
  public double mtScore(String[] refs, String translation, Counter<String> wts) {
     int bestRef = 0;
     double bestRefScore = Double.NEGATIVE_INFINITY;
     
     // only call RTE on one reference
     for (int i=0; i < refs.length; i++) {
       double refScore = BLEUMetric.computeLocalSmoothScore(translation, Arrays.asList(refs[i]), 4);
       if (refScore > bestRefScore) {
         bestRefScore = refScore;
         bestRef = i;
       }
     }
     
     System.out.printf("Scoring:\n\tref: %s\n\thyp: %s\n", refs[bestRef],translation);
     Counter<String> features = mtFeaturizer(refs[bestRef],translation);
     double score = Counters.dotProduct(wts, features);
     System.out.printf("Score: %e\n", score);
     return score;
  }
  
  public Counter<String> mtFeaturizer(String reference, String translation) {
    Counter<String> results;
    try {
      results = featurizer.featurize(reference, translation);
    } catch (Exception e) {
      results = new ClassicCounter<String>();
    }
    double smoothBLEU = BLEUMetric.computeLocalSmoothScore(translation, Arrays.asList(reference), 4);
    results.incrementCount("SmoothBLEU", smoothBLEU);
    if (smoothBLEU != 0) {
      results.incrementCount("logSmoothBLEU", Math.log(smoothBLEU));
    } else {
      results.incrementCount("SmoothBLEUIsZero");
    }
    results.incrementCount("biasFeature");
    return results;
  }
  
  public static void main(String[] args) throws Exception {
    RTEFeaturizer featurizer = initialize(args);
    long startTime = System.currentTimeMillis();
    BufferedReader reader = 
      new BufferedReader(new InputStreamReader(System.in));
    PrintStream pstrm = new PrintStream("rte.featurized");
    List<Counter<String>> dataPts = new ArrayList<Counter<String>>(); 
    List<Double>  scores = new ArrayList<Double>();
    Index<String> featureIndex = new OAIndex<String>();
    
    int row = 0;    
    for (String line = reader.readLine(); line != null; 
         line = reader.readLine()) {
      String[] fields = line.split(" \\|\\|\\| ");
      String id = fields[0];
      String ref = fields[1];
      String mt  = fields[2];
      double score = Double.parseDouble(fields[3]);
      System.err.printf("Scoring: \n\tref: %s hyp: %s\n", ref, mt);
      Counter<String> results = featurizer.mtFeaturizer(ref, mt);      
      dataPts.add(results);
      scores.add(score);
      for (String feature : results.keySet()) {
        featureIndex.indexOf(feature,true);
      }
      System.err.printf("Results: %s\n", results);
      pstrm.printf("%d ||| %s ||| %s ||| %f\n", row, id, results, score);
      row++;
    } 
    // long term - this really shouldn't be here
    
    long dur = System.currentTimeMillis() - startTime;
    System.err.printf("RTE Time: %.3f s\n", dur/1000.);
    
    //RealMatrix A = new Array2DRowRealMatrix(dataPts.size(), featureIndex.size());
    RealMatrix A = new Array2DRowRealMatrix(dataPts.size(), dataPts.size());
    RealVector b = new ArrayRealVector(dataPts.size());
    
    row = 0;
    for (Counter<String> datum : dataPts) {
       for (Map.Entry<String, Double> entry : datum.entrySet()) {
         int idx = featureIndex.indexOf(entry.getKey());
         double val = entry.getValue();
         A.setEntry(row, idx, val);
         b.setEntry(row, scores.get(row));
       }
       row++;
    }

    DecompositionSolver s = new SingularValueDecompositionImpl(A).getSolver();
    RealVector x = s.solve(b);


    RealVector p = A.operate(x);
    
    System.err.println("Linear regression");
    System.err.println("=================");
    Counter<String> wts = CountersRealVectors.fromRealVector(x, featureIndex);
    
    for (Map.Entry<String, Double> entry : wts.entrySet()) {
      System.err.printf("%s: %e\n", entry.getKey(), entry.getValue());
    }
    System.err.println("=================");
    
    row = 0;
    double sse = 0;
    double correctSSE = 0;
    for (Counter<String> datum : dataPts) {
      double hypScore = 0; 
      for (Map.Entry<String, Double> entry : datum.entrySet()) {
        hypScore += wts.getCount(entry.getKey()) * entry.getValue();
      }
      double error = hypScore - scores.get(row);
      System.err.printf("%d: predicted: %.3f actual: %.3f\n error: %.3f\n", row, hypScore, scores.get(row), error);
      sse += error*error;
      correctSSE += (p.getEntry(row) - b.getEntry(row))*(p.getEntry(row) - b.getEntry(row));
      row++;
    } 
    System.err.printf("MSE: %e (MSSE amc: %e)\n", sse/row, correctSSE/row);
  }
}

