package edu.stanford.nlp.mt.benchmark;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel.FeatureTemplate;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;

/**
 * Stress test for the dynamic TM.
 * 
 * @author Spence Green
 *
 */
public final class DynamicTMThreading {

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.printf("Usage: java %s tm_file source_file n%n", DynamicTMThreading.class.getName());
      System.exit(-1);
    }
    String fileName = args[0];
    String inputFile = args[1];
    int numThreads = Integer.parseInt(args[2]);
    TimeKeeper timer = TimingUtils.start();
    DynamicTranslationModel<String> tm = DynamicTranslationModel.load(fileName, true, "benchmark");
    tm.setReorderingScores();
    timer.mark("Load");
    tm.createQueryCache(FeatureTemplate.DENSE_EXT);
    timer.mark("Cache creation");

    // Read the source at once for accurate timing of queries
    List<Sequence<IString>> sourceFile = IStrings.tokenizeFile(inputFile);
    timer.mark("Source file loading");
    
    final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) 
        Executors.newFixedThreadPool(numThreads);
    final ExecutorCompletionService<List<ConcreteRule<IString,String>>> workQueue = 
        new ExecutorCompletionService<>(threadPool);
    
    long startTime = TimingUtils.startTime();
    int numRules = 0;
    final InputProperties inProps = new InputProperties();
    for (final Sequence<IString> source : sourceFile) {
      workQueue.submit(new Callable<List<ConcreteRule<IString,String>>>() {
        @Override
        public List<ConcreteRule<IString, String>> call() throws Exception {
          return tm.getRules(source, inProps, 0, null);
        }   
      });
    }
    try {
      for (int k = 0; k < sourceFile.size(); ++k) {
        List<ConcreteRule<IString,String>> result = workQueue.take().get();
        numRules += result.size();
      }
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not read results");
    } finally {
      threadPool.shutdown();
    }
    double queryTimeMillis = TimingUtils.elapsedMillis(startTime);
    timer.mark("Query");

    threadPool.shutdown();
    
    System.out.printf("TM src cardinality: %d%n", tm.maxLengthSource());
    System.out.printf("TM tgt cardinality: %d%n", tm.maxLengthTarget());
    System.out.println("===========");
    System.out.printf("#source segments:   %d%n", sourceFile.size());
    System.out.printf("Timing: %s%n", timer);
    System.out.printf("Time/segment: %.2fms%n", queryTimeMillis / (double) sourceFile.size());
    System.out.printf("#rules: %d%n", numRules);
    System.out.printf("#segments: %d%n", sourceFile.size());
  }
}
