package edu.stanford.nlp.mt.tune;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.base.NGramLanguageModelFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.WordPenaltyFeaturizer;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.metrics.CorpusLevelMetricFactory;
import edu.stanford.nlp.mt.metrics.EvaluationMetric;
import edu.stanford.nlp.mt.metrics.IncrementalEvaluationMetric;
import edu.stanford.nlp.mt.metrics.MetricUtils;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetricFactory;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel.FeatureTemplate;
import edu.stanford.nlp.mt.tm.TranslationModel;
import edu.stanford.nlp.mt.train.DynamicTMBuilder;
import edu.stanford.nlp.mt.tune.OnlineUpdateRule.UpdaterState;
import edu.stanford.nlp.mt.tune.optimizers.CrossEntropyOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.MIRA1BestHopeFearOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.OptimizerUtils;
import edu.stanford.nlp.mt.tune.optimizers.PairwiseRankingOptimizerSGD;
import edu.stanford.nlp.mt.tune.optimizers.ExpectedBLEUOptimizer;
import edu.stanford.nlp.mt.util.EmptySequence;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.FlatNBestList;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IOTools.SerializationMode;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.mt.util.ParallelCorpus;

/**
 * Online model tuning for machine translation as described in
 * Green et al. (2013) (Proc. of ACL).
 * 
 * @author Spence Green
 *
 */
public final class OnlineTuner {
  
  private static final String STATE_FILE_EXTENSION = ".ostate";
  
  // Tuning set
  private List<Sequence<IString>> tuneSource;
  private List<List<Sequence<IString>>> references;
  private int numReferences;

  // Various options
  private boolean returnBestDev = false;
  private boolean doParameterAveraging = false;
  private boolean shuffleDev = true;
  
  private final boolean discardInitialWeightState;
  private final String initialWtsFileName;
  private Counter<String> wtsAccumulator;
  private final int expectedNumFeatures;

  // The optimization algorithm
  private OnlineOptimizer<IString,String> optimizer;

  // Phrasal decoder instance.
  private Phrasal decoder;
  
  private String outputWeightPrefix;
  
  // output single best translation?
  private boolean outputSingleBest = false;
  
  // sequetial optimization? i.e. no stale gradient!
  private boolean enforceStrictlySequential = false;

  // Train a local translation model.
  private boolean localTMTraining;
  private int faDistortionLimit = 15;
  
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
  
  private static final Logger logger = LogManager.getLogger(OnlineTuner.class.getName());
  
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
   * @param wrapBoundary 
   * @param experimentName 
   */
  private OnlineTuner(String srcFile, String tgtFile, String phrasalIniFile, 
      String initialWtsFile, String optimizerAlg, String[] optimizerFlags, 
      boolean uniformStartWeights, boolean randomizeStartWeights, int expectedNumFeatures, 
      boolean wrapBoundary, String experimentName, boolean normalizeInitialWeights) {
    this.outputWeightPrefix = experimentName + ".online";

    // Load Phrasal
    try {
      decoder = Phrasal.loadDecoder(phrasalIniFile);
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
    }
    logger.info("Loaded Phrasal from: {}", phrasalIniFile);

    // Configure the initial weights
    this.expectedNumFeatures = expectedNumFeatures;
    this.initialWtsFileName = initialWtsFile;
    this.discardInitialWeightState = uniformStartWeights || randomizeStartWeights;
    wtsAccumulator = OnlineTuner.loadWeights(initialWtsFile, uniformStartWeights, randomizeStartWeights, 
        decoder.getTranslationModel());
    logger.info("Initial weights: '{}' {}", Counters.toBiggestValuesFirstString(wtsAccumulator, 20), 
        (wtsAccumulator.size() > 20 ? "..." : ""));

    // Load the tuning set
    tuneSource = IStrings.tokenizeFile(srcFile);
    assert tuneSource.size() > 0;
    loadReferences(tgtFile, wrapBoundary);
    logger.info("Intrinsic loss corpus contains {} examples", tuneSource.size());
        
    // Load the optimizer last since some optimizers depend on fields initialized
    // by OnlineTuner.
    optimizer = configureOptimizer(optimizerAlg, optimizerFlags, normalizeInitialWeights);
    logger.info("Loaded optimizer: {}", optimizer);
  }


  private void trainLocalTM(boolean trainLocalTM, int faDistortionLimit) { 
    this.localTMTraining = trainLocalTM;
    this.faDistortionLimit = faDistortionLimit; 
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
    
    logger.info("Creating {} pseudoreferences", numPseudoReferences);
    logger.info("Pseudoreference temp directory: {}", tempDirectory);
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
   * Randomize dev set order.
   * 
   * @param b
   */
  private void shuffleDev(boolean b) { this.shuffleDev = b; }
   
  /**
   * Enforce strictly sequential optimization. No stale gradient!
   * 
   * @param b
   */
  private void enforceStrictlySequential(boolean b) { this.enforceStrictlySequential = b; }
  
  /**
   * Output single best translation?
   * 
   * @param b
   */
  private void outputSingleBest(boolean b) { this.outputSingleBest = b; }
  
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
    public final TranslationModel<IString,String> localTM;
    public final boolean createForcedAlignment;
    public ProcessorInput(List<Sequence<IString>> input, 
        List<List<Sequence<IString>>> references, 
        Counter<String> weights, int[] translationIds, int inputId, 
        TranslationModel<IString,String> localTM, boolean createForcedAlignment) {
      this.source = input;
      this.translationIds = translationIds;
      this.references = references;
      this.inputId = inputId;
      // Copy here for thread safety. DO NOT change this unless you know
      // what you're doing....
      this.weights = new ClassicCounter<String>(weights);
      this.localTM = localTM;
      this.createForcedAlignment = createForcedAlignment;
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
    List<RichTranslation<IString, String>> forcedAlignment;
    public ProcessorOutput(Counter<String> gradient, 
        int inputId, 
        List<List<RichTranslation<IString, String>>> nbestLists, int[] translationIds, List<RichTranslation<IString, String>> forcedAlignment) {
      this.gradient = gradient;
      this.inputId = inputId;
      this.nbestLists = nbestLists;
      this.translationIds = translationIds;
      this.forcedAlignment = forcedAlignment;
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

    // Counter for the newInstance() method
    private int childThreadId;

    public GradientProcessor(OnlineOptimizer<IString, String> optimizer, 
        SentenceLevelMetric<IString, String> scoreMetric, int firstThreadId) {
      this.optimizer = optimizer;
      this.scoreMetric = scoreMetric;
      this.threadId = firstThreadId;
      this.childThreadId = firstThreadId+1;
    }

    @Override
    public ProcessorOutput process(ProcessorInput input) {
      assert input.weights != null;
            
      final int batchSize = input.translationIds.length;
      List<List<RichTranslation<IString,String>>> nbestLists = new ArrayList<>(input.translationIds.length);
      List<RichTranslation<IString,String>> forcedAlignments = input.createForcedAlignment ? 
                                                               new ArrayList<>(input.translationIds.length) : null;
      
      // Decode
      for (int i = 0; i < batchSize; ++i) {
        final int sourceId = input.translationIds[i];
        InputProperties inputProperties;
        if(decoder.getInputProperties().size() > sourceId)
            inputProperties = new InputProperties(decoder.getInputProperties().get(sourceId));
        else
            inputProperties = new InputProperties();
        
        inputProperties.put(InputProperty.DecoderLocalWeights, input.weights);
        if (input.localTM != null) inputProperties.put(InputProperty.DecoderLocalTM, input.localTM);
        
        List<RichTranslation<IString,String>> nbestList;
        if(input.createForcedAlignment) {
          // no forced decoding for optimization
          nbestList = decoder.decode(input.source.get(i), sourceId, 
              threadId, decoder.getNbestListSize(), null, inputProperties);
          
          // now compute forced alignment
          inputProperties.put(InputProperty.TargetPrefix, Boolean.toString(true));
          inputProperties.put(InputProperty.DistortionLimit, faDistortionLimit);
          List<RichTranslation<IString, String>> faNbestList = decoder.decode(input.source.get(i), sourceId, 
              threadId, decoder.getNbestListSize(), input.references.get(i), inputProperties);
          
          forcedAlignments.add(faNbestList.get(0));
        }
        else {
          nbestList = decoder.decode(input.source.get(i), sourceId, 
              threadId, inputProperties);
        }
        nbestLists.add(nbestList);
      }

      // Compute gradient
      Counter<String> gradient;
      if (batchSize == 1) {
        gradient = optimizer.getGradient(input.weights, input.source.get(0), 
            input.translationIds[0], nbestLists.get(0), input.references.get(0), 
            referenceWeights, scoreMetric);
        
      } else {
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
      return new ProcessorOutput(gradient, input.inputId, nbestLists, input.translationIds, forcedAlignments);
    }

    @Override
    public ThreadsafeProcessor<ProcessorInput, ProcessorOutput> newInstance() {
      return new GradientProcessor(optimizer, scoreMetric, childThreadId++);
    }
  }

  /**
   * Asynchronous template from Langford et al. (2009). Get gradients from the threadpool and update the weight vector.
   */
  private int update(Counter<String> currentWts, 
      int updateStep, MulticoreWrapper<ProcessorInput,ProcessorOutput> threadpool, 
      OnlineUpdateRule<String> updater, Map<Integer, Sequence<IString>> nbestLists, 
      boolean endOfEpoch, ParallelCorpus localTmTrainingData) {
    assert threadpool != null;
    assert currentWts != null;
    assert updater != null;
    
    // There may be more than one gradient available, so loop
    while (threadpool.peek()) {
      final ProcessorOutput result = threadpool.poll();
      boolean isEndOfEpoch = endOfEpoch && ! threadpool.peek();

      logger.info("Update {} gradient cardinality: {}", updateStep, result.gradient.keySet().size());
      
      // Update rule. 
      updater.update(currentWts, result.gradient, updateStep, isEndOfEpoch);

      // Debug info
      logger.info("Update {} with gradient from input step {} (diff: {})", 
          updateStep, result.inputId, result.inputId - updateStep);
      logger.info("Update {} approximate L2 ||w'-w|| {}", updateStep, Counters.L2Norm(result.gradient));
      logger.info("Update {} cardinality: {}", updateStep, currentWts.keySet().size());
      ++updateStep;

      // Accumulate intermediate weights for parameter averaging
      if (doParameterAveraging) {
        wtsAccumulator.addAll(currentWts);
      }
      
      // Do something with the n-best lists before dumping them?
      if (nbestLists != null || createPseudoReferences || localTmTrainingData != null) {
        for (int i = 0; i < result.translationIds.length; ++i) {
          int sourceId = result.translationIds[i];
          if (createPseudoReferences && nbestListWriter != null) {
            IOTools.writeNbest(result.nbestLists.get(i), sourceId, "moses", null, nbestListWriter);
          }
          if (nbestLists != null) {
            assert ! nbestLists.containsKey(sourceId);
            // For objective function evaluations, put the one best prediction as opposed to the full n-best list,
            // which consumes too much memory for large tuning sets.
            if (result.nbestLists.get(i).size() > 0) {
              Sequence<IString> bestHypothesis = result.nbestLists.get(i).get(0).translation;
              nbestLists.put(sourceId, bestHypothesis);
            } else {
              nbestLists.put(sourceId, new EmptySequence<IString>());
            }
          }
          if(localTmTrainingData != null && result.forcedAlignment != null) {
            RichTranslation<IString, String> fa = result.forcedAlignment.get(i);
            if (fa != null) {
              localTmTrainingData.add(fa.alignmentGrid().f().toString(), fa.translation.toString(), fa.alignmentString());
            } else {
              logger.error("No forced alignment for input {}", result.inputId);
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
   * 
   * @param numEpochs
   * @param batchSize
   * @param scoreMetric
   * @param corpusLevelMetricStr
   * @param weightWriteOutInterval
   */
  public void run(int numEpochs, int batchSize, SentenceLevelMetric<IString, String> scoreMetric, 
      String corpusLevelMetricStr, int weightWriteOutInterval) {
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
    final UpdaterState initialState = OnlineTuner.loadUpdaterState(initialWtsFileName);
    if (initialState != null && ! discardInitialWeightState) {
      updater.setState(initialState);
      logger.info("Warm restart: loaded updater state for weights file: {}", initialWtsFileName);
    }
    final Runtime runtime = Runtime.getRuntime();

    // Threadpool for decoders. Create one per epoch so that we can wait for all jobs
    // to finish at the end of the epoch
    final MulticoreWrapper<ProcessorInput,ProcessorOutput> wrapper = 
        new MulticoreWrapper<ProcessorInput,ProcessorOutput>(numThreads, 
            new GradientProcessor(optimizer,scoreMetric,0), enforceStrictlySequential);
    
    logger.info("Start of online tuning");
    logger.info("Number of epochs: {}", numEpochs);
    logger.info("Number of threads: {}", numThreads);
    logger.info("Number of references: {}", numReferences);
    int updateId = 0;
    double maxObjectiveValue = Double.NEGATIVE_INFINITY;
    int maxObjectiveEpoch = -1;
    for (int epoch = 0; epoch < numEpochs; ++epoch) {
      final long startTime = TimingUtils.startTime();
      logger.info("Start of epoch: {}", epoch);
      
      // n-best lists. Purge for each epoch
      Map<Integer,Sequence<IString>> nbestLists = new HashMap<>(tuneSetSize);
      if (createPseudoReferences) updatePseudoReferences(epoch);

      // Randomize order of training examples in-place (Langford et al. (2009), p.4)
      if(shuffleDev)
        ArrayMath.shuffle(indices);
      
      logger.info("Number of batches for epoch {}: {}", epoch, numBatches);
      ParallelCorpus corpus = localTMTraining ? new ParallelCorpus() : null;
      for (int t = 0; t < numBatches; ++t) {
        logger.info("Epoch {} batch {} memory free: {}  max: {}", epoch, t, runtime.freeMemory(), 
            runtime.maxMemory());
        int[] batch = makeBatch(indices, t, batchSize);
        int inputId = (epoch*numBatches) + t;
        TranslationModel<IString,String> localTM  = localTMTraining && t > 0 ? getLocalTM(corpus) : null;
        
        ProcessorInput input = makeInput(batch, inputId, currentWts, localTM);
        wrapper.put(input);
        logger.info("Threadpool.status: {}", wrapper);
        if(enforceStrictlySequential)
          wrapper.join(false);
        updateId = update(currentWts, updateId, wrapper, updater, nbestLists, false, corpus);
        
        if((t+1) % weightWriteOutInterval == 0) {
          String filename = String.format("%s.%d.%d%s", outputWeightPrefix, epoch, t, IOTools.WEIGHTS_FILE_EXTENSION);
          IOTools.writeWeights(filename, currentWts);
        }
      }
      
      // Wait for threadpool shutdown for this epoch and get final gradients
      boolean isLastEpoch = epoch+1 == numEpochs;
      wrapper.join(isLastEpoch);
      updateId = update(currentWts, updateId, wrapper, updater, nbestLists, true, corpus);
      
      // Compute (averaged) intermediate weights for next epoch, and write to file.
      if (doParameterAveraging) {
        currentWts = new ClassicCounter<String>(wtsAccumulator);
        Counters.divideInPlace(currentWts, (epoch+1)*numBatches);
      }
      
      // Write the intermediate state for this epoch
      String epochFilePrefix = String.format("%s.%d", outputWeightPrefix, epoch);
      IOTools.writeWeights(epochFilePrefix + IOTools.WEIGHTS_FILE_EXTENSION, currentWts);
      IOTools.serialize( epochFilePrefix + STATE_FILE_EXTENSION, updater.getState(), SerializationMode.BIN_GZ);
      
      if(outputSingleBest) {
        PrintStream ps = IOTools.getWriterFromFile(epochFilePrefix + ".trans");
        IOTools.writeSingleBest(nbestLists, ps);
        ps.close();
      }
      
      // Debug info for this epoch
      double elapsedTime = TimingUtils.elapsedSeconds(startTime);
      logger.info("Epoch {} elapsed time: {} seconds", epoch, elapsedTime);
      double approxObjectiveValue = approximateObjective(nbestLists, epoch, corpusLevelMetricStr);
      if (approxObjectiveValue > maxObjectiveValue) maxObjectiveEpoch = epoch;
    }
    
    saveFinalWeights(currentWts, maxObjectiveEpoch, numEpochs);
  }
  
  private TranslationModel<IString,String> getLocalTM(ParallelCorpus corpus) {
    DynamicTMBuilder tmBuilder = new DynamicTMBuilder(corpus);
    TranslationModel<IString,String> localTM = tmBuilder.build();
    ((DynamicTranslationModel<String>) localTM).initializeLocalTM("localTM", FeatureTemplate.DENSE_EXT);
    
    //IOTools.serialize("localTM.bin", localTM);
    return localTM;
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
        logger.error("Could not load pseudo references from: {}", nbestFilename);
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
      logger.info("Number of pseudo references: {}", String.valueOf(pseudoReferences.get(0).size()));
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
      logger.info("Writing nbest lists to: {}", nbestFilename);
      nbestListWriter = IOTools.getWriterFromFile(nbestFilename);
    }
  }

  /**
   * Sort the list of 1-best translations and score with a corpus-level evaluation metric.
   *
   * @param scoreMetricStr A string specifying the metric to be passed to <code>CorpusLevelMetricFactory</code>.
   */
  private double approximateObjective(Map<Integer, Sequence<IString>> nbestLists, int epoch, String scoreMetricStr) {
    assert nbestLists.keySet().size() == references.size();

    EvaluationMetric<IString,String> metric = CorpusLevelMetricFactory.newMetric(scoreMetricStr, references);
    IncrementalEvaluationMetric<IString,String> incMetric = metric.getIncrementalMetric();
    Map<Integer, Sequence<IString>> sortedMap = 
        new TreeMap<Integer, Sequence<IString>>(nbestLists);
    for (Map.Entry<Integer, Sequence<IString>> entry : sortedMap.entrySet()) {
      incMetric.add(new ScoredFeaturizedTranslation<IString,String>(entry.getValue(), null, 0.0));
    }
    double objectiveValue = incMetric.score() * 100.0;
    logger.info("Epoch {} expected {}: {}", epoch, scoreMetricStr.toUpperCase(), objectiveValue);
    return objectiveValue;
  }

  /**
   * Make a batch from an array of indices.
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
   */
  private ProcessorInput makeInput(int[] batch, int inputId, Counter<String> weights, 
      TranslationModel<IString,String> localTM) {
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
    return new ProcessorInput(sourceList, referenceList, weights, batch, inputId, localTM, localTMTraining);
  }

  /**
   * Load multiple references for accurate objective function evaluation during
   * tuning.
   * 
   * NOTE: This method re-initializes OnlineTuner.references
   * 
   * @param refStr a comma-separated list of reference filenames
   * @param wrapBoundary 
   */
  public void loadReferences(String refStr, boolean wrapBoundary) {
    if (refStr == null || refStr.length() == 0) {
      throw new IllegalArgumentException("Invalid reference list");
    }
    
    try {
      String[] filenames = refStr.split(",");
      System.err.println("reading references: " + refStr);
      references = MetricUtils.readReferences(filenames);
      assert references.get(0).size() == filenames.length;
      numReferences = filenames.length;
      if (wrapBoundary) {
        for (List<Sequence<IString>> refList : references) {
          wrap(refList);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    assert references.size() == tuneSource.size();
    logger.info("Number of references for objective function calculation: {}", numReferences);
  }
  
  /**
   * Wrap all sequences in the input with start and end tokens.
   * 
   * @param sequences
   */
  private static void wrap(List<Sequence<IString>> sequences) {
    for (int i = 0, sz = sequences.size(); i < sz; ++i) {
      sequences.set(i, Sequences.wrapStartEnd(sequences.get(i), TokenUtils.START_TOKEN, TokenUtils.END_TOKEN));
    }
  }

  /**
   * Configure weights stored on file.
   * @param translationModel 
   */
  private static Counter<String> loadWeights(String wtsInitialFile,
      boolean uniformStartWeights, boolean randomizeStartWeights, TranslationModel<IString, String> translationModel) {

    Counter<String> weights = IOTools.readWeights(wtsInitialFile);
    if (weights == null) weights = new ClassicCounter<>();
    if (uniformStartWeights) {
      // Initialize according to Moses heuristic
      Set<String> featureNames = new HashSet<>(weights.keySet());
      featureNames.addAll(FeatureUtils.getBaselineFeatures(translationModel));
      for (String key : featureNames) {
        if (key.startsWith(NGramLanguageModelFeaturizer.DEFAULT_FEATURE_NAME)) {
          weights.setCount(key, 0.5);
        } else if (key.startsWith(WordPenaltyFeaturizer.FEATURE_NAME)) {
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
   * Load the online updater state that accompanies this weight file.
   * 
   * @param wtsInitialFile The name of a weights file.
   * @return An UpdaterState instance or null if the state file does not exist.
   */
  private static UpdaterState loadUpdaterState(String wtsInitialFile) {
    int delim = wtsInitialFile.lastIndexOf('.');
    if (delim < 0) return null;
    String fileName = wtsInitialFile.substring(0, delim) + STATE_FILE_EXTENSION;
    return IOTools.deserialize(fileName, UpdaterState.class, SerializationMode.BIN_GZ);
  }

  /**
   * Configure the tuner for the specific tuning algorithm. Return the optimizer object.
   */
  private OnlineOptimizer<IString, String> configureOptimizer(String optimizerAlg, String[] optimizerFlags, boolean normalizeInitialWeights) {
    assert optimizerAlg != null;

    switch (optimizerAlg) {
      case "mira-1best":
        return new MIRA1BestHopeFearOptimizer(optimizerFlags);

      case "pro-sgd":
        assert wtsAccumulator != null : "You must load the initial weights before loading PairwiseRankingOptimizerSGD";
        assert tuneSource != null : "You must load the tuning set before loading PairwiseRankingOptimizerSGD";
        if(normalizeInitialWeights)
          Counters.normalize(wtsAccumulator);
        return new PairwiseRankingOptimizerSGD(tuneSource.size(), expectedNumFeatures, optimizerFlags);

      case "expectedBLEU":
        assert wtsAccumulator != null : "You must load the initial weights before loading expected BLEU";
        assert tuneSource != null : "You must load the tuning set before loading expected BLEU";
        if(normalizeInitialWeights)
          Counters.normalize(wtsAccumulator);
        return new ExpectedBLEUOptimizer(tuneSource.size(), expectedNumFeatures, optimizerFlags);

      case "crossentropy":
        assert wtsAccumulator != null : "You must load the initial weights before loading cross entropy optimizer";
        assert tuneSource != null : "You must load the tuning set before loading cross entropy optimizer";
        if(normalizeInitialWeights)
          Counters.normalize(wtsAccumulator);
        return new CrossEntropyOptimizer(tuneSource.size(), expectedNumFeatures, optimizerFlags);

      default:
        throw new IllegalArgumentException("Unsupported optimizer: " + optimizerAlg);
    }
  }

  /**
   * Save the final weight vector. This is either the output of the last epoch,
   * or it is the best model according to the objective function.
   * 
   * @param lastWeights
   * @param maxObjectiveEpoch
   * @param numEpochs
   */
  private void saveFinalWeights(Counter<String> lastWeights, int maxObjectiveEpoch, int numEpochs) {
    Counter<String> finalWeights = lastWeights;
    if (returnBestDev && maxObjectiveEpoch >= 0 && ! (maxObjectiveEpoch == numEpochs-1)) {
      String bestWeightsFile = String.format("%s.%d%s", outputWeightPrefix, maxObjectiveEpoch, IOTools.WEIGHTS_FILE_EXTENSION);
      lastWeights = IOTools.readWeights(bestWeightsFile);
    } 
    String finalFilename = String.format("%s.final%s", outputWeightPrefix, IOTools.WEIGHTS_FILE_EXTENSION);
    IOTools.writeWeights(finalFilename, finalWeights);
    logger.info("Final weights from epoch {} to: {}", returnBestDev ? maxObjectiveEpoch : numEpochs-1,
        finalFilename);
    logger.info("Non-zero final weights: {}", finalWeights.keySet().size());
  }

  /********************************************
   * MAIN METHOD STUFF
   ********************************************/

  /**
   * Command-line parameter specification.
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
    optionMap.put("ef", 1);
    optionMap.put("wi", 1);
    optionMap.put("fmc", 1);
    optionMap.put("tmp", 1);
    optionMap.put("p", 1);
    optionMap.put("s", 0);
    optionMap.put("rand", 1);
    optionMap.put("localTM", 0);
    optionMap.put("seq", 0);
    optionMap.put("faDistLimit", 1);    
    optionMap.put("niw", 1);    
    optionMap.put("sb", 0);
    return optionMap;
  }

  /**
   * Usage string for the main method.
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
      .append("   -ef        : Expected # of features").append(nl)
      .append("   -wi        : # of minibatches between intermediate weight file writeouts within an epoch").append(nl)
      .append("   -fmc num   : Minimum number of times a feature must appear (default: 0)").append(nl)
      .append("   -tmp path  : Temp directory (default: /tmp)").append(nl)
      .append("   -p str     : Compute pseudo references with parameters <#refs,burn-in> (format: CSV list)").append(nl)
      .append("   -s         : Wrap references and source inputs in boundary tokens").append(nl)
      .append("   -rand      : Randomize dev set before tuning (default: true)").append(nl)
      .append("   -localTM   : Incrementally train a local translation model on the dev data. (default: false)").append(nl)
      .append("   -seq       : Enforce a strictly sequential optimization - this will make multi-threading pointless. (default: false)").append(nl)
      .append("   -faDistLimit : distortion limit for forced alignment in localTM training (default: 15)").append(nl)
      .append("   -niw       : normalize the initial weights file (default: true)").append(nl)
      .append("   -sb        : Specify for single best output. ");
    
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
    int expectedNumFeatures = PropertiesUtils.getInt(opts, "ef", 30);
    int weightWriteOutInterval = PropertiesUtils.getInt(opts, "wi", 10000/batchSize);
    int minFeatureCount = PropertiesUtils.getInt(opts, "fmc", 0);
    String tmpPath = opts.getProperty("tmp", "/tmp");
    String pseudoRefOptions = opts.getProperty("p", null);
    boolean wrapBoundary = PropertiesUtils.getBool(opts, "s", false);
    boolean shuffleDev = PropertiesUtils.getBool(opts, "rand", true);
    boolean outputSingleBest = PropertiesUtils.getBool(opts, "sb", false);
    boolean trainLocalTM = PropertiesUtils.getBool(opts, "localTM", false);
    int faDistortionLimit = PropertiesUtils.getInt(opts, "faDistLimit", 15);
    boolean enforceStrictlySequential = PropertiesUtils.getBool(opts, "seq", false);
    boolean normalizeInitialWeights = PropertiesUtils.getBool(opts, "niw", true);
    
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
    System.out.println("Phrasal Online Tuner");
    System.out.printf("Startup: %s%n", new Date());
    System.out.println("====================");
    for (Entry<String, String> option : PropertiesUtils.getSortedEntries(opts)) {
      System.out.printf(" %s\t%s%n", option.getKey(), option.getValue());
    }
    System.out.println("====================");
    System.out.println();
      
    // Run optimization
    final SentenceLevelMetric<IString,String> slScoreMetric = SentenceLevelMetricFactory.getMetric(scoreMetricStr, scoreMetricOpts);
    final String clMetricString = SentenceLevelMetricFactory.sentenceLevelToCorpusLevel(scoreMetricStr);
    OnlineTuner tuner = new OnlineTuner(srcFile, tgtFile, phrasalIniFile, wtsInitialFile, 
        optimizerAlg, optimizerFlags, uniformStartWeights, randomizeStartingWeights,
        expectedNumFeatures, wrapBoundary, experimentName, normalizeInitialWeights);
    if (refStr != null) {
      tuner.loadReferences(refStr, wrapBoundary);
    }
    if (pseudoRefOptions != null) {
      tuner.computePseudoReferences(pseudoRefOptions, tmpPath);
    }
    tuner.doParameterAveraging(doParameterAveraging);
    tuner.finalWeightsFromBestEpoch(finalWeightsFromBestEpoch);
    tuner.minFeatureCount(minFeatureCount);
    tuner.shuffleDev(shuffleDev);
    tuner.outputSingleBest(outputSingleBest);
    tuner.enforceStrictlySequential(enforceStrictlySequential);
    tuner.trainLocalTM(trainLocalTM, faDistortionLimit);
    tuner.run(numEpochs, batchSize, slScoreMetric, clMetricString, weightWriteOutInterval);

    final long elapsedTime = System.nanoTime() - startTime;
    System.out.printf("Elapsed time: %.2f seconds%n", elapsedTime / 1e9);
    System.out.printf("Finished at: %s%n", new Date());
  }
}
