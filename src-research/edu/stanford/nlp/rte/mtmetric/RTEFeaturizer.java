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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 
 * @author John Bauer, Daniel Cer
 *
 */
public class RTEFeaturizer {
  RTEPipeline pipeline;
  NoLearningExperiment kbeTester;
  
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
    Global.setProperty("parse.malt", "false");
    Global.setProperty("rsrc.malt", "engmalt.linear.mco");
    Global.setProperty("lex.Nominalization", "on");
    Global.setProperty("lex.InfoMap", "on");
    Global.setProperty("lex.WNHypernymy", "on"); 
    Global.setProperty("lex.JiangConrathWN", "on");
    Global.setProperty("lex.WNHyponymy", "on");
    Global.setProperty("lex.DekangLin", "on");
    Global.setProperty("lex.Country", "on");
    Global.setProperty("lex.WNSynonymy", "on");
    Global.setProperty("lex.Ordinal", "on");
    Global.setProperty("lex.LinWN", "on");
    Global.setProperty("lex.WNAntonymy", "on");

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
    List<Counter<String>> dataPts = new ArrayList<Counter<String>>(); 
    List<Double>  scores = new ArrayList<Double>();
    Index<String> featureIndex = new OAIndex<String>();
    boolean interactive = System.console() != null;
    int row = 0;    

    if (interactive) System.out.print("\nRTE Eval>");
    for (String line = reader.readLine(); line != null; 
         line = reader.readLine()) {
      try {
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
        String resultStr = results.toString();
        resultStr = resultStr.substring(1, resultStr.length()-1).replaceAll(", ", " ");
        System.out.printf("\nFeature List ||| %d ||| %s\n", id, resultStr);
        row++;
      } catch (Exception e) {
        if (interactive) {
          e.printStackTrace();
        } else {
          throw e;
        }
      }
      if (interactive) System.out.print("\nRTE Eval>");
    }

    long dur = System.currentTimeMillis() - startTime;
    System.err.printf("RTE Time: %.3f s\n", dur/1000.);
  }
}

