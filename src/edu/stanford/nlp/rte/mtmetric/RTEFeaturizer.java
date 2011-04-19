package edu.stanford.nlp.rte.mtmetric;

import edu.stanford.nlp.rte.FeatureOccurrence;
import edu.stanford.nlp.rte.Global;
import edu.stanford.nlp.rte.InfoFile;
import edu.stanford.nlp.rte.MockProblem;
import edu.stanford.nlp.rte.NoLearningExperiment;
import edu.stanford.nlp.rte.Problem;
import edu.stanford.nlp.rte.RTEPipeline;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

class RTEFeaturizer {
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
      // TODO: if there are duplicate feature names, is adding the
      // values acceptable?
      counter.incrementCount(prefix + "-" + name, feature.value);
    }
  }

  public static void main(String[] args) throws Exception {
    Global.setProperty("requireInfo", "false");
    Global.setProperty("calculateResults", "false");
    RTEFeaturizer featurizer = initialize(args);
    long startTime = System.currentTimeMillis();
    int cnt = 0;
    BufferedReader reader = 
      new BufferedReader(new InputStreamReader(System.in));
    PrintStream pstrm = new PrintStream("rte.featurized");
    for (String line = reader.readLine(); line != null; 
         line = reader.readLine()) {
      String[] fields = line.split(" \\|\\|\\| ");
      String id = fields[0];
      String ref = fields[1];
      String mt  = fields[2];
      double score = Double.parseDouble(fields[3]);
      System.err.printf("Scoring: \n\tref: %s hyp: %s\n", ref, mt);
      Counter<String> results;
      try {
        results = featurizer.featurize(ref, fields[1]);
      } catch (Exception e) {
        results = new ClassicCounter<String>();
      }
      System.err.printf("Results: %s\n", results);
      pstrm.printf("%s ||| %s ||| %f\n", id, results, score); 
    } 
    long dur = System.currentTimeMillis() - startTime;
    System.err.printf("Time: %.3f s\n", dur/1000.);    
  }
}

