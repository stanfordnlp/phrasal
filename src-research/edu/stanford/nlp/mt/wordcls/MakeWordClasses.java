package edu.stanford.nlp.mt.wordcls;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.log.PhrasalLogger;
import edu.stanford.nlp.mt.log.PhrasalLogger.LogName;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Various algorithms for learning a mapping function from an input
 * word to an output equivalence class.
 * 
 * TODO Add encoding parameter
 * TODO Output sufficient statistics for test evaluation
 * TODO Extract out objective function as an interface to support
 * other clustering algorithms if needed.
 * 
 * @author Spence Green
 *
 */
public class MakeWordClasses {

  private static final IString START_TOKEN = new IString("<s>");
  
  private final int numIterations;
  private final int numClasses;
  private final int numThreads;
  private final int order;
  
  private final Logger logger;
  
  private static final int INITIAL_CAPACITY = 100000;
  private final Map<IString,Integer> wordToClass;
  private final Counter<IString> wordCount;
  private final TwoDimensionalCounter<IString, NgramHistory> historyCount;
  private TwoDimensionalCounter<Integer,NgramHistory> classHistoryCount;
  private final ClassicCounter<Integer> classCount;
  
  public MakeWordClasses(Properties properties) {
    // User options
    this.numIterations = PropertiesUtils.getInt(properties, "niters", 20);
    assert this.numIterations > 0;
    
    this.numClasses = PropertiesUtils.getInt(properties, "nclasses", 512);
    assert this.numClasses > 0;
    
    this.numThreads = PropertiesUtils.getInt(properties, "nthreads", 1);
    assert this.numThreads > 0;
    
    this.order = PropertiesUtils.getInt(properties, "order", 2);
    assert this.order > 1;
    
    logger = Logger.getLogger(this.getClass().getName());
    PhrasalLogger.prefix = String.valueOf(numClasses) + "-classes";
    PhrasalLogger.attach(logger, LogName.WORD_CLASS);
    logger.info("#iterations: " + String.valueOf(numIterations));
    logger.info("#classes: " + String.valueOf(numClasses));
    logger.info("order: " + String.valueOf(order));
    
    // Internal data structures
    wordToClass = Generics.newHashMap(INITIAL_CAPACITY);
    wordCount = new ClassicCounter<IString>(INITIAL_CAPACITY);
    classCount = new ClassicCounter<Integer>(numClasses);
    historyCount = new TwoDimensionalCounter<IString,NgramHistory>();
    classHistoryCount = new TwoDimensionalCounter<Integer,NgramHistory>();
  }

  private void initialize(String[] filenames) throws IOException {
    List<IString> defaultHistory = Generics.newLinkedList();
    for (int i = 0; i < order-1; ++i) {
      defaultHistory.add(START_TOKEN);
    }
    // Read the vocabulary and histories
    final long startTime = System.nanoTime();
    for (String filename : filenames) {
      logger.info("Reading: " + filename);
      LineNumberReader reader = IOTools.getReaderFromFile(filename);
      for (String line; (line = reader.readLine()) != null;) {
        Sequence<IString> tokens = IStrings.tokenize(line.trim());
        List<IString> history = Generics.newLinkedList(defaultHistory);
        for (IString token : tokens) {
          wordCount.incrementCount(token);
          historyCount.incrementCount(token, new NgramHistory(history));
          history.add(token);
          history.remove(0);
        }
      }
      reader.close();
    }
    NgramHistory.lockIndex();
    final double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
    logger.info(String.format("Done reading input files (%.3fsec)", elapsedTime));
    logger.info(String.format("Input gross statistics: %d words  %d tokens  %d histories", 
        wordCount.keySet().size(), (int) wordCount.totalCount(), (int) historyCount.totalCount()));
    
    // Initialize clustering
    List<IString> vocab = Generics.newArrayList(wordCount.keySet());
    Collections.sort(vocab, Counters.toComparator(wordCount, false, true));
    for (int i = 0; i < vocab.size(); ++i) {
      final int classId = i % numClasses;
      classCount.incrementCount(classId);
      IString word = vocab.get(i);
      wordToClass.put(word, classId);
      Set<NgramHistory> historiesForWord = historyCount.getCounter(word).keySet();
      for (NgramHistory h : historiesForWord) {
        classHistoryCount.incrementCount(classId, h);
      }
    }
    logger.info("Finished generating initial cluster assignment");
  }

  public void run(String[] filenames) {
    try {
      initialize(filenames);
    } catch (IOException e1) {
      throw new RuntimeException(e1);
    }
    
    logger.info(String.format("Starting clustering with %d threads", numThreads));
    final List<IString> vocab = Generics.newArrayList(wordCount.keySet());
    for (int e = 0; e < numIterations; ++e) {
      logger.info(String.format("Iteration %d: start", e));
      final long startTime = System.nanoTime();
      // Create threadpool and dispatch workers
      MulticoreWrapper<ClustererInput,ClustererOutput> threadpool = 
          new MulticoreWrapper<ClustererInput,ClustererOutput>(numThreads, 
              new ThreadsafeProcessor<ClustererInput,ClustererOutput>() {
                @Override
                public ClustererOutput process(ClustererInput input) {
                  GoogleObjectiveFunction algorithm = new GoogleObjectiveFunction(input);
                  return algorithm.cluster();
                }
                @Override
                public ThreadsafeProcessor<ClustererInput, ClustererOutput> newInstance() {
                  return this;
                }
          });
      // Randomly shuffle the vocabulary
      Collections.shuffle(vocab);
      for (int t = 0; t < numThreads; ++t) {
        ClustererInput input = createInput(vocab, t);
        threadpool.put(input);
      }

      // Wait for shutdown and process results
      threadpool.join();
      clearDataStructures();
      int numUpdates = 0;
      while(threadpool.peek()) {
        ClustererOutput result = threadpool.poll();
        numUpdates += updateCountsWith(result);
      }
      double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
      logger.info(String.format("Iteration %d: elapsed time %.3fsec", e, elapsedTime));
      logger.info(String.format("Iteration %d: #updates %d", e, numUpdates));
      logger.info(String.format("Iteration %d: objective: %.4f", e, objectiveFunctionValue()));
    }
  }
  
  /**
   * Objective function of Uszkoreit and Brants (2008) (Eq. 10).
   * 
   * @return
   */
  private double objectiveFunctionValue() {
    double objValue = 0.0;
    for (int classId = 0; classId < numClasses; ++classId) {
      Counter<NgramHistory> historyCount = classHistoryCount.getCounter(classId);
      for (NgramHistory history : historyCount.keySet()) {
        double count = historyCount.getCount(history);
        objValue += count * Math.log(count);
      }
      double count = classCount.getCount(classId);
      assert historyCount.totalCount() == count;
      if (count > 0.0) {
        objValue -= count * Math.log(count);
      } else {
        logger.warning("Empty cluster: " + String.valueOf(classId));
      }
    }
    return objValue;
  }
  
  private void clearDataStructures() {
    classCount.clear();
    classHistoryCount = new TwoDimensionalCounter<Integer,NgramHistory>();
  }

  private ClustererInput createInput(List<IString> vocab, int t) {
    int inputSize = vocab.size() / numThreads;
    int start = t * inputSize;
    int end = Math.min((t+1)*inputSize, vocab.size());
    List<IString> inputVocab = vocab.subList(start, end);
    return new ClustererInput(inputVocab, this.wordCount, 
        this.historyCount, this.wordToClass, this.classCount, this.classHistoryCount);
  }

  private int updateCountsWith(ClustererOutput result) {
    Counters.addInPlace(classCount, result.classCount);
    Set<Integer> classes = result.classHistoryCount.firstKeySet();
    for (Integer classId : classes) {
      Counter<NgramHistory> counter = this.classHistoryCount.getCounter(classId);
      Counter<NgramHistory> delta = result.classHistoryCount.getCounter(classId);
      Counters.addInPlace(counter, delta);
    }
    
    // Update assignments
    int numUpdates = 0;
    for (Map.Entry<IString, Integer> assignment : result.wordToClass.entrySet()) {
      if (wordToClass.get(assignment.getKey()) != assignment.getValue()) {
        ++numUpdates;
        wordToClass.put(assignment.getKey(), assignment.getValue());
      }
    }
    return numUpdates;
  }

  public void writeResults(PrintStream out) {
    logger.info("Writing final class assignments");
    for (Map.Entry<IString, Integer> assignment : wordToClass.entrySet()) {
      out.printf("%s\t%d%n", assignment.getKey().toString(), assignment.getValue());
    }
  }

  private static Map<String, Integer> optionArgDefs() {
    Map<String,Integer> argDefs = Generics.newHashMap();
    argDefs.put("order", 1);
    argDefs.put("nthreads", 1);
    argDefs.put("nclasses", 1);
    argDefs.put("niters", 1);
    return argDefs;
  }
  
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    final String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(MakeWordClasses.class.getName()).append(" OPTS file [file]").append(nl)
    .append(" -order num     : Model order (default: 2)").append(nl)
    .append(" -nthreads num  : Number of threads (default: 1)").append(nl)
    .append(" -nclasses num  : Number of classes (default: 512)").append(nl)
    .append(" -niters num    : Number of iterations (default: 20)");
    
    return sb.toString();
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    String[] filenames = options.getProperty("","").split("\\s+");
    if (filenames.length < 1) {
      System.err.println(usage());
      System.exit(-1);
    }
    MakeWordClasses mkWordCls = new MakeWordClasses(options);
    mkWordCls.run(filenames);
    mkWordCls.writeResults(System.out);
  }
}
