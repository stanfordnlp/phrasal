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

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.BLEUOracleMetric;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.tune.optimizers.MIRAHopeFearOptimizer;
import edu.stanford.nlp.mt.tune.optimizers.OnlineOptimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.OAIndex;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

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
  
  // Log to file
  public static Handler logHandler;
  static {
    try {
      SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-HH-mm-ss");
      String filename = String.format("online-tuner.debug.%s.log", formatter.format(new Date()));
      logHandler = new FileHandler(filename);
      logHandler.setFormatter(new SimpleFormatter()); //Plain text
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  public static void attach(Logger logger) { logger.addHandler(logHandler); }
  
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
  private Index<String> featureIndex;
  
  /**
   * Phrasal decoder instance.
   */
  private Phrasal decoder;
  
  public OnlineTuner(String srcFile, String tgtFile, String phrasalIniFile, String wtsInitialFile) {
    // Write the error log to file
    logger = Logger.getLogger(OnlineTuner.class.getName());
    logger.addHandler(logHandler);

    // Load initial weights
    featureIndex = new OAIndex<String>();
    try {
      wts = IOTools.readWeights(wtsInitialFile, featureIndex);
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
      decoder.getScorer().updateWeights(wts);
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
   * Run an optimizer with a specified objective function.
   * 
   * TODO(spenceg): Implement iterative parameter mixing for multicore
   * 
   * @param objective 
   * @param optimizerAlg
   * @param optimizerFlags 
   * @param nThreads
   */
  private void run(OnlineOptimizer<IString, String> optimizer, 
      SentenceLevelMetric<IString, String> objective, int nThreads) {
    final int tuneSetSize = tuneSource.size();
    Counter<String> decoderWts = new ClassicCounter<String>(wts);
    wts.clear();
    for (int epoch = 0; epoch < NUM_EPOCHS; ++epoch) {
      logger.info("Start of epoch: " + epoch);
      // TODO(spenceg): Randomize order if we run more than one epoch.
      for (int i = 0; i < tuneSetSize; ++i) {
        Sequence<IString> source = tuneSource.get(i);
        Sequence<IString> target = tuneTarget.get(i);
        List<Sequence<IString>> references = new ArrayList<Sequence<IString>>();
        references.add(target);
        
        // Decode source (get n-best list)
        List<RichTranslation<IString,String>> nbestList = decoder.decode(source, i, 0);
        
        // Tune weights
        decoderWts = optimizer.update(source, i, nbestList, references, objective, decoderWts);
        logger.info(String.format("New weights (%d-%d): %s", epoch, i, decoderWts.toString()));
        decoder.getScorer().updateWeights(decoderWts);

        // Accumulate new weights for final averaging
        wts.addAll(decoderWts);
        
        // TODO(spenceg): Extract rules and update phrase table for this example
        // 
      }
    }
    
    // Average final weights
    Counters.divideInPlace(wts, NUM_EPOCHS*tuneSetSize);
    logger.info("Final weights: " + wts.toString());
  }

  
  private static OnlineOptimizer<IString, String> loadOptimizer(String optimizerAlg, String[] optimizerFlags) {
    if (optimizerAlg.equals("mira-1best")) {
      return new MIRAHopeFearOptimizer(optimizerFlags);
    } else if (optimizerAlg.equals("arow")) {
      // TODO
      throw new UnsupportedOperationException();
    }
    throw new UnsupportedOperationException();
  }
  

  private static SentenceLevelMetric<IString, String> loadObjective(
      String objectiveMetric) {
    if (objectiveMetric.equals("bleu-chiang")) {
      return new BLEUOracleMetric<IString,String>();
    }
    throw new UnsupportedOperationException();
  }


  /**
   * Save the final weights file to file.
   * 
   * @param wtsFinalFile
   */
  private void save(String wtsFinalFile) {
    if (!wtsFinalFile.endsWith(".binwts")) {
      wtsFinalFile += ".binwts";
    }
    IOTools.writeWeights(wtsFinalFile, wts);
    logger.info("Wrote final weights to " + wtsFinalFile);
  }
  
  /**
   * Perform any necessary cleanup.
   */
  private void shutdown() {
    logHandler.close();
  }
  
  /**
   * MAIN METHOD STUFF
   */
  
  /**
   * Command-line specification
   * 
   * @return
   */
  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionMap = new HashMap<String,Integer>();
    optionMap.put("s", 1);
    optionMap.put("t", 1);
    optionMap.put("n", 1);
    optionMap.put("e", 1);
    optionMap.put("o", 1);
    optionMap.put("of", 1);
    optionMap.put("m", 1);
    return optionMap;
  }
  
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(OnlineTuner.class.getName()).append(" [OPTIONS] src tgt phrasal_ini wts_initial wts_final ").append(nl);
    sb.append(nl);
    sb.append("Options:").append(nl);
    sb.append("   -s file    : Extrinsic loss: source file").append(nl);
    sb.append("   -t file    : Extrinsic loss: target file").append(nl);
    sb.append("   -n num     : Number of threads for iterative parameter mixing.").append(nl);
    sb.append("   -e num     : Number of online epochs").append(nl);
    sb.append("   -o str     : Optimizer: [arow,mira-1best]").append(nl);
    sb.append("   -of str    : Optimizer flags [comma-separated list]").append(nl);
    sb.append("   -m str     : Evaluation metric (objective) for the tuning algorithm").append(nl);
    return sb.toString().trim();
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    // Parse command-line parameters
    Properties opts = StringUtils.argsToProperties(args, optionArgDefs());
    NUM_EPOCHS = PropertiesUtils.getInt(opts, "e", NUM_EPOCHS);
    String optimizerAlg = opts.getProperty("o", "mira-1best");
    String[] optimizerFlags = opts.containsKey("of") ? opts.getProperty("of").split(",") : null;
    String objectiveMetric = opts.getProperty("m", "bleu-chiang");
    String altSourceFile = opts.getProperty("s");
    String altTargetFile = opts.getProperty("t");
    int nThreads = PropertiesUtils.getInt(opts, "n", 1);
    String[] parsedArgs = opts.getProperty("").split("\\s+");
    if (parsedArgs.length != 5) {
      System.err.println(usage());
      System.exit(-1);
    }
    String srcFile = parsedArgs[0];
    String tgtFile = parsedArgs[1];
    String phrasalIniFile = parsedArgs[2];
    String wtsInitialFile = parsedArgs[3];
    String wtsFinalFile = parsedArgs[4];
   
    System.out.println("Phrasal Online Tuner");
    Date now = new Date();
    System.out.printf("Startup: %s%n", now);
    System.out.println("====================");
    for (Entry<String, String> option : PropertiesUtils.getSortedEntries(opts)) {
      System.out.printf(" %s\t%s%n", option.getKey(), option.getValue());
    }
    System.out.println("====================");
    System.out.println();
    
    final OnlineOptimizer<IString,String> optimizer = loadOptimizer(optimizerAlg, optimizerFlags);
    final SentenceLevelMetric<IString,String> objective = loadObjective(objectiveMetric);
    OnlineTuner tuner = new OnlineTuner(srcFile, tgtFile, phrasalIniFile, wtsInitialFile);
    if (altSourceFile != null && altTargetFile != null) {
      tuner.sampleExtrinsicLossFrom(altSourceFile, altTargetFile);
    }
    tuner.run(optimizer, objective, nThreads);
    tuner.save(wtsFinalFile);
    tuner.shutdown();
    
    now = new Date();
    System.err.printf("Finished at: %s%n", now);
  }
}
