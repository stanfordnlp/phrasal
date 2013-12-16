package edu.stanford.nlp.mt.wordcls;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SystemLogger;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.base.SystemLogger.LogName;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Various algorithms for learning a mapping function from an input
 * word to an output equivalence class.
 * 
 * TODO Extract out objective function as an interface to support
 * other clustering algorithms if needed.
 * 
 * @author Spence Green
 *
 */
public class MakeWordClasses {

  private final int numIterations;
  private final int numClasses;
  private final int numThreads;
  private final int vparts;
  private final int order;
  private final String inputEncoding;

  private final Logger logger;

  private static enum OutputFormat {SRILM, TSV};

  private static final int INITIAL_CAPACITY = 100000;
  private final Map<IString,Integer> wordToClass;
  private final Counter<IString> wordCount;
  private final TwoDimensionalCounter<IString, NgramHistory> historyCount;
  private TwoDimensionalCounter<Integer,NgramHistory> classHistoryCount;
  private final ClassicCounter<Integer> classCount;
  private final OutputFormat outputFormat;
  private final int vocabThreshold;
  private List<IString> effectiveVocabulary;
  private final boolean normalizeDigits;

  private double currentObjectiveValue = 0.0;
  
  public MakeWordClasses(Properties properties) {
    // User options
    this.numIterations = PropertiesUtils.getInt(properties, "niters", 30);
    assert this.numIterations > 0;

    this.numClasses = PropertiesUtils.getInt(properties, "nclasses", 512);
    assert this.numClasses > 0;

    this.numThreads = PropertiesUtils.getInt(properties, "nthreads", 1);
    assert this.numThreads > 0;

    this.vparts = PropertiesUtils.getInt(properties, "vparts", 3);
    assert this.vparts > 0;

    this.order = PropertiesUtils.getInt(properties, "order", 2);
    assert this.order > 1;

    this.vocabThreshold = PropertiesUtils.getInt(properties, "vclip", 5);
    assert this.vocabThreshold >=0;
    
    this.inputEncoding = properties.getProperty("encoding", "UTF-8");

    this.normalizeDigits = PropertiesUtils.getBool(properties, "normdigits", true);

    this.outputFormat = OutputFormat.valueOf(
        properties.getProperty("format", OutputFormat.TSV.toString()).toUpperCase());

    logger = Logger.getLogger(this.getClass().getName());
    SystemLogger.logLevel = Level.FINE;
    SimpleDateFormat sdf = new SimpleDateFormat("HH-mm-ss");
    SystemLogger.prefix = properties.getProperty("name", 
        String.format("%d-classes.%s", numClasses, sdf.format(new Date())));
    SystemLogger.attach(logger, LogName.WORD_CLASS);
    
    logger.info("#iterations: " + String.valueOf(numIterations));
    logger.info("#classes: " + String.valueOf(numClasses));
    logger.info("order: " + String.valueOf(order));
    logger.info("#vocabulary partitions: " + String.valueOf(vparts));
    logger.info("Rare word threshold: " + String.valueOf(vocabThreshold));
    logger.info("Input file encoding: " + inputEncoding);
    if (normalizeDigits) {
      logger.info("Mapping all ASCII digit characters to 0");
    }

    // Internal data structures
    wordToClass = Generics.newHashMap(INITIAL_CAPACITY);
    wordCount = new ClassicCounter<IString>(INITIAL_CAPACITY);
    classCount = new ClassicCounter<Integer>(numClasses);
    historyCount = new TwoDimensionalCounter<IString,NgramHistory>();
    classHistoryCount = new TwoDimensionalCounter<Integer,NgramHistory>();
  }

  /**
   * Read the input and create the initial clustering.
   * 
   * @param filenames
   * @throws IOException
   */
  private void initialize(String[] filenames) throws IOException {
    List<IString> defaultHistory = Generics.newLinkedList();
    for (int i = 0; i < order-1; ++i) {
      defaultHistory.add(TokenUtils.START_TOKEN);
    }
    
    // Read the vocabulary and histories
    final long startTime = System.nanoTime();
    for (String filename : filenames) {
      logger.info("Reading: " + filename);
      LineNumberReader reader = IOTools.getReaderFromFile(filename, inputEncoding);
      for (String line; (line = reader.readLine()) != null;) {
        Sequence<IString> tokens = IStrings.tokenize(line.trim());
        List<IString> history = Generics.newLinkedList(defaultHistory);
        for (IString token : tokens) {
          if (normalizeDigits && TokenUtils.hasDigit(token.toString())) {
            token = new IString(TokenUtils.normalizeDigits(token.toString()));
          }
          wordCount.incrementCount(token);
          historyCount.incrementCount(token, new NgramHistory(history));

          // Update the ngram history
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

    // Collapse vocabulary by mapping rare words to <unk>
    Set<IString> fullVocabulary = Generics.newHashSet(wordCount.keySet());
    Set<IString> filteredWords = Generics.newHashSet(fullVocabulary.size());
    for (IString word : fullVocabulary) {
      int count = (int) wordCount.getCount(word);
      if (vocabThreshold > 0 && count < vocabThreshold) {
        filteredWords.add(word);
        wordCount.incrementCount(TokenUtils.UNK_TOKEN, count);
        wordCount.remove(word);
        Counter<NgramHistory> histories = historyCount.getCounter(word);
        Counter<NgramHistory> unkHistories = historyCount.getCounter(TokenUtils.UNK_TOKEN);
        Counters.addInPlace(unkHistories, histories);
        historyCount.remove(word);
      }
    }

    // Setup the vocabulary that will be clustered (i.e., the
    // effective vocabulary)
    if (filteredWords.size() > 0) {
      logger.info(String.format("Mapping %d / %d words to unk token %s", 
          filteredWords.size(), fullVocabulary.size(), TokenUtils.UNK_TOKEN.toString()));
      fullVocabulary.add(TokenUtils.UNK_TOKEN);
    }
    fullVocabulary.removeAll(filteredWords);
    effectiveVocabulary = Generics.newArrayList(fullVocabulary);

    // Initialize clustering
    Collections.sort(effectiveVocabulary, Counters.toComparator(wordCount, false, true));
    for (int i = 0; i < effectiveVocabulary.size(); ++i) {
      IString word = effectiveVocabulary.get(i);
      int classId = i % numClasses;
      classCount.incrementCount(classId, wordCount.getCount(word));
      wordToClass.put(word, classId);
      Counter<NgramHistory> historiesForWord = historyCount.getCounter(word);
      Counter<NgramHistory> historiesForClass = classHistoryCount.getCounter(classId);
      Counters.addInPlace(historiesForClass, historiesForWord);
    }
    Collections.shuffle(effectiveVocabulary);

    // Debug output
    logger.info("Effective vocabulary size: " + String.valueOf(effectiveVocabulary.size()));
    currentObjectiveValue = objectiveFunctionValue();
    logger.info("Finished generating initial cluster assignment");
    logger.info(String.format("Initial objective function value: %.3f%n", currentObjectiveValue));
  }

  /**
   * Create word clusters from the list of input files.
   * 
   * @param filenames
   */
  public void run(String[] filenames) {
    final long runStartTime = System.nanoTime();
    try {
      initialize(filenames);
    } catch (IOException e1) {
      throw new RuntimeException(e1);
    }

    logger.info(String.format("Starting clustering with %d threads", numThreads));
    for (int e = 0; e < numIterations; ++e) {
      MulticoreWrapper<ClustererState,PartialStateUpdate> threadpool = 
          new MulticoreWrapper<ClustererState,PartialStateUpdate>(numThreads, 
              new ThreadsafeProcessor<ClustererState,PartialStateUpdate>() {
            @Override
            public PartialStateUpdate process(ClustererState input) {
              OneSidedObjectiveFunction algorithm = new OneSidedObjectiveFunction(input);
              return algorithm.cluster();
            }
            @Override
            public ThreadsafeProcessor<ClustererState, PartialStateUpdate> newInstance() {
              return this;
            }
          });
      
      // Select vocabulary partition number
      final int partitionNumber = e % vparts;

      if (e > 0 && partitionNumber == 0) {
        logger.info("Sorting vocabulary according to the current class assignments");
        Collections.sort(effectiveVocabulary, new WordClassComparator(wordToClass));
      }

      logger.info(String.format("Iteration %d: partition %d start", e, partitionNumber));
      final long iterationStartTime = System.nanoTime();
      int startIndex = 0;
      for (int t = 0; t < numThreads; ++t) {
        Pair<ClustererState,Integer> input = createInput(partitionNumber, t, startIndex);
        if (input != null) {
          threadpool.put(input.first());
          startIndex = input.second();
        }
      }

      // Wait for shutdown and process results
      threadpool.join();
      int numUpdates = 0;
      while(threadpool.peek()) {
        PartialStateUpdate result = threadpool.poll();
        numUpdates += updateCountsWith(result);
      }

      // Clean out zeros from counters after updating
      classHistoryCount.clean();
      Counters.retainNonZeros(classCount);

      double elapsedTime = ((double) System.nanoTime() - iterationStartTime) / 1e9;
      logger.info(String.format("Iteration %d: elapsed time %.3fsec", e, elapsedTime));
      logger.info(String.format("Iteration %d: #updates %d", e, numUpdates));
      logger.info(String.format("Iteration %d: objective: %.4f", e, objectiveFunctionValue()));
    }

    double elapsedTime = ((double) System.nanoTime() - runStartTime) / 1e9;
    logger.info(String.format("Total runtime: %.3fsec", elapsedTime));
  }

  private static class WordClassComparator implements Comparator<IString> {
    Map<IString, Integer> map;
    public WordClassComparator(Map<IString, Integer> map) {
      this.map = map;
    }
    public int compare(IString a, IString b) {
      int classA = map.get(a);
      int classB = map.get(b);
      if (classA < classB) {
        return -1;
      } else if (classA > classB) {
        return 1;
      } else {
        return 0;
      }
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
        assert count > 0.0;
        objValue += count * Math.log(count);
      }
      double count = classCount.getCount(classId);
      if (count > 0.0) {
        objValue -= count * Math.log(count);
      } else {
        logger.warning("Empty cluster: " + String.valueOf(classId));
      }
    }
    return objValue;
  }

  /**
   * Create the input to a clustering iteration.
   * 
   * @param fullVocabulary
   * @param partitionNumber
   * @param threadId
   * @return
   */
  private Pair<ClustererState,Integer> createInput(int partitionNumber, int threadId, int inputStart) {
    int partitionSize = effectiveVocabulary.size() / vparts;
    int partitionStart = partitionNumber*partitionSize;
    int partitionEnd = partitionNumber == vparts-1 ? effectiveVocabulary.size() : (partitionNumber+1)*partitionSize;
    partitionSize = partitionEnd-partitionStart;

    int targetInputSize = partitionSize / numThreads;
    int startIndex = inputStart == 0 ? partitionStart + inputStart : inputStart;
    int endIndex = Math.min(partitionEnd, startIndex + targetInputSize);
    if (endIndex - startIndex <= 0) return null;
    
    // Brants and Uszkoreit heuristic: make sure that all words from a given class
    // end up in the same worker.
    int i = endIndex-1;
    for (; i < partitionEnd-1; ++i) {
      IString iWord = effectiveVocabulary.get(i);
      IString nextWord = effectiveVocabulary.get(i+1);
      int iClass= wordToClass.get(iWord);
      int nextClass= wordToClass.get(nextWord);
      if (iClass != nextClass) {
        break;
      }
    }
    logger.info(String.format("endIndex: %d -> %d", endIndex, i+1));
    endIndex = i+1;

    List<IString> inputVocab = effectiveVocabulary.subList(startIndex, endIndex);
    
    logger.info(String.format("Partition %d thread %d size %d: input %d-%d", partitionNumber,
        threadId, inputVocab.size(), startIndex, endIndex-1));
    
    // Create the state
    ClustererState state =  new ClustererState(inputVocab, this.wordCount, 
        this.historyCount, this.wordToClass, this.classCount, this.classHistoryCount,
        numClasses, this.currentObjectiveValue);
    return new Pair<ClustererState,Integer>(state, endIndex);
  }

  private int updateCountsWith(PartialStateUpdate result) {
    // Update counts
    Counters.addInPlace(classCount, result.deltaClassCount);
    Set<Integer> classes = result.deltaClassHistoryCount.firstKeySet();
    for (Integer classId : classes) {
      Counter<NgramHistory> counter = this.classHistoryCount.getCounter(classId);
      Counter<NgramHistory> delta = result.deltaClassHistoryCount.getCounter(classId);
      Counters.addInPlace(counter, delta);
    }

    // Update assignments
    int numUpdates = 0;
    for (Map.Entry<IString, Integer> assignment : result.wordToClass.entrySet()) {
      int oldAssignment = wordToClass.get(assignment.getKey());
      int newAssignment = assignment.getValue();
      if (oldAssignment != newAssignment) {
        ++numUpdates;
        wordToClass.put(assignment.getKey(), assignment.getValue());
      }
    }
    return numUpdates;
  }
  
  /**
   * Write the final cluster assignments to the specified output stream.
   * 
   * @param out
   */
  public void writeResults(PrintStream out) {
    logger.info(String.format("Writing final class assignments in %s format",
        outputFormat.toString()));
    Collections.sort(effectiveVocabulary, new WordClassComparator(wordToClass));
    for (IString word : effectiveVocabulary) {
      int assignment = wordToClass.get(word);
      if (outputFormat == OutputFormat.TSV) {
        out.printf("%s\t%d%n", word.toString(), assignment);

      } else if (outputFormat == OutputFormat.SRILM) {
        out.printf("%d 1.0 %s%n", assignment, word.toString());
      }
    }
  }

  private static Map<String, Integer> optionArgDefs() {
    Map<String,Integer> argDefs = Generics.newHashMap();
    argDefs.put("order", 1);
    argDefs.put("nthreads", 1);
    argDefs.put("nclasses", 1);
    argDefs.put("niters", 1);
    argDefs.put("vparts", 1);
    argDefs.put("format", 1);
    argDefs.put("name", 1);
    argDefs.put("vclip", 1);
    argDefs.put("normdigits", 0);
    argDefs.put("encoding", 1);
    return argDefs;
  }

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    final String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(MakeWordClasses.class.getName()).append(" OPTS file [file] > output").append(nl)
    .append(" -order num     : Model order (default: 2)").append(nl)
    .append(" -nthreads num  : Number of threads (default: 1)").append(nl)
    .append(" -nclasses num  : Number of classes (default: 512)").append(nl)
    .append(" -niters num    : Number of iterations (default: 30)").append(nl)
    .append(" -vparts num    : Number of vocabulary partitions (default: 3)").append(nl)
    .append(" -format type   : Output format [srilm|tsv] (default: tsv)").append(nl)
    .append(" -name str      : Run name for log file.").append(nl)
    .append(" -vclip num     : Map rare words to <unk> (default: 5)").append(nl)
    .append(" -normdigits    : Map ASCII digits to 0 (default: true)").append(nl)
    .append(" -encoding str  : Input file encoding (default: UTF-8)");

    return sb.toString();
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    String[] filenames = options.getProperty("","").split("\\s+");
    if (filenames.length < 1 || filenames[0].length() == 0 || options.containsKey("h")
        || options.containsKey("help")) {
      System.err.println(usage());
      System.exit(-1);
    }
    MakeWordClasses mkWordCls = new MakeWordClasses(options);
    mkWordCls.run(filenames);
    mkWordCls.writeResults(System.out);
  }
}
