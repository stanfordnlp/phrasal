package edu.stanford.nlp.mt.tune;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.EmptySequence;
import edu.stanford.nlp.mt.base.FlatNBestList;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.NBestListContainer;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SystemLogger;
import edu.stanford.nlp.mt.base.SystemLogger.LogName;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.Metrics;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetricFactory;
import edu.stanford.nlp.mt.tune.optimizers.CrossEntropyOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.ExpectedBLEUOptimizer2;
import edu.stanford.nlp.mt.tune.optimizers.MIRA1BestHopeFearOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.OptimizerUtils;
import edu.stanford.nlp.mt.tune.optimizers.PairwiseRankingOptimizerSGD;
import edu.stanford.nlp.mt.tune.optimizers.ExpectedBLEUOptimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Online model tuning for machine translation as described in
 * Green et al. (2013) (Proc. of ACL).
 * 
 * @author Spence Green
 *
 */
public class OnlineTuner {
  
  // Tuning set
  private List<Sequence<IString>> tuneSource;
  private List<List<Sequence<IString>>> references;
  private int numReferences;

  // Various options
  private boolean returnBestDev = false;
  private boolean doParameterAveraging = false;
  
  private Counter<String> wtsAccumulator;
  private final int expectedNumFeatures;

  // The optimization algorithm
  private OnlineOptimizer<IString,String> optimizer;

  // Phrasal decoder instance.
  private Phrasal decoder;
  
  private String outputWeightPrefix;
  
  // minimum number of times we need to see a feature 
  // before learning a decoding model weight for it 
  private int minFeatureCount;
  private final Map<String,Set<Integer>> clippedFeatureIndex = 
    new HashMap<String,Set<Integer>>();
  
  // Pseudo-reference selection
  private boolean createPseudoReferences = false;
  private String tempDirectory = "/tmp";
  private PrintStream nbestListWriter;
  private String nbestFilename;
  private int numPseudoReferences = -1;
  private int pseudoReferenceBurnIn = -1;
  private List<List<Sequence<IString>>> pseudoReferences;
  private double[] referenceWeights;
  
  private final Logger logger;

  // Thang Mar14
  // Print decode time: 0 -- no printing, 1 -- per epoch time, 2 -- per sent time
  private static int printDecodeTime = 0; 
  private final Timing tuneTimer = new Timing();
  
  
  /**
   * Constructor.
   * 
   * @param srcFile
   * @param tgtFile
   * @param phrasalIniFile
   * @param initialWtsFile
   * @param optimizerAlg
   * @param optimizerFlags
   * @param uniformStartWeights
   * @param randomizeStartWeights
   * @param expectedNumFeatures
   */
  public OnlineTuner(String srcFile, String tgtFile, String phrasalIniFile, 
      String initialWtsFile, String optimizerAlg, String[] optimizerFlags, 
      boolean uniformStartWeights, boolean randomizeStartWeights, int expectedNumFeatures) {
    logger = Logger.getLogger(OnlineTuner.class.getName());
    SystemLogger.attach(logger, LogName.ONLINE);
    this.outputWeightPrefix = SystemLogger.getPrefix() + "." + LogName.ONLINE.toString().toLowerCase();

    // Configure the initial weights
    this.expectedNumFeatures = expectedNumFeatures;
    wtsAccumulator = OnlineTuner.loadWeights(initialWtsFile, uniformStartWeights, randomizeStartWeights);
    logger.info("Initial weights: " + wtsAccumulator.toString());

    // Load the tuning set
    tuneSource = IStrings.tokenizeFile(srcFile);
    assert tuneSource.size() > 0;
    loadReferences(tgtFile);
    logger.info(String.format("Intrinsic loss corpus contains %d examples", tuneSource.size()));
    
    // Load Phrasal
    try {
      decoder = Phrasal.loadDecoder(phrasalIniFile);
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
    }
    logger.info("Loaded Phrasal from: " + phrasalIniFile);
    
    // Load the optimizer last since some optimizers depend on fields initialized
    // by OnlineTuner.
    optimizer = configureOptimizer(optimizerAlg, optimizerFlags);
    logger.info("Loaded optimizer: " + optimizer.toString());
  }

  /**
   * Configure selection of pseudo-references for the gold scoring metrics.
   * 
   * @param tmpPath
   */
  private void computePseudoReferences(String pseudoRefOptions,
      String tmpPath) {
    createPseudoReferences = true;
    tempDirectory = tmpPath;
    String[] options = pseudoRefOptions.split(",");
    assert options.length == 2 : "Invalid pseudoreference option string";
    numPseudoReferences = Integer.valueOf(options[0]);
    pseudoReferenceBurnIn = Integer.valueOf(options[1]);
    int tuneSetSize = tuneSource.size();
    pseudoReferences = new ArrayList<List<Sequence<IString>>>(tuneSetSize);
    for (int i = 0; i < tuneSetSize; ++i) {
      pseudoReferences.add(new LinkedList<Sequence<IString>>());
    }
    
    logger.info(String.format("Creating %d pseudoreferences", numPseudoReferences));
    logger.info("Pseudoreference temp directory: " + tempDirectory);
  }
  
  /**
   * Minimum number of times we need to see a feature
   * before learning a model weight for it.
   *
   * @param minFeatureCount
   */
  
  public void minFeatureCount(int minFeatureCount) {
    this.minFeatureCount = minFeatureCount;
  }

  /**
   * Return the weight vector from the epoch that maximizes
   * the training objective.
   * 
   * @param b
   */
  public void finalWeightsFromBestEpoch(boolean b) { this.returnBestDev = b; }

  /**
   * Average parameters between epochs.
   * 
   * @param b
   */
  private void doParameterAveraging(boolean b) { this.doParameterAveraging = b; }

  /**
   * Determine whether a feature has been seen enough times
   * to learn a decoding model weight for it
   */ 
  boolean hasMinFeatureCount(String feature) {
     if (minFeatureCount == 0) return true;
     Set<Integer> ids = clippedFeatureIndex.get(feature);
     if (ids == null) return false;
     return ids.size() >= minFeatureCount; 
  } 

  /** 
   * Update counts of the number of times we have seen each feature.
   * Features are only counted ounce per source sentence
   */
   void updateFeatureCounts(int[] translationIds, List<List<RichTranslation<IString,String>>> nbestLists) {
     for (int i = 0; i < translationIds.length; i++) {
       Set<String> features = new HashSet<String>();
       for (RichTranslation<IString,String> trans : nbestLists.get(i)) {
         for (FeatureValue<String> f : trans.features) {
           features.add(f.name);
         }
       }
       synchronized(clippedFeatureIndex) {
         for (String fName : features) {
           Set<Integer> ids = clippedFeatureIndex.get(fName);
           if (ids == null) {
             ids = new TreeSet<Integer>();
             clippedFeatureIndex.put(fName, ids); 
           }
           if (ids.size() < minFeatureCount) {
             ids.add(translationIds[i]);
           } 
         } 
       }
     }
   }

  /**
   * Input data to the gradient processor.
   * 
   * @author Spence Green
   *
   */
  private class ProcessorInput {
    public final List<Sequence<IString>> source;
    public final List<List<Sequence<IString>>> references;
    public final int[] translationIds;
    public final Counter<String> weights;
    public final int inputId;
    public ProcessorInput(List<Sequence<IString>> input, 
        List<List<Sequence<IString>>> references, 
        Counter<String> weights, int[] translationIds, int inputId) {
      this.source = input;
      this.translationIds = translationIds;
      this.references = references;
      this.inputId = inputId;
      // Copy here for thread safety. DO NOT change this unless you know
      // what you're doing....
      this.weights = new ClassicCounter<String>(weights);
    }
  }

  /**
   * Output of the gradient processor.
   * 
   * @author Spence Green
   *
   */
  private class ProcessorOutput {
    public final Counter<String> gradient;
    public final int inputId;
    public final List<List<RichTranslation<IString, String>>> nbestLists;
    public final int[] translationIds;
    public ProcessorOutput(Counter<String> gradient, 
        int inputId, 
        List<List<RichTranslation<IString, String>>> nbestLists, int[] translationIds) {
      this.gradient = gradient;
      this.inputId = inputId;
      this.nbestLists = nbestLists;
      this.translationIds = translationIds;
    }
  }

  /**
   * Wrapper around the decoder and optimizer for asynchronous online training.
   * 
   * @author Spence Green
   *
   */
  private class GradientProcessor implements ThreadsafeProcessor<ProcessorInput,ProcessorOutput> {
    private final OnlineOptimizer<IString, String> optimizer; 
    private final SentenceLevelMetric<IString, String> scoreMetric;
    private final int threadId;
    private final Timing timer; // Thang Mar14

    // Counter for the newInstance() method
    private int childThreadId;

    public GradientProcessor(OnlineOptimizer<IString, String> optimizer, 
        SentenceLevelMetric<IString, String> scoreMetric, int firstThreadId) {
      this.optimizer = optimizer;
      this.scoreMetric = scoreMetric;
      this.threadId = firstThreadId;
      this.childThreadId = firstThreadId+1;
      this.timer = new Timing();
    }

    @Override
    public ProcessorOutput process(ProcessorInput input) {
      assert input.weights != null;
      
      // The decoder does not copy the weight vector; ProcessorInput should have.
      decoder.getScorer(threadId).updateWeights(input.weights);
      
      int batchSize = input.translationIds.length;
      List<List<RichTranslation<IString,String>>> nbestLists = 
          new ArrayList<List<RichTranslation<IString,String>>>(input.translationIds.length);
      Counter<String> gradient;
      if (batchSize == 1) {
        // Conventional online learning
        List<RichTranslation<IString,String>> nbestList = decoder.decode(input.source.get(0), input.translationIds[0], 
            threadId);
        gradient = optimizer.getGradient(input.weights, input.source.get(0), 
            input.translationIds[0], nbestList, input.references.get(0), referenceWeights, scoreMetric);
        nbestLists.add(nbestList);
        
      } else {
        // Mini-batch learning
        for (int i = 0; i < input.translationIds.length; ++i) {
          int translationId = input.translationIds[i];
          Sequence<IString> source = input.source.get(i);
          
          List<RichTranslation<IString,String>> nbestList = decoder.decode(source, translationId, threadId);
          if(OnlineTuner.printDecodeTime>=2) { timer.end("Thread " + threadId + " ends decoding sent " + translationId + ", num words = " + source.size()); }
          
          nbestLists.add(nbestList);
        }

        gradient = optimizer.getBatchGradient(input.weights, input.source, input.translationIds, 
                nbestLists, input.references, referenceWeights, scoreMetric);
      }

      if (minFeatureCount > 0) {
        updateFeatureCounts(input.translationIds, nbestLists);
        Set<String> features = new TreeSet<String>(gradient.keySet());
        for (String feature : features) {
           if (!hasMinFeatureCount(feature)) {
              gradient.remove(feature);
           }
        } 
       
      }
     

      return new ProcessorOutput(gradient, input.inputId, nbestLists, input.translationIds);
    }

    @Override
    public ThreadsafeProcessor<ProcessorInput, ProcessorOutput> newInstance() {
      return new GradientProcessor(optimizer, scoreMetric, childThreadId++);
    }
  }

  /**
   * Asynchronous template from Langford et al. (2009). Get gradients from the threadpool and update the weight vector.
   * 
   * @param threadpool
   * @param updater 
   * @param nbestLists 
   * @param doExpectedBleu 
   * @param endOfEpoch
   * @param timeStep 
   * @param decoderWts 
   * @return
   */
  private int update(Counter<String> currentWts, 
      int updateStep, MulticoreWrapper<ProcessorInput,ProcessorOutput> threadpool, 
      OnlineUpdateRule<String> updater, Map<Integer, Sequence<IString>> nbestLists, 
      boolean endOfEpoch) {
    assert threadpool != null;
    assert currentWts != null;
    assert updater != null;
    
    // There may be more than one gradient available, so loop
    while (threadpool.peek()) {
      final ProcessorOutput result = threadpool.poll();
      boolean isEndOfEpoch = endOfEpoch && ! threadpool.peek();

      logger.info(String.format("Update %d gradient cardinality: %d", updateStep, result.gradient.keySet().size()));
      
      // Update rule. 
      updater.update(currentWts, result.gradient, updateStep, isEndOfEpoch);

      // Debug info
      logger.info(String.format("Update %d with gradient from input step %d (diff: %d)", 
          updateStep, result.inputId, result.inputId - updateStep));
      logger.info(String.format("Update %d approximate L2 ||w'-w|| %.4f", updateStep, Counters.L2Norm(result.gradient)));
      logger.info(String.format("Update %d cardinality: %d", updateStep, currentWts.keySet().size()));
      ++updateStep;

      // Accumulate intermediate weights for parameter averaging
      if (doParameterAveraging) {
        wtsAccumulator.addAll(currentWts);
      }

      // Do something with the n-best lists before dumping them?
      if (nbestLists != null || createPseudoReferences) {
        for (int i = 0; i < result.translationIds.length; ++i) {
          int sourceId = result.translationIds[i];
          if (createPseudoReferences && nbestListWriter != null) {
            IOTools.writeNbest(result.nbestLists.get(i), sourceId, true, nbestListWriter);
          }
          if (nbestLists != null) {
            assert ! nbestLists.containsKey(sourceId);
            // For expected bleu evaluations, put the one best prediction as opposed to the n best list as before.
            if (result.nbestLists.get(i).size() > 0) {
              Sequence<IString> bestHypothesis = result.nbestLists.get(i).get(0).translation;
              nbestLists.put(sourceId, bestHypothesis);
            } else {
              nbestLists.put(sourceId, new EmptySequence<IString>());
            }
          }
        }
      }
    }
    
    return updateStep;
  }

  /**
   * Run an optimization algorithm with a specified loss function. Implements asynchronous updating
   * per Langford et al. (2009).
   * @param batchSize 
   * 
   * @param scoreMetric 
   * @param doExpectedBleu 
   * @param randomizeStartingWeights 
   * @param optimizerAlg
   * @param optimizerFlags 
   * @param nThreads
   */
  public void run(int numEpochs, int batchSize, SentenceLevelMetric<IString, String> scoreMetric, 
      boolean doExpectedBleu, int weightWriteOutInterval) {
    // Initialize weight vector(s) for the decoder
    // currentWts will be used in every round; wts will accumulate weight vectors
    final int numThreads = decoder.getNumThreads();
    Counter<String> currentWts = new ClassicCounter<String>(wtsAccumulator);
    // Clear the accumulator, which we will use for parameter averaging.
    wtsAccumulator.clear();
    
    final int tuneSetSize = tuneSource.size();
    final int[] indices = ArrayMath.range(0, tuneSetSize);
    final int numBatches = (int) Math.ceil((double) indices.length / (double) batchSize);
    final OnlineUpdateRule<String> updater = optimizer.newUpdater();
    final List<Triple<Double,Integer,Counter<String>>> epochWeights = 
        new ArrayList<Triple<Double,Integer,Counter<String>>>(numEpochs);
    final Runtime runtime = Runtime.getRuntime();

    logger.info("Start of online tuning");
    logger.info("Number of epochs: " + numEpochs);
    logger.info("Number of threads: " + numThreads);
    logger.info("Number of references: " + numReferences);
    int updateId = 0;
    

    // Threadpool for decoders. Create one per epoch so that we can wait for all jobs
    // to finish at the end of the epoch
    boolean orderResults = false;
    
    for (int epoch = 0; epoch < numEpochs; ++epoch) {
      final long startTime = System.nanoTime();
      logger.info("Start of epoch: " + epoch);
      if(OnlineTuner.printDecodeTime>0) { tuneTimer.start("# Start tuning for epoch " + epoch); }    
      
      // n-best lists. Purge for each epoch
      Map<Integer,Sequence<IString>> nbestLists = doExpectedBleu ? 
          new HashMap<Integer,Sequence<IString>>(tuneSetSize) : null;
      if (createPseudoReferences) updatePseudoReferences(epoch);

      MulticoreWrapper<ProcessorInput,ProcessorOutput> wrapper = new MulticoreWrapper<ProcessorInput,ProcessorOutput>(numThreads, 
          new GradientProcessor(optimizer,scoreMetric,0), orderResults);
      
      // Randomize order of training examples in-place (Langford et al. (2009), p.4)
      ArrayMath.shuffle(indices);
      logger.info(String.format("Number of batches for epoch %d: %d", epoch, numBatches));
      for (int t = 0; t < numBatches; ++t) {
        logger.info(String.format("Epoch %d batch %d memory free: %d  max: %d", epoch, t, runtime.freeMemory(), runtime.maxMemory()));
        int[] batch = makeBatch(indices, t, batchSize);
        int inputId = (epoch*numBatches) + t;
        ProcessorInput input = makeInput(batch, inputId, currentWts);
        wrapper.put(input);
        logger.info("Threadpool.status: " + wrapper.toString());
        updateId = update(currentWts, updateId, wrapper, updater, nbestLists, false);
        
        if((t+1) % weightWriteOutInterval == 0) {
        	IOTools.writeWeights(String.format("%s.%d.%d.binwts", outputWeightPrefix, epoch, t), currentWts);
        }
      }

      // Wait for threadpool shutdown for this epoch and get final gradients
      wrapper.join();
      
      if(OnlineTuner.printDecodeTime>0) { tuneTimer.end("Ends decoding for epoch " + epoch); }
      
      updateId = update(currentWts, updateId, wrapper, updater, nbestLists, true);
      
      // Compute (averaged) intermediate weights for next epoch, and write to file.
      if (doParameterAveraging) {
        currentWts = new ClassicCounter<String>(wtsAccumulator);
        Counters.divideInPlace(currentWts, (epoch+1)*numBatches);
      }
      
      // Write the intermediate weights for this epoch
      IOTools.writeWeights(String.format("%s.%d.binwts", outputWeightPrefix, epoch), currentWts);

      // Debug info for this epoch
      long elapsedTime = System.nanoTime() - startTime;
      logger.info(String.format("Epoch %d elapsed time: %.2f seconds", epoch, (double) elapsedTime / 1e9));
      double expectedBleu = 0.0;
      if (doExpectedBleu) {
        expectedBleu = approximateBLEUObjective(nbestLists, epoch);
      }
      // Purge history if we're not picking the best weight vector
      if ( ! returnBestDev) epochWeights.clear();
      epochWeights.add(new Triple<Double,Integer,Counter<String>>(expectedBleu, epoch, new ClassicCounter<String>(currentWts)));
    }
    
    saveFinalWeights(epochWeights);
  }

  /**
   * Update the pseudo-references for this training epoch.
   * 
   * @param epoch
   */
  private void updatePseudoReferences(int epoch) {    
    // Compute the pseudo reference set
    if (nbestListWriter != null) {
      nbestListWriter.close();
      EvaluationMetric<IString, String> metric = new BLEUMetric<IString, String>(references);
      MultiTranslationMetricMax<IString, String> searchAlgorithm = new HillClimbingMultiTranslationMetricMax<IString, String>(
          metric);

      NBestListContainer<IString, String> nbestLists = null;
      try {
        nbestLists = new FlatNBestList(nbestFilename, references.size());
      } catch (IOException e) {
        e.printStackTrace();
        logger.severe("Could not load pseudo references from:" + nbestFilename);
        throw new RuntimeException("Could not load pseudo references from:" + nbestFilename);
      }
      List<ScoredFeaturizedTranslation<IString, String>> maxFeaturizedTranslations = searchAlgorithm
          .maximize(nbestLists);

      assert maxFeaturizedTranslations.size() == tuneSource.size() : "Pseudo reference set does not match tuning set";
      int numTranslations = maxFeaturizedTranslations.size();
      for (int i = 0; i < numTranslations; ++i) {
        Sequence<IString> translation = maxFeaturizedTranslations.get(i).translation;
        if(pseudoReferences.get(i).size() >= numPseudoReferences) pseudoReferences.get(i).remove(0);
        pseudoReferences.get(i).add(translation);
      }
      logger.info("Number of pseudo references: " + String.valueOf(pseudoReferences.get(0).size()));
      // Cleanup...these n-best files can get huge.
      File file = new File(nbestFilename);
      file.delete();
      
      // Setup the reference weights. Downweight the pseudo references
      referenceWeights = new double[numReferences+pseudoReferences.get(0).size()];
      Arrays.fill(referenceWeights, 1.0);
      //      Arrays.fill(referenceWeights, 0.5);
//      for (int i = 0; i < numReferences; ++i) referenceWeights[i] = 1.0;
    }
    
    // Setup the next n-best writer
    if (epoch >= pseudoReferenceBurnIn) {
      nbestFilename = String.format("%s/online-nbest.%d.nbest", tempDirectory,
          epoch);
      logger.info("Writing nbest lists to: " + nbestFilename);
      nbestListWriter = IOTools.getWriterFromFile(nbestFilename);
    }
  }

  /**
   * Sort the list of 1-best translations and score with BLEU.
   * 
   * @param nbestLists
   * @param epoch
   * @return
   */
  private double approximateBLEUObjective(Map<Integer, Sequence<IString>> nbestLists, int epoch) {
    assert nbestLists.keySet().size() == references.size();

    BLEUMetric<IString, String> bleu = new BLEUMetric<IString, String>(references, false);
    BLEUMetric<IString, String>.BLEUIncrementalMetric incMetric = bleu
        .getIncrementalMetric();
    Map<Integer, Sequence<IString>> sortedMap = 
        new TreeMap<Integer, Sequence<IString>>(nbestLists);
    for (Map.Entry<Integer, Sequence<IString>> entry : sortedMap.entrySet()) {
      incMetric.add(new ScoredFeaturizedTranslation<IString,String>(entry.getValue(), null, 0.0));
    }
    double expectedBleu = incMetric.score() * 100.0;
    logger.info(String.format("Epoch %d expected BLEU: %.2f / BP: %.3f", epoch, expectedBleu, incMetric.brevityPenalty()));
    return expectedBleu;
  }

  /**
   * Make a batch from an array of indices.
   * 
   * @param indices
   * @param t
   * @param batchSize
   * @return
   */
  private int[] makeBatch(int[] indices, int t, int batchSize) {
    final int start = t*batchSize;
    assert start < indices.length;
    final int end = Math.min((t+1)*batchSize, indices.length);
    int[] batch = new int[end - start];
    System.arraycopy(indices, start, batch, 0, batch.length);
    return batch;
  }

  /**
   * Make a ProcessorInput object for the thread pool from this mini batch.
   * 
   * @param batch
   * @param featureWhitelist 
   * @param randomizeWeights 
   * @param epoch 
   * @return
   */
  private ProcessorInput makeInput(int[] batch, int inputId, Counter<String> weights) {
    List<Sequence<IString>> sourceList = new ArrayList<Sequence<IString>>(batch.length);
    List<List<Sequence<IString>>> referenceList = new ArrayList<List<Sequence<IString>>>(batch.length);
    for (int sourceId : batch) {
      sourceList.add(tuneSource.get(sourceId));
      if (createPseudoReferences && pseudoReferences != null && pseudoReferences.get(0).size() > 0) {
        List<Sequence<IString>> combinedRefs = new ArrayList<Sequence<IString>>(references.get(sourceId));
        combinedRefs.addAll(pseudoReferences.get(sourceId));
        referenceList.add(combinedRefs);
      } else {
        referenceList.add(references.get(sourceId));
      }
    }
    return new ProcessorInput(sourceList, referenceList, weights, batch, inputId);
  }

  /**
   * Load multiple references for accurate expected BLEU evaluation during
   * tuning. Computing BLEU with a single reference is really unstable.
   * 
   * NOTE: This method re-initializes OnlineTuner.references
   * 
   * @param refStr a comma-separated list of reference filenames
   */
  public void loadReferences(String refStr) {
    if (refStr == null || refStr.length() == 0) {
      throw new IllegalArgumentException("Invalid reference list");
    }
    
    try {
      String[] filenames = refStr.split(",");
      references = Metrics.readReferences(filenames);
      assert references.get(0).size() == filenames.length;
      numReferences = filenames.length;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    assert references.size() == tuneSource.size();
    logger.info("Number of references for expected BLEU calculation: " + numReferences);
  }

  /**
   * Configure weights stored on file.
   * 
   * @param wtsInitialFile
   * @param uniformStartWeights
   * @param randomizeStartWeights 
   * @return
   */
  private static Counter<String> loadWeights(String wtsInitialFile,
      boolean uniformStartWeights, boolean randomizeStartWeights) {

    Counter<String> weights;
    try {
      weights = IOTools.readWeights(wtsInitialFile);
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not load weight vector!");
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      throw new RuntimeException("Could not load weight vector!");
    }
    if (uniformStartWeights) {
      // Initialize according to Moses heuristic
      Set<String> featureNames = Generics.newHashSet(weights.keySet());
      featureNames.addAll(FeatureUtils.BASELINE_DENSE_FEATURES);
      for (String key : featureNames) {
        if (key.startsWith("LM")) {
          weights.setCount(key, 0.5);
        } else if (key.startsWith("WordPenalty")) {
          weights.setCount(key, -1.0);
        } else {
          weights.setCount(key, 0.2);
        }
      }
    }
    if (randomizeStartWeights) {
      // Add some random noise
      double scale = 1e-4;
      OptimizerUtils.randomizeWeightsInPlace(weights, scale);
    }
    return weights;
  }

  /**
   * Configure the tuner for the specific tuning algorithm. Return the optimizer object.
   * 
   * @param optimizerAlg
   * @param optimizerFlags
   * @return
   */
  private OnlineOptimizer<IString, String> configureOptimizer(String optimizerAlg, String[] optimizerFlags) {
    assert optimizerAlg != null;

    if (optimizerAlg.equals("mira-1best")) {
      return new MIRA1BestHopeFearOptimizer(optimizerFlags);

    } else if (optimizerAlg.equals("pro-sgd")) {
      assert wtsAccumulator != null : "You must load the initial weights before loading PairwiseRankingOptimizerSGD";
      assert tuneSource != null : "You must load the tuning set before loading PairwiseRankingOptimizerSGD";
      Counters.normalize(wtsAccumulator);
      return new PairwiseRankingOptimizerSGD(tuneSource.size(), expectedNumFeatures, optimizerFlags);

    } else if (optimizerAlg.equals("expectedBLEU")) {
       assert wtsAccumulator != null : "You must load the initial weights before loading expected BLEU";
       assert tuneSource != null : "You must load the tuning set before loading expected BLEU";
       Counters.normalize(wtsAccumulator);
       return new ExpectedBLEUOptimizer(tuneSource.size(), expectedNumFeatures, optimizerFlags);
     
    } else if (optimizerAlg.equals("expectedBLEU2")) {
      assert wtsAccumulator != null : "You must load the initial weights before loading expected BLEU";
      assert tuneSource != null : "You must load the tuning set before loading expected BLEU";
      Counters.normalize(wtsAccumulator);
      return new ExpectedBLEUOptimizer2(tuneSource.size(), expectedNumFeatures, optimizerFlags);
    
    } else if (optimizerAlg.equals("crossentropy")) {
      assert wtsAccumulator != null : "You must load the initial weights before loading cross entropy optimizer";
      assert tuneSource != null : "You must load the tuning set before loading cross entropy optimizer";
      Counters.normalize(wtsAccumulator);
      return new CrossEntropyOptimizer(tuneSource.size(), expectedNumFeatures, optimizerFlags);
    
    } else {
      throw new IllegalArgumentException("Unsupported optimizer: " + optimizerAlg);
    }
  }



  /**
   * Select the final weights from epochResults and save to file.
   * 
   * @param epochResults 
   */
  private void saveFinalWeights(List<Triple<Double, Integer, Counter<String>>> epochResults) {
    if (returnBestDev) {
      // Maximize BLEU (training objective)
      Collections.sort(epochResults);
    } 
    Triple<Double, Integer, Counter<String>> selectedEpoch = epochResults.get(epochResults.size()-1);
    Counter<String> finalWeights = selectedEpoch.third();
    String filename = outputWeightPrefix + ".final.binwts";
    IOTools.writeWeights(filename, finalWeights);
    logger.info("Wrote final weights to " + filename);
    logger.info(String.format("Final weights from epoch %d: BLEU: %.2f", selectedEpoch.second(), selectedEpoch.first()));
    logger.info(String.format("Non-zero final weights: %d", finalWeights.keySet().size()));
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
    optionMap.put("uw", 0);
    optionMap.put("rw", 0);
    optionMap.put("e", 1);
    optionMap.put("o", 1);
    optionMap.put("of", 1);
    optionMap.put("m", 1);
    optionMap.put("mf", 1);
    optionMap.put("n", 1);
    optionMap.put("r", 1);
    optionMap.put("bw", 0);
    optionMap.put("a", 0);
    optionMap.put("b", 1);
    optionMap.put("l", 1);
    optionMap.put("ne", 0);
    optionMap.put("ef", 1);
    optionMap.put("wi", 1);
    optionMap.put("fmc", 1);
    optionMap.put("tmp", 1);
    optionMap.put("p", 1);
    
    // Thang Mar14
    optionMap.put("pdt", 1); // printDecodeTime
    return optionMap;
  }

  /**
   * Usage string for the main method.
   * 
   * @return
   */
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(OnlineTuner.class.getName())
      .append(" [OPTIONS] source_file target_file phrasal_ini initial_weights").append(nl).append(nl)
      .append("Options:").append(nl)
      .append("   -uw        : Uniform weight initialization").append(nl)
      .append("   -rw        : Randomize starting weights at the start of each epoch").append(nl)
      .append("   -e num     : Number of online epochs").append(nl)
      .append("   -o str     : Optimizer: [pro-sgd,mira-1best]").append(nl)
      .append("   -of str    : Optimizer flags (format: CSV list)").append(nl)
      .append("   -m str     : Gold scoring metric for the tuning algorithm (default: bleu-smooth)").append(nl)
      .append("   -mf str    : Gold scoring metric flags (format: CSV list)").append(nl)
      .append("   -n str     : Experiment name").append(nl)
      .append("   -r str     : Use multiple references (format: CSV list)").append(nl)
      .append("   -bw        : Set final weights to the best training epoch.").append(nl)
      .append("   -a         : Enable Collins-style parameter averaging between epochs").append(nl)
      .append("   -b num     : Mini-batch size (optimizer must support mini-batch learning").append(nl)
      .append("   -l level   : Set java.logging level").append(nl)
      .append("   -ne        : Disable expected BLEU calculation (saves memory)").append(nl)
      .append("   -ef        : Expected # of features").append(nl)
      .append("   -wi        : # of minibatches between intermediate weight file writeouts within an epoch").append(nl)
      .append("   -fmc num   : Minimum number of times a feature must appear (default: 0)").append(nl)
      .append("   -tmp path  : Temp directory (default: /tmp)").append(nl)
      .append("   -p str     : Compute pseudo references with parameters <#refs,burn-in> (format: CSV list)");
    
    return sb.toString();
  }

  /**
   * Online optimization for machine translation.
   * 
   * @param args
   */
  public static void main(String[] args) {
    // Parse command-line parameters
    Properties opts = StringUtils.argsToProperties(args, optionArgDefs());
    int numEpochs = PropertiesUtils.getInt(opts, "e", 1);
    String optimizerAlg = opts.getProperty("o", "mira-1best");
    String[] optimizerFlags = opts.containsKey("of") ? opts.getProperty("of").split(",") : null;
    String scoreMetricStr = opts.getProperty("m", "bleu-smooth");
    String[] scoreMetricOpts = opts.containsKey("mf") ? opts.getProperty("mf").split(",") : null;
    String experimentName = opts.getProperty("n", "debug");
    boolean uniformStartWeights = PropertiesUtils.getBool(opts, "uw");
    String refStr = opts.getProperty("r", null);
    boolean finalWeightsFromBestEpoch = PropertiesUtils.getBool(opts, "bw", false);
    boolean doParameterAveraging = PropertiesUtils.getBool(opts, "a", false);
    int batchSize = PropertiesUtils.getInt(opts, "b", 1);
    boolean randomizeStartingWeights = PropertiesUtils.getBool(opts, "rw", false);
    SystemLogger.setLevel(LogName.ONLINE, Level.parse(opts.getProperty("l", "INFO")));
    boolean doExpectedBleu = ! PropertiesUtils.getBool(opts, "ne", false);
    int expectedNumFeatures = PropertiesUtils.getInt(opts, "ef", 30);
    int weightWriteOutInterval = PropertiesUtils.getInt(opts, "wi", 10000/batchSize);
    int minFeatureCount = PropertiesUtils.getInt(opts, "fmc", 0);
    String tmpPath = opts.getProperty("tmp", "/tmp");
    String pseudoRefOptions = opts.getProperty("p", null);
    OnlineTuner.printDecodeTime = PropertiesUtils.getInt(opts, "pdt", 0);
   
    // Parse arguments
    String[] parsedArgs = opts.getProperty("","").split("\\s+");
    if (parsedArgs.length != 4) {
      System.err.println(usage());
      System.exit(-1);
    }
    String srcFile = parsedArgs[0];
    String tgtFile = parsedArgs[1];
    String phrasalIniFile = parsedArgs[2];
    String wtsInitialFile = parsedArgs[3];

    final long startTime = System.nanoTime();
    SystemLogger.setPrefix(experimentName);
    SystemLogger.disableConsoleLogger();
    System.out.println("Phrasal Online Tuner");
    System.out.printf("Startup: %s%n", new Date());
    System.out.println("====================");
    for (Entry<String, String> option : PropertiesUtils.getSortedEntries(opts)) {
      System.out.printf(" %s\t%s%n", option.getKey(), option.getValue());
    }
    System.out.println("====================");
    System.out.println();

    // Run optimization
    final SentenceLevelMetric<IString,String> lossFunction = SentenceLevelMetricFactory.getMetric(scoreMetricStr, scoreMetricOpts);
    OnlineTuner tuner = new OnlineTuner(srcFile, tgtFile, phrasalIniFile, wtsInitialFile, 
        optimizerAlg, optimizerFlags, uniformStartWeights, randomizeStartingWeights,
        expectedNumFeatures);
    if (refStr != null) {
      tuner.loadReferences(refStr);
    }
    if (pseudoRefOptions != null) {
      tuner.computePseudoReferences(pseudoRefOptions, tmpPath);
    }
    tuner.doParameterAveraging(doParameterAveraging);
    tuner.finalWeightsFromBestEpoch(finalWeightsFromBestEpoch);
    tuner.minFeatureCount(minFeatureCount);
    tuner.run(numEpochs, batchSize, lossFunction, doExpectedBleu, weightWriteOutInterval);

    final long elapsedTime = System.nanoTime() - startTime;
    System.out.printf("Elapsed time: %.2f seconds%n", elapsedTime / 1e9);
    System.out.printf("Finished at: %s%n", new Date());
  }
}
