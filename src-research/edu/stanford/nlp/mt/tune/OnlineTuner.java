package edu.stanford.nlp.mt.tune;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.BLEUOracleMetric;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.tune.optimizers.MIRA1BestHopeFearOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.OnlineOptimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Tunes a machine translation model with an online algorithm.
 * 
 * TODO(spenceg) Figure out format of alternative phrase table for human features.
 *               
 * @author Spence Green
 *
 */
public class OnlineTuner {

  /**
   * Number of online epochs
   */
  private static int NUM_EPOCHS = 1;
  
  // Static methods for setting up a global logger
  // Other classes should attach() to this log handler
  private static Handler logHandler = null;
  private static String logPrefix;
  
  private static void initLogger(String tag) {
    SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-HH-mm-ss");
    logPrefix = String.format("online-tuner.%s.%s", tag, formatter.format(new Date()));
    try {
      logHandler = new FileHandler(logPrefix + ".log");
      logHandler.setFormatter(new SimpleFormatter()); //Plain text
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  public static void attach(Logger logger) { 
    if (logHandler != null) {
      logger.addHandler(logHandler);
    }
  }

  // What it says
  private final Logger logger;

  // Intrinsic loss examples
  private List<Sequence<IString>> tuneSource;
  private List<Sequence<IString>> tuneTarget;
  
  // Extrinsic loss examples
  private List<Sequence<IString>> altSource;
  private List<Sequence<IString>> altTarget;
  
  /**
   * Weight vector for Phrasal
   */
  private Counter<String> wts;
  
  /**
   * Phrasal decoder instance.
   */
  private Phrasal decoder;
  
  public OnlineTuner(String srcFile, 
      String tgtFile, 
      String phrasalIniFile, 
      String wtsInitialFile) {
    // Write the error log to file
    logger = Logger.getLogger(OnlineTuner.class.getName());
    OnlineTuner.attach(logger);

    // Load initial weights
    try {
      wts = IOTools.readWeights(wtsInitialFile, null);
      logger.info("Initial weights: " + wts.toString());
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    
    // Load the source and target files for the intrinsic loss.
    tuneSource = IStrings.fileSplitToIStrings(srcFile);
    tuneTarget = IStrings.fileSplitToIStrings(tgtFile);
    assert tuneSource.size() == tuneTarget.size();
    logger.info(String.format("Intrinsic loss corpus contains %d examples", tuneSource.size()));
    
    // Load decoder (Phrasal)
    try {
      Map<String, List<String>> config = Phrasal.readConfig(phrasalIniFile);
      Phrasal.initStaticMembers(config);
      decoder = new Phrasal(config);
      FlatPhraseTable.lockIndex();
      logger.info("Loaded Phrasal from: " + phrasalIniFile);
      
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  /**
   * Load a source and target file that will be used for computing the extrinsic loss.
   * We will sample from these files.
   * 
   * @param altSourceFile
   * @param altTargetFile
   */
  private void sampleExtrinsicLossFrom(String altSourceFile,
      String altTargetFile) {
    altSource = IStrings.fileSplitToIStrings(altSourceFile);
    altTarget = IStrings.fileSplitToIStrings(altTargetFile);
    assert altSource.size() == altTarget.size();
    logger.info(String.format("Extrinsic loss corpus contains %d examples", altSource.size()));
  }

  /**
   * Input data to the weight updater.
   * 
   * @author Spence Green
   *
   */
  private class UpdaterInput {
    public final Sequence<IString> source;
    public final List<Sequence<IString>> references;
    public final int translationId;
    public final Counter<String> initialWts;
    public final int inputId;
    public UpdaterInput(Sequence<IString> input, 
        List<Sequence<IString>> references, 
        Counter<String> wts, int translationId, int inputId) {
      this.source = input;
      this.translationId = translationId;
      this.references = references;
      this.initialWts = wts;
      this.inputId = inputId;
    }
  }
  
  /**
   * Output of the weight updater.
   * 
   * @author Spence Green
   *
   */
  private class UpdaterOutput {
    public final Counter<String> weights;
    public final int inputId;
    public UpdaterOutput(Counter<String> weights, int inputId) {
      this.weights = weights;
      this.inputId = inputId;
    }
  }
  
  /**
   * Wrapper around the decoder and optimizer for asynchronous online training.
   * 
   * @author Spence Green
   *
   */
  private class AsynchronousUpdater implements ThreadsafeProcessor<UpdaterInput,UpdaterOutput> {

    private final OnlineOptimizer<IString, String> optimizer; 
    private final SentenceLevelMetric<IString, String> objective;
    private final int threadId;
    
    private int childThreadId;
    
    public AsynchronousUpdater(OnlineOptimizer<IString, String> optimizer, 
      SentenceLevelMetric<IString, String> objective, int firstThreadId) {
      this.optimizer = optimizer;
      this.objective = objective;
      this.threadId = firstThreadId;
      this.childThreadId = firstThreadId+1;
    }
    
    @Override
    public UpdaterOutput process(UpdaterInput input) {
      assert input.initialWts != null;
      // Set the decoder weights and decode
      decoder.getScorer(threadId).updateWeights(input.initialWts);
      List<RichTranslation<IString,String>> nbestList = decoder.decode(input.source, input.translationId, threadId);
      
      // Tune weights
      Counter<String> newWeights = 
          optimizer.update(input.source, input.translationId, nbestList, input.references, objective, input.initialWts);
      return new UpdaterOutput(newWeights, input.inputId);
    }

    @Override
    public ThreadsafeProcessor<UpdaterInput, UpdaterOutput> newInstance() {
      return new AsynchronousUpdater(optimizer, objective, childThreadId++);
    }
  }
  
  /**
   * Get results from the threadpool. Return the "best" weight vector for the next round.
   * 
   * Policy: return the "least stale" vector, i.e., the one with the latest submission id.
   * 
   * TODO(spenceg): Experiment with this policy.
   * 
   * @param threadpool
   * @param decoderWts 
   * @return
   */
  private Counter<String> mergeResultsFrom(MulticoreWrapper<UpdaterInput,UpdaterOutput> threadpool, 
      Counter<String> currentWts) {
    int lastSubmittedId = Integer.MIN_VALUE;
    Counter<String> latestWts = currentWts;
    while (threadpool.peek()) {
      UpdaterOutput result = threadpool.poll();
      wts.addAll(result.weights);
      if (result.inputId > lastSubmittedId) {
        // Use the most recent result as the weight vector for the next round
        latestWts = result.weights;
        lastSubmittedId = result.inputId;
      }
    }
    assert latestWts != null;
    return latestWts;
  }
  
  /**
   * Run an optimization algorithm with a specified loss function. Implements asynchronous updating
   * per Langford et al. (2009).
   * 
   * @param lossFunction 
   * @param optimizerAlg
   * @param optimizerFlags 
   * @param nThreads
   */
  private void run(OnlineOptimizer<IString, String> optimizer, 
      SentenceLevelMetric<IString, String> lossFunction) {
    // Initialize weight vector(s) for the decoder
    // decoderWts will be used in every round; wts will accumulate weight vectors
    final int numThreads = decoder.getNumThreads();
    Counter<String> decoderWts = new ClassicCounter<String>(wts);
    for (int i = 0; i < numThreads; ++i) decoder.getScorer(i).updateWeights(decoderWts);
    wts.clear();
        
    // Create a vector for randomizing the order of training instances.
    final int tuneSetSize = tuneSource.size();
    int[] indices = ArrayMath.range(0, tuneSetSize);
    
    // Online optimization with asynchronous updating
    logger.info("Start of online tuning; number of epochs: " + NUM_EPOCHS);
    for (int epoch = 0; epoch < NUM_EPOCHS; ++epoch) {
      final long startTime = System.nanoTime();
      logger.info("Start of epoch: " + epoch);

      // Threadpool for decoders. Create one per epoch so that we can wait for all jobs
      // to finish at the end of the epoch
      // TODO(spenceg): Langford et al. do not order results. But it's cheap, so should do it?
      boolean orderResults = false;
      final MulticoreWrapper<UpdaterInput,UpdaterOutput> threadpool = 
          new MulticoreWrapper<UpdaterInput,UpdaterOutput>(numThreads, 
              new AsynchronousUpdater(optimizer,lossFunction,0), orderResults);

      // Randomize order of training examples in-place (Langford et al. (2009), p.4)
      ArrayMath.shuffle(indices);
      int inputId = 0;
      for (final int translationId : indices) {
        // Retrieve the training example
        final Sequence<IString> source = tuneSource.get(translationId);
        final Sequence<IString> target = tuneTarget.get(translationId);
        final List<Sequence<IString>> references = new ArrayList<Sequence<IString>>();
        references.add(target);
        
        // Submit to threadpool, then look for updates.
        UpdaterInput input = new UpdaterInput(source, references, decoderWts, translationId, inputId++);
        threadpool.put(input);
        decoderWts = mergeResultsFrom(threadpool, decoderWts);
        
        // TODO(spenceg): Extract rules and update phrase table for this example
      }
      
      // Wait for threadpool shutdown for this epoch
      threadpool.join();
      decoderWts = mergeResultsFrom(threadpool, decoderWts);
      
      // Write out (averaged) intermediate weights
      Counter<String> iWts = new ClassicCounter<String>(wts);
      Counters.divideInPlace(iWts, (epoch+1)*tuneSetSize);
      IOTools.writeWeights(String.format("%s.%d.binwts", logPrefix, epoch), iWts);
      
      // TODO(spenceg): iWts should be the starting point for the next epoch!
      
      long elapsedTime = System.nanoTime() - startTime;
      logger.info(String.format("Epoch %d elapsed time: %.2f seconds", epoch, elapsedTime / 1000000000.0));
    }
    
    // Average final weights
    Counters.divideInPlace(wts, NUM_EPOCHS*tuneSetSize);
    logger.info("Final weights: " + wts.toString());
  }

  
  private static OnlineOptimizer<IString, String> loadOptimizer(String optimizerAlg, String[] optimizerFlags) {
    if (optimizerAlg.equals("mira-1best")) {
      return new MIRA1BestHopeFearOptimizer(optimizerFlags);
    } else if (optimizerAlg.equals("arow")) {
      // TODO(spenceg)
      throw new UnsupportedOperationException();
    }
    throw new UnsupportedOperationException();
  }
  

  private static SentenceLevelMetric<IString, String> loadLossFunction(
      String lossFunctionStr) {
    if (lossFunctionStr.equals("bleu-chiang")) {
      return new BLEUOracleMetric<IString,String>();
    }
    // TODO(spenceg): We could try other metrics, but Chiang's seems to work best?
    throw new UnsupportedOperationException();
  }


  /**
   * Save the final weights file to file.
   * 
   * @param wtsFinalFile
   */
  private void saveFinalWeights() {
    String filename = logPrefix + ".final.binwts";
    IOTools.writeWeights(filename, wts);
    logger.info("Wrote final weights to " + filename);
  }
  
  /**
   * Perform any necessary cleanup.
   */
  private void shutdown() {
    logHandler.close();
    decoder.shutdown();
  }
  
  /********************************************
   * MAIN METHOD STUFF
   ********************************************/
  
  /**
   * Command-line parameter specification.
   * 
   * @return
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionMap = new HashMap<String,Integer>();
    optionMap.put("s", 1);
    optionMap.put("t", 1);
    optionMap.put("e", 1);
    optionMap.put("o", 1);
    optionMap.put("of", 1);
    optionMap.put("m", 1);
    optionMap.put("n", 1);
    return optionMap;
  }
  
  // TODO(spenceg): Add experiment name parameter
  
  /**
   * Usage string for the main method.
   * 
   * @return
   */
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(OnlineTuner.class.getName()).append(" [OPTIONS] src tgt phrasal_ini wts_initial").append(nl);
    sb.append(nl);
    sb.append("Options:").append(nl);
    sb.append("   -s file    : Extrinsic loss: source file").append(nl);
    sb.append("   -t file    : Extrinsic loss: target file").append(nl);
    sb.append("   -e num     : Number of online epochs").append(nl);
    sb.append("   -o str     : Optimizer: [arow,mira-1best]").append(nl);
    sb.append("   -of str    : Optimizer flags [comma-separated list]").append(nl);
    sb.append("   -m str     : Evaluation metric (loss function) for the tuning algorithm").append(nl);
    sb.append("   -n str     : Experiment name.").append(nl);
    return sb.toString().trim();
  }
  
  /**
   * Online optimization for machine translation.
   * 
   * @param args
   */
  public static void main(String[] args) {
    // Parse command-line parameters
    Properties opts = StringUtils.argsToProperties(args, optionArgDefs());
    NUM_EPOCHS = PropertiesUtils.getInt(opts, "e", NUM_EPOCHS);
    String optimizerAlg = opts.getProperty("o", "mira-1best");
    String[] optimizerFlags = opts.containsKey("of") ? opts.getProperty("of").split(",") : null;
    String lossFunctionStr = opts.getProperty("m", "bleu-chiang");
    String altSourceFile = opts.getProperty("s");
    String altTargetFile = opts.getProperty("t");
    String experimentName = opts.getProperty("n", "debug");
    
    // Parse arguments
    String[] parsedArgs = opts.getProperty("").split("\\s+");
    if (parsedArgs.length != 4) {
      System.err.println(usage());
      System.exit(-1);
    }
    String srcFile = parsedArgs[0];
    String tgtFile = parsedArgs[1];
    String phrasalIniFile = parsedArgs[2];
    String wtsInitialFile = parsedArgs[3];
   
    final long startTime = System.nanoTime();
    OnlineTuner.initLogger(experimentName);
    System.out.println("Phrasal Online Tuner");
    System.out.printf("Startup: %s%n", new Date());
    System.out.println("====================");
    for (Entry<String, String> option : PropertiesUtils.getSortedEntries(opts)) {
      System.out.printf(" %s\t%s%n", option.getKey(), option.getValue());
    }
    System.out.println("====================");
    System.out.println();
    
    // Run optimization
    final OnlineOptimizer<IString,String> optimizer = loadOptimizer(optimizerAlg, optimizerFlags);
    final SentenceLevelMetric<IString,String> lossFunction = loadLossFunction(lossFunctionStr);
    OnlineTuner tuner = new OnlineTuner(srcFile, tgtFile, phrasalIniFile, wtsInitialFile);
    if (altSourceFile != null && altTargetFile != null) {
      tuner.sampleExtrinsicLossFrom(altSourceFile, altTargetFile);
    }
    tuner.run(optimizer, lossFunction);
    tuner.saveFinalWeights();
    tuner.shutdown();
    
    final long elapsedTime = System.nanoTime() - startTime;
    System.out.printf("Elapsed time: %.2f seconds%n", elapsedTime / 1000000000.0);
    System.out.printf("Finished at: %s%n", new Date());
  }
}
