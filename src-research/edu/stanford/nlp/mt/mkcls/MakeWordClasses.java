package edu.stanford.nlp.mt.mkcls;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
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
 * TODO Add logger
 * TODO Extract out objective function as an interface to support
 * other clustering algorithms if needed.
 * 
 * @author Spence Green
 *
 */
public class MakeWordClasses {

  private static final String START_TOKEN = "<s>";
  
  private final int numIterations;
  private final int numClasses;
  private final int numThreads;
  private final int order;
  
  private static final int INITIAL_CAPACITY = 100000;
  private final Map<IString,Integer> wordToClass;
  private final Counter<IString> wordCount;
  private final TwoDimensionalCounter<IString, NgramHistory> historyCount;
  private final IString startToken;
  private final TwoDimensionalCounter<Integer,NgramHistory> classHistoryCount;
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
    
    // Internal data structures
    wordToClass = Generics.newHashMap(INITIAL_CAPACITY);
    wordCount = new ClassicCounter<IString>(INITIAL_CAPACITY);
    classCount = new ClassicCounter<Integer>(numClasses);
    historyCount = new TwoDimensionalCounter<IString,NgramHistory>();
    startToken = new IString(START_TOKEN);
    classHistoryCount = new TwoDimensionalCounter<Integer,NgramHistory>();
  }

  private void initialize(String[] filenames) throws IOException {
    List<IString> defaultHistory = Generics.newLinkedList();
    for (int i = 0; i < order-1; ++i) {
      defaultHistory.add(startToken);
    }
    // Read the vocabulary and histories
    for (String filename : filenames) {
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
  }

  public void run(String[] filenames) {
    try {
      initialize(filenames);
    } catch (IOException e1) {
      throw new RuntimeException(e1);
    }
    
    final List<IString> vocab = Generics.newArrayList(wordCount.keySet());
    for (int e = 0; e < numIterations; ++e) {
      // Randomly shuffle the vocabulary
      Collections.shuffle(vocab);
      
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
      
      for (int t = 0; t < numThreads; ++t) {
        ClustererInput input = createInput(vocab, t);
        threadpool.put(input);
      }

      // Wait for shutdown and process results
      threadpool.join();
      clearDataStructures();
      while(threadpool.peek()) {
        ClustererOutput result = threadpool.poll();
        updateCountsWith(result);
      }
    }
  }
  
  private void clearDataStructures() {
    // TODO reset all of the data structures prior to receiving the processed results
  }

  private ClustererInput createInput(List<IString> vocab, int t) {
    int inputSize = vocab.size() / numThreads;
    int start = t * inputSize;
    int end = Math.min((t+1)*inputSize, vocab.size());
    List<IString> inputVocab = vocab.subList(start, end);
    return new ClustererInput(inputVocab, this.wordCount, 
        this.historyCount, this.wordToClass, this.classCount, this.classHistoryCount);
  }

  private void updateCountsWith(ClustererOutput result) {
    // TODO Auto-generated method stub
    
  }

  public void writeResults(PrintStream out) {
    // Sort the output by class id
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
    MakeWordClasses mkcls = new MakeWordClasses(options);
    mkcls.run(filenames);
    mkcls.writeResults(System.out);
  }
}
