package edu.stanford.nlp.mt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.decoder.AbstractBeamInferer;
import edu.stanford.nlp.mt.decoder.AbstractBeamInfererBuilder;
import edu.stanford.nlp.mt.decoder.DTUDecoder;
import edu.stanford.nlp.mt.decoder.Inferer;
import edu.stanford.nlp.mt.decoder.Inferer.NbestMode;
import edu.stanford.nlp.mt.decoder.InfererBuilderFactory;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerFactory;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.HierarchicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.LexicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.h.HeuristicFactory;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilterFactory;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.util.OutputSpace;
import edu.stanford.nlp.mt.decoder.util.OutputSpaceFactory;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.decoder.util.ScorerFactory;
import edu.stanford.nlp.mt.metrics.MetricUtils;
import edu.stanford.nlp.mt.process.Postprocessor;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.ProcessorFactory;
import edu.stanford.nlp.mt.tm.CombinedTranslationModel;
import edu.stanford.nlp.mt.tm.CompiledPhraseTable;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DTUTable;
import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.tm.ExtendedLexicalReorderingTable;
import edu.stanford.nlp.mt.tm.LexicalReorderingTable;
import edu.stanford.nlp.mt.tm.PhraseTable;
import edu.stanford.nlp.mt.tm.TranslationModel;
import edu.stanford.nlp.mt.tm.TranslationModelFactory;
import edu.stanford.nlp.mt.tm.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.FactoryUtil;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.KSR;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SourceClassMap;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.mt.util.WordPredictionAccuracy;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Phrasal: a phrase-based machine translation system from the Stanford
 * University NLP group.
 *
 * NOTE: This object is not threadsafe. To enable programmatic multithreading
 * with Phrasal, specify the number of threads in the *.ini as usual, then use
 * the threadId arguments in the decode() functions to submit to the underlying
 * threadpool. This design permits storage of the LM and phrase table---among
 * other large data structures---in shared memory.
 *
 * @author danielcer
 * @author Michel Galley
 * @author Spence Green
 *
 */
public class Phrasal {

  // TODO(spenceg): Add input encoding option. Replace all instances of "UTF-8"
  // in the codebase.
  private static String usage() {
    final StringBuilder sb = new StringBuilder();
    final String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(Phrasal.class.getName()).append(" OPTS [ini_file] < input > output").append(nl)
        .append(nl).append("Phrasal: A phrase-based machine translation decoder from the Stanford NLP group.")
        .append(nl).append(nl).append("Command-line arguments override arguments specified in the optional ini_file:")
        .append(nl).append("  -").append(INPUT_FILE_OPT).append(" file : Filename of file to decode").append(nl)
        .append(nl).append("  -").append(TRANSLATION_TABLE_OPT)
        .append(
            " filename : Translation model file. Multiple models can be specified by separating filenames with colons.")
        .append(nl).append("  -").append(LANGUAGE_MODEL_OPT)
        .append(" filename : Language model file. For KenLM, prefix filename with 'kenlm:'").append(nl).append("  -")
        .append(OPTION_LIMIT_OPT).append(" num : Translation option limit.").append(nl).append("  -")
        .append(NBEST_LIST_OPT).append(" num : n-best list size.").append(nl).append("  -")
        .append(DISTINCT_NBEST_LIST_OPT).append(" boolean : Generate distinct n-best lists (default: false)").append(nl).append("  -")
        .append("  -").append(FORCE_DECODE).append(" filename [filename] : Force decode to reference file(s).")
        .append(nl).append("  -").append(PREFIX_ALIGN_COMPOUNDS).append(" boolean : Apply heuristic compound word alignmen for prefix decoding? Affects cube pruning decoder only. (default: false) ")
        .append(nl).append("  -").append(BEAM_SIZE).append(" num : Stack/beam size.").append(nl).append("  -")
        .append(SEARCH_ALGORITHM).append(" [cube|multibeam] : Inference algorithm (default:cube)").append(nl)
        .append("  -").append(REORDERING_MODEL)
        .append(
            " type filename [options] : Lexicalized re-ordering model where type is [classic|hierarchical]. Multiple models can be separating filenames with colons.")
        .append(nl).append("  -").append(WEIGHTS_FILE).append(" filename : Load all model weights from file.")
        .append(nl).append("  -").append(MAX_SENTENCE_LENGTH).append(" num : Maximum input sentence length.").append(nl)
        .append("  -").append(MIN_SENTENCE_LENGTH).append(" num : Minimum input sentence length.").append(nl)
        .append("  -").append(DISTORTION_LIMIT)
        .append(" num [cost] : Hard distortion limit and delay cost (default cost: 0.0).").append(nl).append("  -")
        .append(ADDITIONAL_FEATURIZERS).append(" class [class] : List of additional feature functions.").append(nl)
        .append("  -").append(DISABLED_FEATURIZERS).append(" class [class] : List of baseline featurizers to disable.")
        .append(nl).append("  -").append(NUM_THREADS).append(" num : Number of decoding threads (default: 1)")
        .append(nl).append("  -").append(USE_ITG_CONSTRAINTS)
        .append(" boolean : Use ITG constraints for decoding (multibeam search only)").append(nl).append("  -")
        .append(RECOMBINATION_MODE).append(" name : Recombination mode [pharoah,exact,dtu] (default: exact).")
        .append(nl).append("  -").append(DROP_UNKNOWN_WORDS)
        .append(" boolean : Drop unknown source words from the output (default: false)").append(nl).append("  -")
        .append(INDEPENDENT_PHRASE_TABLES)
        .append(
            " filename [filename] : Phrase tables that cannot have associated reordering models. Optionally supports custom per-table prefixes for features (e.g., pref:filename).")
        .append(nl).append("  -").append(ALIGNMENT_OUTPUT_FILE)
        .append(" filename : Output word-word alignments to file for each translation.").append(nl).append("  -")
        .append(PREPROCESSOR_FILTER).append(" language [opts] : Pre-processor to apply to source input.").append(nl)
        .append("  -").append(POSTPROCESSOR_FILTER)
        .append(" language [opts] : Post-processor to apply to target output.").append(nl).append("  -")
        .append(SOURCE_CLASS_MAP)
        .append(" filename : Feature API: Line-delimited source word->class mapping (TSV format).").append(nl)
        .append("  -").append(TARGET_CLASS_MAP)
        .append(" filename : Feature API: Line-delimited target word->class mapping (TSV format).").append(nl)
        .append("  -").append(GAPS_OPT).append(" options : DTU: Enable Galley and Manning (2010) gappy decoding.")
        .append(nl).append("  -").append(MAX_PENDING_PHRASES_OPT)
        .append(" num : DTU: Max number of pending phrases for decoding.").append(nl).append("  -")
        .append(GAPS_IN_FUTURE_COST_OPT).append(" boolean : DTU: Allow gaps in future cost estimate (default: true)")
        .append(nl).append("  -").append(LINEAR_DISTORTION_OPT)
        .append(" type : DTU: linear distortion type (default: standard)").append(nl).append("  -")
        .append(PRINT_MODEL_SCORES).append(" boolean : Output model scores with translations (default: false)")
        .append(nl).append("  -").append(INPUT_PROPERTIES)
        .append(" file : File specifying properties of each source input.").append(nl).append("  -")
        .append(FEATURE_AUGMENTATION).append(" mode : Feature augmentation mode [all|dense|extended].").append(nl)
        .append("  -").append(WRAP_BOUNDARY)
        .append(" boolean : Add boundary tokens around each input sentence (default: false).").append(nl)
        .append("  -").append(KSR_NBEST_SIZE)
        .append(" int : size of n-best list for KSR computation (default: 0, i.e. no KSR computation).").append(nl)
        .append("  -").append(WPA_NBEST_SIZE)
        .append(" int : size of n-best list for word prediction accuracy computation (default: 0, i.e. no WPA computation).").append(nl)
        .append("  -").append(REFERENCE)
        .append(" String : reference file for KSR/WPA computation.").append(nl);
    return sb.toString();
  }
  
  private static final Logger logger = LogManager.getLogger(Phrasal.class);

  public static final String INPUT_FILE_OPT = "text";
  public static final String TRANSLATION_TABLE_OPT = "ttable-file";
  public static final String LANGUAGE_MODEL_OPT = "lmodel-file";
  public static final String OPTION_LIMIT_OPT = "ttable-limit";
  public static final String NBEST_LIST_OPT = "n-best-list";
  public static final String DISTINCT_NBEST_LIST_OPT = "distinct-n-best-list";
  public static final String FORCE_DECODE = "force-decode";
  public static final String PREFIX_ALIGN_COMPOUNDS = "prefix-align-compounds";
  public static final String BEAM_SIZE = "stack";
  public static final String SEARCH_ALGORITHM = "search-algorithm";
  public static final String REORDERING_MODEL = "reordering-model";
  public static final String WEIGHTS_FILE = "weights-file";
  public static final String MAX_SENTENCE_LENGTH = "max-sentence-length";
  public static final String MIN_SENTENCE_LENGTH = "min-sentence-length";
  public static final String DISTORTION_LIMIT = "distortion-limit";
  public static final String ADDITIONAL_FEATURIZERS = "additional-featurizers";
  public static final String DISABLED_FEATURIZERS = "disabled-featurizers";
  public static final String NUM_THREADS = "threads";
  public static final String USE_ITG_CONSTRAINTS = "use-itg-constraints";
  public static final String RECOMBINATION_MODE = "recombination-mode";
  public static final String GAPS_OPT = "gaps";
  public static final String MAX_PENDING_PHRASES_OPT = "max-pending-phrases";
  public static final String GAPS_IN_FUTURE_COST_OPT = "gaps-in-future-cost";
  public static final String LINEAR_DISTORTION_OPT = "linear-distortion-options";
  public static final String DROP_UNKNOWN_WORDS = "drop-unknown-words";
  public static final String INDEPENDENT_PHRASE_TABLES = "independent-phrase-tables";
  public static final String ALIGNMENT_OUTPUT_FILE = "alignment-output-file";
  public static final String PREPROCESSOR_FILTER = "preprocessor-filter";
  public static final String POSTPROCESSOR_FILTER = "postprocessor-filter";
  public static final String SOURCE_CLASS_MAP = "source-class-map";
  public static final String TARGET_CLASS_MAP = "target-class-map";
  public static final String PRINT_MODEL_SCORES = "print-model-scores";
  public static final String INPUT_PROPERTIES = "input-properties";
  public static final String FEATURE_AUGMENTATION = "feature-augmentation";
  public static final String WRAP_BOUNDARY = "wrap-boundary";
  public static final String KSR_NBEST_SIZE = "ksr_nbest_size";
  public static final String WPA_NBEST_SIZE = "wpa_nbest_size";
  public static final String ORACLE_NBEST_SIZE = "oracle_nbest_size";
  public static final String REFERENCE = "reference";

  private static final Set<String> REQUIRED_FIELDS = new HashSet<>();
  private static final Set<String> OPTIONAL_FIELDS = new HashSet<>();
  private static final Set<String> ALL_RECOGNIZED_FIELDS = new HashSet<>();

  static {
    REQUIRED_FIELDS.add(TRANSLATION_TABLE_OPT);
    OPTIONAL_FIELDS.addAll(Arrays.asList(INPUT_FILE_OPT,WEIGHTS_FILE, REORDERING_MODEL, DISTORTION_LIMIT, ADDITIONAL_FEATURIZERS,
        DISABLED_FEATURIZERS, OPTION_LIMIT_OPT, NBEST_LIST_OPT, DISTINCT_NBEST_LIST_OPT, 
        FORCE_DECODE, PREFIX_ALIGN_COMPOUNDS, RECOMBINATION_MODE, SEARCH_ALGORITHM, BEAM_SIZE, WEIGHTS_FILE, MAX_SENTENCE_LENGTH, MIN_SENTENCE_LENGTH,
        USE_ITG_CONSTRAINTS, NUM_THREADS, GAPS_OPT, GAPS_IN_FUTURE_COST_OPT, LINEAR_DISTORTION_OPT,
        MAX_PENDING_PHRASES_OPT, DROP_UNKNOWN_WORDS, INDEPENDENT_PHRASE_TABLES, LANGUAGE_MODEL_OPT,
        ALIGNMENT_OUTPUT_FILE, PREPROCESSOR_FILTER, POSTPROCESSOR_FILTER, SOURCE_CLASS_MAP, TARGET_CLASS_MAP,
        PRINT_MODEL_SCORES, INPUT_PROPERTIES, FEATURE_AUGMENTATION, WRAP_BOUNDARY, KSR_NBEST_SIZE, WPA_NBEST_SIZE, ORACLE_NBEST_SIZE, REFERENCE));
    ALL_RECOGNIZED_FIELDS.addAll(REQUIRED_FIELDS);
    ALL_RECOGNIZED_FIELDS.addAll(OPTIONAL_FIELDS);
  }

  /**
   * Translation model names for the two types of TMs that can be loaded.
   */
  public static final String TM_BACKGROUND_NAME = "background-tm";
  public static final String TM_FOREGROUND_NAME = "foreground-tm";
  
  public static final int MAX_NBEST_SIZE = 1000;

  /**
   * Number of decoding threads. Setting this parameter to 0 enables
   * multithreading inside the main decoding loop. Generally, it is better to
   * set the desired number of threads here (i.e., set this parameter >= 1).
   */
  private int numThreads = 1;

  /**
   * Hard distortion limit for phrase-based decoder
   */
  private int distortionLimit = 5;

  /**
   * Maximum phrase table query size per span.
   */
  private int ruleQueryLimit = 20;

  /**
   * Global model loaded at startup.
   */
  private Counter<String> globalModel;

  /**
   * DTU options
   *
   * TODO(spenceg): Remove static members. The Phrasal object itself is not
   * threadsafe.
   */
  private static List<String> gapOpts = null;
  public static boolean withGaps = false;

  /**
   * Inference objects, one per thread
   */
  private final List<Inferer<IString, String>> inferers;

  /**
   * Holds the model weights, one per inferer. The model weights have a shared
   * feature index.
   */
  private final List<Scorer<String>> scorers;

  /**
   * The feature extractor.
   */
  private FeatureExtractor<IString, String> featurizer;

  /**
   * Phrase table / translation model
   */
  private final TranslationModel<IString, String> translationModel;

  /**
   * Whether to filter unknown words in the output
   */
  private boolean dropUnknownWords = false;

  /**
   * @return true if unknown words are dropped, and false otherwise.
   */
  public boolean isDropUnknownWords() {
    return dropUnknownWords;
  }

  /**
   * n-best list options
   */
  private String nbestListOutputType = "moses";
  private Pattern nBestListFeaturePattern = null;
  private PrintStream nbestListWriter;
  private int nbestListSize;
  private boolean distinctNbest = false;
  private NbestMode nbestMode = NbestMode.Standard;

  /**
   * Internal alignment options
   */
  private PrintStream alignmentWriter;

  /**
   * References for force decoding
   */
  private List<List<Sequence<IString>>> forceDecodeReferences;

  /**
   * Hard limits on inputs to be decoded
   */
  private int maxSentenceSize = Integer.MAX_VALUE;
  private int minSentenceSize = 0;

  /**
   * Output model scores to console.
   */
  private boolean printModelScores = false;

  /**
   * Properties of each input when Phrasal is run on a finite input file.
   */
  private final List<InputProperties> inputPropertiesList;

  /**
   * Recombination configuration.
   */
  private String recombinationMode = RecombinationFilterFactory.EXACT_RECOMBINATION;

  /**
   * Add boundary tokens flag.
   */
  private final boolean wrapBoundary;
  
  /**
   * For simulating KSR and word prediction accuracy
   */
  private final int ksr_nbest_size;
  private final int wpa_nbest_size;
  private final int oracle_nbest_size;
  private final String references;

  /**
   * Pre/post processing filters.
   */
  private Preprocessor preprocessor;
  private Postprocessor postprocessor;

  public Preprocessor getPreprocessor() {
    return preprocessor;
  }

  public Postprocessor getPostprocessor() {
    return postprocessor;
  }

  /**
   * Set the global model used by Phrasal.
   *
   * @param m
   */
  public void setModel(Counter<String> m) {
    this.globalModel = m;
  }

  /**
   * Return the global Phrasal model.
   *
   * @return
   */
  public Counter<String> getModel() {
    return this.globalModel;
  }

  /**
   * @return the number of threads specified in the ini file.
   */
  public int getNumThreads() {
    return numThreads;
  }

  /**
   * Access the decoder's phrase table.
   *
   * @return
   */
  public TranslationModel<IString, String> getTranslationModel() {
    return translationModel;
  }

  /**
   * Return the input properties loaded with the ini file.
   *
   * @return
   */
  public List<InputProperties> getInputProperties() {
    return Collections.unmodifiableList(inputPropertiesList);
  }

  /**
   * Return the nbest list size specified in the ini file.
   *
   * @return
   */
  public int getNbestListSize() {
    return nbestListSize;
  }

  /**
   * @return The wrap boundary property specified in the ini file.
   */
  public boolean getWrapBoundary() {
    return wrapBoundary;
  }

  // TODO(spenceg): Remove static members. The Phrasal object itself is not
  // threadsafe.
  public static void initStaticMembers(Map<String, List<String>> config) {
    withGaps = config.containsKey(GAPS_OPT);
    gapOpts = withGaps ? config.get(GAPS_OPT) : null;
    if (config.containsKey(GAPS_IN_FUTURE_COST_OPT))
      DTUDecoder.gapsInFutureCost = Boolean.parseBoolean(config.get(GAPS_IN_FUTURE_COST_OPT).get(0));
    if (config.containsKey(LINEAR_DISTORTION_OPT))
      ConcreteRule.setLinearDistortionType(config.get(LINEAR_DISTORTION_OPT).get(0));
    else if (withGaps)
      ConcreteRule.setLinearDistortionType(ConcreteRule.LinearDistortionType.last_contiguous_segment.name());
  }

  @SuppressWarnings("unchecked")
  public Phrasal(Map<String, List<String>> config)
      throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, SecurityException,
      InvocationTargetException, NoSuchMethodException, ClassNotFoundException {
    // Check for required parameters
    if (!config.keySet().containsAll(REQUIRED_FIELDS)) {
      final Set<String> missingFields = new HashSet<>(REQUIRED_FIELDS);
      missingFields.removeAll(config.keySet());
      logger.fatal("The following required fields are missing: {}", missingFields);
      throw new RuntimeException();
    }
    // Check for unrecognized parameters
    if (!ALL_RECOGNIZED_FIELDS.containsAll(config.keySet())) {
      final Set<String> extraFields = new HashSet<>(config.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_FIELDS);
      logger.warn("The following fields are unrecognized: {}", extraFields);
    }

    numThreads = config.containsKey(NUM_THREADS) ? Integer.parseInt(config.get(NUM_THREADS).get(0)) : 1;
    if (numThreads < 1) {
      logger.fatal("Number of threads must be positive: {}", numThreads);
      throw new RuntimeException();
    }
    logger.info("Number of threads: {}", numThreads);

    if (withGaps) {
      recombinationMode = RecombinationFilterFactory.DTU_RECOMBINATION;
    } else if (config.containsKey(RECOMBINATION_MODE)) {
      recombinationMode = config.get(RECOMBINATION_MODE).get(0);
    }

    if (config.containsKey(PRINT_MODEL_SCORES)) {
      printModelScores = Boolean.valueOf(config.get(PRINT_MODEL_SCORES).get(0));
    }

    if (config.containsKey(INPUT_PROPERTIES)) {
      inputPropertiesList = InputProperties.parse(new File(config.get(INPUT_PROPERTIES).get(0)));
      logger.info("loaded input properties from file " + config.get(INPUT_PROPERTIES).get(0));
    } else
      inputPropertiesList = new ArrayList<InputProperties>(1);

    wrapBoundary = config.containsKey(WRAP_BOUNDARY) ? Boolean.valueOf(config.get(WRAP_BOUNDARY).get(0)) : false;

    // Pre/post processor filters. These may be accessed programmatically, but
    // they
    // are only applied automatically to text read from the console.
    if (config.containsKey(PREPROCESSOR_FILTER)) {
      final List<String> parameters = config.get(PREPROCESSOR_FILTER);
      if (parameters.size() == 0) {
        logger.fatal("Preprocessor configuration requires at least one argument");
        throw new RuntimeException();
      }
      final String language = parameters.get(0);
      final String[] options = parameters.size() > 1 ? parameters.get(1).split("\\s+") : (String[]) null;
      preprocessor = ProcessorFactory.getPreprocessor(language, options);
      logger.info("Preprocessor filter: {}", preprocessor.getClass().getName());
    }
    if (config.containsKey(POSTPROCESSOR_FILTER)) {
      final List<String> parameters = config.get(POSTPROCESSOR_FILTER);
      if (parameters.size() == 0) {
        logger.fatal("Postprocessor configuration requires at least one argument");
        throw new RuntimeException();
      }
      final String language = parameters.get(0);
      final String[] options = parameters.size() > 1 ? parameters.get(1).split("\\s+") : (String[]) null;
      postprocessor = ProcessorFactory.getPostprocessor(language, options);
      logger.info("Postprocessor filter: {}", postprocessor.getClass().getName());
    }

    // Word->class maps
    if (config.containsKey(SOURCE_CLASS_MAP)) {
      final List<String> parameters = config.get(SOURCE_CLASS_MAP);
      if (parameters.size() == 0) {
        logger.fatal("Source class map requires a file argument");
        throw new RuntimeException();
      }
      final SourceClassMap map = SourceClassMap.getInstance();
      for (final String filename : parameters) {
        map.load(filename);
        logger.info("Loaded source class map: {}", filename);
      }
    }
    if (config.containsKey(TARGET_CLASS_MAP)) {
      final List<String> parameters = config.get(TARGET_CLASS_MAP);
      if (parameters.size() == 0) {
        logger.fatal("Target class map requires a file argument");
        throw new RuntimeException();
      }
      final TargetClassMap map = TargetClassMap.getInstance();
      for (final String filename : parameters) {
        map.load(filename);
        logger.info("Loaded target class map: {}", filename);
      }
    }

    final boolean forceDecode = config.containsKey(FORCE_DECODE);
    if (forceDecode) {
      forceDecodeReferences = MetricUtils
          .readReferences(config.get(FORCE_DECODE).stream().toArray(String[]::new));
    }
    
    final boolean prefixAlignCompounds = config.containsKey(PREFIX_ALIGN_COMPOUNDS) ?
        Boolean.parseBoolean(config.get(PREFIX_ALIGN_COMPOUNDS).get(0)) : false;

    // int distortionLimit = -1;
    float distortionCost = 0.0f;
    if (config.containsKey(DISTORTION_LIMIT)) {
      final List<String> opts = config.get(DISTORTION_LIMIT);
      if (opts.size() > 0)
        distortionLimit = Integer.parseInt(opts.get(0));
      if (opts.size() > 1)
        distortionCost = Float.parseFloat(opts.get(1));
    }

    // DTU decoding (Galley and Manning, 2010)
    final FeaturizerFactory.GapType gapT = !withGaps ? FeaturizerFactory.GapType.none
        : ((gapOpts.size() > 1) ? FeaturizerFactory.GapType.both : FeaturizerFactory.GapType.source);
    final String gapType = gapT.name();
    if (withGaps) {
      logger.info("Gap type: {}", gapType);
      final int maxSourcePhraseSpan = Integer.parseInt(gapOpts.get(0));
      DTUTable.setMaxPhraseSpan(maxSourcePhraseSpan);

      final int maxTargetPhraseSpan = (gapOpts.size() > 1) ? Integer.parseInt(gapOpts.get(1)) : -1;
      if (maxTargetPhraseSpan == -1) {
        logger.info("Phrases with target gaps not loaded into memory.");
        DTUTable.maxNumberTargetSegments = 1;
      }
      if (gapT == FeaturizerFactory.GapType.target || gapT == FeaturizerFactory.GapType.both) {
        DTUHypothesis.setMaxTargetPhraseSpan(maxTargetPhraseSpan);
        // AbstractBeamInferer.DISTINCT_SURFACE_TRANSLATIONS = true; // TODO:
        // restore?
      }

      // Support for floating phrases:
      if (config.containsKey(MAX_PENDING_PHRASES_OPT)) {
        final List<String> floatOpts = config.get(MAX_PENDING_PHRASES_OPT);
        if (floatOpts.size() != 1)
          throw new UnsupportedOperationException();
        final int maxPendingPhrases = Integer.parseInt(floatOpts.get(0));
        DTUHypothesis.setMaxPendingPhrases(maxPendingPhrases);
      }
    }

    // Phrase table query size limit
    if (config.containsKey(OPTION_LIMIT_OPT)) {
      ruleQueryLimit = Integer.valueOf(config.get(OPTION_LIMIT_OPT).get(0));
    }
    logger.info("Phrase table rule query limit: {}", ruleQueryLimit);

    // Translation model setup
    final List<String> tmOptions = config.get(TRANSLATION_TABLE_OPT);
    final String translationModelFile = tmOptions.get(0);
    final String[] factoryOptions = tmOptions.size() > 1 ? tmOptions.get(1).split(",") : new String[0];
    logger.info("Translation model options {}", Arrays.toString(factoryOptions));
    final TranslationModel<IString, String> primaryModel = TranslationModelFactory
        .<String> factory(translationModelFile, factoryOptions);
    primaryModel.setName(TM_BACKGROUND_NAME);
    
    final List<DerivationFeaturizer<IString, String>> lexReorderFeaturizers = new ArrayList<>();
    if (primaryModel instanceof DynamicTranslationModel) {
      // we can NOT use a CombinedTranslationModel here due to "instanceof DynamicTranslationModel" used for ruleGrid augmentation
      translationModel = primaryModel; //new CombinedTranslationModel<>(primaryModel, ruleQueryLimit);
      logger.info("Translation model mode: dynamic");
      
    } else {
      logger.info("Translation model mode: static");
      final List<TranslationModel<IString, String>> translationModels = new ArrayList<>();
      translationModels.add(primaryModel);

      // Load independent phrase tables that do not have associated lexicalized
      // reordering models
      if (config.get(INDEPENDENT_PHRASE_TABLES) != null) {
        int i = 0;
        for (String filename : config.get(INDEPENDENT_PHRASE_TABLES)) {
          logger.info("Loading independent phrase table: {}", filename);
          final String[] fields = filename.split(":");
          String[] modelOptions = new String[0];
          if (fields.length == 2) {
            filename = fields[0];
            modelOptions = new String[] {
                FactoryUtil.makePair(TranslationModelFactory.FEATURE_PREFIX_OPTION, fields[0]) };
          }
          final TranslationModel<IString, String> model = TranslationModelFactory.<String> factory(filename,
              modelOptions);
          model.setName(String.format("%s-%d", TM_BACKGROUND_NAME, i++));
          translationModels.add(model);
        }
      }

      translationModel = new CombinedTranslationModel<>(translationModels, ruleQueryLimit);
      
      // Load a lexicalized reordering model for a compiled phrase table
      if (config.containsKey(REORDERING_MODEL)) {
        final PhraseTable<IString> phraseTable = (PhraseTable<IString>) primaryModel;

        List<String> parameters = config.get(REORDERING_MODEL);
        if (parameters.size() < 3) {
          logger.fatal(REORDERING_MODEL + " parameter requires at least three arguments");
          throw new RuntimeException();
        }
        final String modelType = parameters.get(0);
        final String modelFilename = parameters.get(1);
        final String modelSpecification = parameters.get(2);

        if (modelType.equals("classic")) {
          final LexicalReorderingTable lrt = new LexicalReorderingTable(modelFilename, phraseTable, modelSpecification);
          lexReorderFeaturizers.add(new LexicalReorderingFeaturizer(lrt));

        } else if (modelType.equals("hierarchical")) {
          parameters = parameters.subList(3, parameters.size());
          final ExtendedLexicalReorderingTable mlrt = new ExtendedLexicalReorderingTable(modelFilename, phraseTable,
              modelSpecification);
          lexReorderFeaturizers.add(new HierarchicalReorderingFeaturizer(mlrt, parameters));

        } else {
          logger.fatal("Unsupported reordering model type: " + modelType);
          throw new RuntimeException();
        }
      }
    }

    // Featurizers
    final List<Featurizer<IString, String>> additionalFeaturizers = new ArrayList<>();
    if (config.containsKey(ADDITIONAL_FEATURIZERS)) {
      final List<String> tokens = config.get(ADDITIONAL_FEATURIZERS);
      String featurizerName = null;
      String args = null;
      for (final String token : tokens) {
        Featurizer<IString, String> featurizer = null;
        if (featurizerName == null) {
          if (token.endsWith("()")) {
            final String name = token.replaceFirst("\\(\\)$", "");
            final Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory.loadFeaturizer(name);
            featurizer = featurizerClass.newInstance();
            additionalFeaturizers.add(featurizer);
          } else if (token.contains("(")) {
            if (token.endsWith(")")) {
              featurizerName = token.replaceFirst("\\(.*", "");
              args = token.replaceFirst("^.*\\(", "");
              args = args.substring(0, args.length() - 1);
              args = args.replaceAll("\\s*,\\s*", ",");
              args = args.replaceAll("^\\s+", "");
              args = args.replaceAll("\\s+$", "");
              final String[] argsList = args.split(",");
              logger.info("Additional featurizer: {}. Args: {}", featurizerName, Arrays.toString(argsList));
              final Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory
                  .loadFeaturizer(featurizerName);
              featurizer = featurizerClass.getConstructor(argsList.getClass()).newInstance(new Object[] { argsList });
              additionalFeaturizers.add(featurizer);
              featurizerName = null;
              args = null;
            } else {
              featurizerName = token.replaceFirst("\\(.*", "");
              args = token.replaceFirst(".*\\(", "");
            }
          } else {
            logger.fatal("Error: '(' expected immediately after feature name {}", token);
            logger.fatal("Note that no whitespace between '(' and the associated feature name is allowed");
            throw new RuntimeException();
          }
        } else {
          if (token.endsWith(")")) {
            args += " " + token.substring(0, token.length() - 1);
            args = args.replaceAll("\\s*,\\s*", ",");
            args = args.replaceAll("^\\s+", "");
            args = args.replaceAll("\\s+$", "");
            final String[] argsList = args.split(",");
            logger.info("args: {}", Arrays.toString(argsList));
            final Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory.loadFeaturizer(featurizerName);
            featurizer = featurizerClass.getConstructor(argsList.getClass()).newInstance((Object) argsList);
            additionalFeaturizers.add(featurizer);
            featurizerName = null;
            args = null;
          } else {
            args += " " + token;
          }
        }
      }
      if (featurizerName != null) {
        logger.fatal("Error: no ')' found for featurizer {}", featurizerName);
        throw new RuntimeException();
      }
    }

    // Create feature extractor
    final String lgModel = config.containsKey(LANGUAGE_MODEL_OPT) ? config.get(LANGUAGE_MODEL_OPT).get(0) : null;

    final String featureAugmentationMode = config.containsKey(FEATURE_AUGMENTATION)
        ? config.get(FEATURE_AUGMENTATION).get(0) : null;

    if (lgModel != null) {
      logger.info("Language model: {}", lgModel);
      featurizer = FeaturizerFactory.factory(FeaturizerFactory.MOSES_DENSE_FEATURES, withGaps,
          FactoryUtil.makePair(FeaturizerFactory.GAP_PARAMETER, gapType),
          FactoryUtil.makePair(FeaturizerFactory.ARPA_LM_PARAMETER, lgModel),
          FactoryUtil.makePair(FeaturizerFactory.LINEAR_DISTORTION_COST, String.valueOf(distortionCost)));
    } else {
      featurizer = FeaturizerFactory.factory(FeaturizerFactory.MOSES_DENSE_FEATURES, withGaps,
          FactoryUtil.makePair(FeaturizerFactory.GAP_PARAMETER, gapType),
          FactoryUtil.makePair(FeaturizerFactory.LINEAR_DISTORTION_COST, String.valueOf(distortionCost)));
    }

    if (config.containsKey(DISABLED_FEATURIZERS)) {
      final Set<String> disabledFeaturizers = new HashSet<>(config.get(DISABLED_FEATURIZERS));
      featurizer.deleteFeaturizers(disabledFeaturizers);
    }

    additionalFeaturizers.addAll(lexReorderFeaturizers);

    if (!additionalFeaturizers.isEmpty()) {
      final List<Featurizer<IString, String>> allFeaturizers = new ArrayList<>();
      allFeaturizers.addAll(featurizer.getFeaturizers());
      allFeaturizers.addAll(additionalFeaturizers);
      featurizer = new FeatureExtractor<IString, String>(allFeaturizers);
    }

    if (featureAugmentationMode != null) {
      logger.info("Feature augmentation mode: {}", featureAugmentationMode);
      featurizer.setFeatureAugmentationMode(featureAugmentationMode);
    }

    // Link the final featurizer and the phrase table
    translationModel.setFeaturizer(featurizer);

    // Create Scorer / weight vector
    this.globalModel = new ClassicCounter<String>();

    if (config.containsKey(WEIGHTS_FILE)) {
      logger.info("Weights file: {}", config.get(WEIGHTS_FILE).get(0));
      globalModel = IOTools.readWeights(config.get(WEIGHTS_FILE).get(0));
      if (globalModel == null)
        globalModel = new ClassicCounter<>();
    }

    if (config.containsKey(MAX_SENTENCE_LENGTH)) {
      maxSentenceSize = Integer.parseInt(config.get(MAX_SENTENCE_LENGTH).get(0));
      if (maxSentenceSize == 0)
        maxSentenceSize = Integer.MAX_VALUE;
    }

    if (config.containsKey(MIN_SENTENCE_LENGTH)) {
      minSentenceSize = Integer.parseInt(config.get(MIN_SENTENCE_LENGTH).get(0));
    }

    logger.info("WeightConfig: '{}' {}", Counters.toBiggestValuesFirstString(globalModel, 20),
        (globalModel.size() > 20 ? "..." : ""));

    // Create Recombination Filter
    final RecombinationFilter<Derivation<IString, String>> filter = RecombinationFilterFactory
        .factory(recombinationMode, featurizer.getFeaturizers());

    // Create Search Heuristic
    final RuleFeaturizer<IString, String> isolatedPhraseFeaturizer = featurizer;
    final SearchHeuristic<IString, String> heuristic = HeuristicFactory.factory(isolatedPhraseFeaturizer,
        withGaps ? HeuristicFactory.ISOLATED_DTU_SOURCE_COVERAGE : HeuristicFactory.ISOLATED_PHRASE_SOURCE_COVERAGE);

    // Set the OOV policy
    if (config.containsKey(DROP_UNKNOWN_WORDS)) {
      dropUnknownWords = Boolean.parseBoolean(config.get(DROP_UNKNOWN_WORDS).get(0));
    }
    logger.info("Unknown words policy: {}", dropUnknownWords ? "Drop" : "Keep");
    final TranslationModel<IString, String> oovModel = new UnknownWordPhraseGenerator<IString, String>(
        dropUnknownWords);

    // Create Inferers and scorers
    inferers = new ArrayList<>(numThreads);
    scorers = new ArrayList<>(numThreads);

    String searchAlgorithm = config.containsKey(SEARCH_ALGORITHM) ? config.get(SEARCH_ALGORITHM).get(0).trim()
        : InfererBuilderFactory.DEFAULT_INFERER;
    if (withGaps) {
      searchAlgorithm = InfererBuilderFactory.DTU_DECODER;
    }
    logger.info("Search algorithm: {}", searchAlgorithm);

    final AbstractBeamInfererBuilder<IString, String> infererBuilder = (AbstractBeamInfererBuilder<IString, String>) InfererBuilderFactory
        .factory(searchAlgorithm);

    // Create the decoders, one per thread
    for (int i = 0; i < numThreads; i++) {
      try {
        infererBuilder.setUnknownWordModel(oovModel, dropUnknownWords);
        infererBuilder.setFeaturizer((FeatureExtractor<IString, String>) featurizer.clone());
        infererBuilder.setPhraseGenerator((TranslationModel<IString, String>) translationModel.clone());
        final Scorer<String> scorer = ScorerFactory.factory(ScorerFactory.SPARSE_SCORER, globalModel, null);
        infererBuilder.setScorer(scorer);
        scorers.add(scorer);
        infererBuilder.setSearchHeuristic((SearchHeuristic<IString, String>) heuristic.clone());
        infererBuilder.setRecombinationFilter((RecombinationFilter<Derivation<IString, String>>) filter.clone());

      } catch (final CloneNotSupportedException e) {
        logger.fatal("Could not clone an inferer member", e);
        throw new RuntimeException();
      }

      // Silently ignored by the cube pruning decoder
      // TODO(spenceg) set this explicitly for each search procedure.
      infererBuilder.setBeamType(BeamFactory.BeamType.sloppybeam);

      if (distortionLimit != -1) {
        infererBuilder.setMaxDistortion(distortionLimit);
      }

      if (config.containsKey(USE_ITG_CONSTRAINTS)) {
        infererBuilder.useITGConstraints(Boolean.parseBoolean(config.get(USE_ITG_CONSTRAINTS).get(0)));
      }

      if (config.containsKey(BEAM_SIZE)) {
        final int beamSize = Integer.parseInt(config.get(BEAM_SIZE).get(0));
        infererBuilder.setBeamSize(beamSize);
      }
      inferers.add(infererBuilder.newInferer());
      
      ((AbstractBeamInferer<IString, String>) inferers.get(i)).setPrefixAlignCompounds(prefixAlignCompounds);
    }

    // determine if we need to generate n-best lists
    final List<String> nbestOpt = config.get(NBEST_LIST_OPT);
    if (nbestOpt != null) {
      nbestListSize = Integer.parseInt(nbestOpt.get(0));
      assert nbestListSize >= 0;
      logger.info("n-best list size: {}", nbestListSize);
      
      if (nbestOpt.size() > 1) {
        nbestMode = NbestMode.valueOf(nbestOpt.get(1));
        logger.info("n-best list mode: {}", nbestMode);
      }
      if (nbestOpt.size() > 2) {
        final String nbestListFilename = nbestOpt.get(2);
        nbestListWriter = IOTools.getWriterFromFile(nbestListFilename);
        logger.info("n-best list filename: {}", nbestListFilename);
      }
      if (nbestOpt.size() > 3) {
        nbestListOutputType = nbestOpt.get(3);
        logger.info("n-best list filename: {}", nbestListOutputType);
      }
      if (nbestOpt.size() > 4) {
        nBestListFeaturePattern = Pattern.compile(nbestOpt.get(4));
        logger.info("n-best list feature pattern: {}", nbestOpt.get(4));
      }

    } else {
      nbestListSize = -1;
      nbestListWriter = null;
    }
    if (nbestListSize > MAX_NBEST_SIZE) {
      logger.warn("nbest list size {} exceeds maximum of {}", nbestListSize, MAX_NBEST_SIZE);
      nbestListSize = MAX_NBEST_SIZE;
    }
    
    distinctNbest = config.containsKey(DISTINCT_NBEST_LIST_OPT) ?
        Boolean.parseBoolean(config.get(DISTINCT_NBEST_LIST_OPT).get(0)) : false;
    logger.info("Distinct n-best lists: {}", distinctNbest);

    // Determine if we need to generate an alignment file
    final List<String> alignmentOpt = config.get(ALIGNMENT_OUTPUT_FILE);
    if (alignmentOpt != null && alignmentOpt.size() == 1) {
      alignmentWriter = IOTools.getWriterFromFile(alignmentOpt.get(0));
    }
    
    ksr_nbest_size = config.containsKey(KSR_NBEST_SIZE) ?
        Integer.valueOf(config.get(KSR_NBEST_SIZE).get(0)) : 0;
    wpa_nbest_size = config.containsKey(WPA_NBEST_SIZE) ?
        Integer.valueOf(config.get(WPA_NBEST_SIZE).get(0)) : 0;
    oracle_nbest_size = config.containsKey(ORACLE_NBEST_SIZE) ?
        Integer.valueOf(config.get(ORACLE_NBEST_SIZE).get(0)) : 0;
    references = config.containsKey(REFERENCE) ? config.get(REFERENCE).get(0) : null;
  }

  /**
   * Lightweight container for decoder input.
   *
   * @author Spence Green
   *
   */
  private static class DecoderInput {
    public final Sequence<IString> source;
    public final InputProperties inputProps;
    public final int sourceInputId;
    public final List<Sequence<IString>> targets;
    
    public int ksr_nbest_size = 0;
    public int wpa_nbest_size = 0;
    public int oracle_nbest_size = 0;
    public final Sequence<IString> reference;
    
    public DecoderInput(Sequence<IString> seq, int sourceInputId, List<Sequence<IString>> targets,
        InputProperties inputProps) {
      this(seq, sourceInputId, targets, inputProps, 0, 0, 0, null);
    }

    public DecoderInput(Sequence<IString> seq, int sourceInputId, List<Sequence<IString>> targets,
        InputProperties inputProps, int ksr_nbest_size, int wpa_nbest_size, int oracle_nbest_size, Sequence<IString> reference) {
      this.source = seq;
      this.sourceInputId = sourceInputId;
      this.inputProps = inputProps;
      this.targets = targets;
      this.ksr_nbest_size = ksr_nbest_size;
      this.wpa_nbest_size = wpa_nbest_size;
      this.oracle_nbest_size = oracle_nbest_size;
      this.reference = reference;
    }
    
    
  }

  /**
   * Lightweight container for decoder output.
   *
   * @author Spence Green
   *
   */
  private static class DecoderOutput {
    public final List<RichTranslation<IString, String>> translations;
    public final Sequence<IString> bestTranslation;
    public final int sourceInputId;
    public final int sourceLength;
    public final int ksrTyped;
    public final int ksrTotal;
    public final int wpaCorrect;
    public final int wpaTotal;

    public DecoderOutput(int sourceLength, List<RichTranslation<IString, String>> translations,
        Sequence<IString> bestTranslation, int sourceInputId) {
      this(sourceLength, translations, bestTranslation, sourceInputId, 0, 0, 0, 0);
    }
    
    public DecoderOutput(int sourceLength, List<RichTranslation<IString, String>> translations,
        Sequence<IString> bestTranslation, int sourceInputId, 
        int ksrTyped, int ksrTotal, int wpaCorrect, int wpaTotal) {
      this.sourceLength = sourceLength;
      this.translations = translations;
      this.bestTranslation = bestTranslation;
      this.sourceInputId = sourceInputId;
      this.ksrTyped = ksrTyped;
      this.ksrTotal = ksrTotal;
      this.wpaCorrect = wpaCorrect;
      this.wpaTotal = wpaTotal;
    }
  }

  /**
   * Wrapper class to submit this decoder instance to the thread pool.
   *
   * @author Spence Green
   *
   */
  private class PhrasalProcessor implements ThreadsafeProcessor<DecoderInput, DecoderOutput> {
    private final int infererId;
    private int childInfererId;

    /**
     * Constructor.
     *
     * @param parentInfererId
     *          - the bast infererId for this instance. Calls to newInstance()
     *          will increment from this value.
     */
    public PhrasalProcessor(int parentInfererId) {
      this.infererId = parentInfererId;
      this.childInfererId = parentInfererId + 1;
    }

    @Override
    public DecoderOutput process(DecoderInput input) {
      // Generate n-best list
      final List<RichTranslation<IString, String>> translations = decode(input.source, input.sourceInputId, infererId,
          nbestListSize, input.targets, input.inputProps);

      // Select and process the best translation
      Sequence<IString> bestTranslation = null;
      if (translations.size() > 0) {
        bestTranslation = translations.get(0).translation;
        if (postprocessor != null) {
          try {
            bestTranslation = postprocessor.process(bestTranslation).e();
          } catch (final Exception e) {
            // The postprocessor exploded. Silently ignore and return
            // the unprocessed translation.
            bestTranslation = translations.get(0).translation;
          }
        }
        if (wrapBoundary) {
          bestTranslation = bestTranslation.subsequence(1, bestTranslation.size() - 1);
        }
      }

      int ksrTyped = 0;
      int ksrTotal = 0;
      int wpaCorrect = 0;
      int wpaTotal = 0;
      
      int previousPrefixSize = input.targets != null && input.targets.size() > 0 ? 
          input.targets.get(0).size() : 0;
      if(input.ksr_nbest_size > 0 && input.reference != null) {
        if(previousPrefixSize > 0) {
          System.err.println("ERROR: KSR can not be combined with forced or prefix decoding.");
          System.exit(-1);
        }
        InputProperties ksrProps = new InputProperties(input.inputProps);
        ksrProps.put(InputProperty.TargetPrefix, true);
        
        List<RichTranslation<IString, String>> ksrTranslations = translations;
        KSR ksrResult = null;
        while(true) {
          ksrResult = KSR.getNextPrefix(ksrTranslations, ksr_nbest_size, input.reference, previousPrefixSize);
          ksrTyped += ksrResult.ksrTyped;
          ksrTotal += ksrResult.ksrTotal;  
          if(ksrResult.nextPrefix == null) break;
          
          ksrTranslations = decode(input.source, input.sourceInputId, infererId, nbestListSize, 
              Collections.singletonList(ksrResult.nextPrefix), ksrProps);
          
          previousPrefixSize = ksrResult.nextPrefix.size();         
        }
      }
      
      previousPrefixSize = input.targets != null && input.targets.size() > 0 ?
          input.targets.get(0).size() : 0;
      if(input.wpa_nbest_size > 0 && input.reference != null) {
        if(WordPredictionAccuracy.correctPrediction(translations, wpa_nbest_size, input.reference, previousPrefixSize)) {
          ++wpaCorrect;
        }
        ++wpaTotal;
          
        if(previousPrefixSize == 0 ){ // otherwise we only want to know wpa for the specified prefix
          // check all prefixes
          List<RichTranslation<IString, String>> wpaTranslations;
          InputProperties wpaProps = new InputProperties(input.inputProps);
          wpaProps.put(InputProperty.TargetPrefix, true);
          ++previousPrefixSize;
          for(; previousPrefixSize < input.reference.size(); ++previousPrefixSize) {
            wpaTranslations = decode(input.source, input.sourceInputId, infererId, nbestListSize, 
                Collections.singletonList(input.reference.subsequence(0, previousPrefixSize)), wpaProps);
    
            if(WordPredictionAccuracy.correctPrediction(wpaTranslations, wpa_nbest_size, input.reference, previousPrefixSize)) {
              ++wpaCorrect;
            }
            ++wpaTotal;
          }
         
        }
      }
      
      previousPrefixSize = input.targets != null && input.targets.size() > 0 ?
          input.targets.get(0).size() : 0;
      if(input.oracle_nbest_size > 0 && input.reference != null) {
        bestTranslation = WordPredictionAccuracy.getBestMatch(translations, oracle_nbest_size, input.reference, previousPrefixSize);
      }
      
      return new DecoderOutput(input.source.size(), translations, bestTranslation, input.sourceInputId, 
          ksrTyped, ksrTotal, wpaCorrect, wpaTotal);
    }

    @Override
    public ThreadsafeProcessor<DecoderInput, DecoderOutput> newInstance() {
      return new PhrasalProcessor(childInfererId++);
    }
  }

  /**
   * Output the result of decodeFromConsole(), and write to the n-best list if
   * necessary.
   *
   * NOTE: This call is *not* threadsafe.
   *
   * @param translations
   *          n-best list
   * @param bestTranslation
   *          if post-processing has been applied, then this is post-processed
   *          sequence at the top of the n-best list
   * @param sourceInputId
   */
  private void processConsoleResult(List<RichTranslation<IString, String>> translations,
      Sequence<IString> bestTranslation, int sourceLength, int sourceInputId) {
    if (translations.size() > 0) {
      final RichTranslation<IString, String> bestTranslationInfo = translations.get(0);
      if (printModelScores) {
        System.out.printf("%e\t%s%n", bestTranslationInfo.score, bestTranslation.toString());
      } else {
        System.out.println(bestTranslation.toString());
      }

      // log additional information to stderr
      logger.info("input {}: 1-best model score: {}", sourceInputId, bestTranslationInfo.score);

      // Output the n-best list if necessary
      if (nbestListWriter != null) {
        IOTools.writeNbest(translations, sourceInputId, nbestListOutputType, nBestListFeaturePattern, nbestListWriter);
      }

      // Output the alignments if necessary
      if (alignmentWriter != null) {
        for (final RichTranslation<IString, String> translation : translations) {
          alignmentWriter.printf("%d %s %s%n", sourceInputId, CompiledPhraseTable.FIELD_DELIM,
              translation.alignmentString());
        }
      }

    } else {
      // Decoder failure. Print an empty line.
      System.out.println();

      // Output the n-best list if necessary
      if (nbestListWriter != null) {
        IOTools.writeEmptyNBest(sourceInputId, nbestListWriter);
      }

      // Output the alignments if necessary
      if (alignmentWriter != null) {
        alignmentWriter.println();
      }

      logger.info("<<< decoder failure for id: {} >>>", sourceInputId);
    }
  }

  /**
   * Decode input from inputStream and either write 1-best translations to
   * stdout or return them in a <code>List</code>.
   *
   * @param inputStream
   * @param outputToConsole
   *          if true, output the 1-best translations to the console. Otherwise,
   *          return them in a <code>List</code>
   * @throws IOException
   */
  public List<RichTranslation<IString, String>> decode(InputStream inputStream, boolean outputToConsole)
      throws IOException {
    logger.info("Entering main translation loop");
    final MulticoreWrapper<DecoderInput, DecoderOutput> wrapper = new MulticoreWrapper<>(numThreads, 
        new PhrasalProcessor(0));
    final LineNumberReader reader = new LineNumberReader(new InputStreamReader(inputStream, 
        IOTools.DEFAULT_ENCODING));
    final List<RichTranslation<IString, String>> bestTranslationList = outputToConsole ? null
        : new ArrayList<>();

    // Sanity check -- Set each thread's model to the current global model.
    this.scorers.stream().forEach(scorer -> scorer.updateWeights(globalModel));

    int ksrTyped = 0;
    int ksrTotal = 0;
    int wpaCorrect = 0;
    int wpaTotal = 0;
    
    boolean doEval = references != null && (ksr_nbest_size > 0 || wpa_nbest_size > 0);
    
    final LineNumberReader refReader = doEval ?
        new LineNumberReader(new InputStreamReader(new FileInputStream(new File(references)), 
            IOTools.DEFAULT_ENCODING))
        : null;
    
    final long startTime = TimingUtils.startTime();
    int sourceInputId = 0;
    for (String line; (line = reader.readLine()) != null; ++sourceInputId) {
      final Sequence<IString> source = preprocessor == null ? IStrings.tokenize(line)
          : preprocessor.process(line.trim());

      if (source.size() > maxSentenceSize || source.size() < minSentenceSize) {
        logger.warn("Skipping: {}", line);
        logger.warn("Tokens: {} (min: {} max: {})", source.size(), minSentenceSize, maxSentenceSize);
        continue;
      }

      final InputProperties inputProps = inputPropertiesList != null && sourceInputId < inputPropertiesList.size()
          ? inputPropertiesList.get(sourceInputId) : new InputProperties();
      final List<Sequence<IString>> targets = forceDecodeReferences == null ? null
          : forceDecodeReferences.get(sourceInputId);

      Sequence<IString> ref = null;
      if(doEval) {
        String refLine = refReader.readLine();
        if(refLine == null) {
          System.err.println("ERROR: reference file is too short");
          System.exit(-1);
        }
        ref = IStrings.tokenize(refLine);
      }
      
      wrapper.put(new DecoderInput(source, sourceInputId, targets, inputProps, ksr_nbest_size, wpa_nbest_size, oracle_nbest_size, ref));
      for (DecoderOutput result; (result = wrapper.poll()) != null;) {
        if (outputToConsole) {
          processConsoleResult(result.translations, result.bestTranslation, result.sourceLength, result.sourceInputId);
        } else {
          final RichTranslation<IString, String> best = result.translations.size() > 0 ? 
              result.translations.get(0) : null;
          bestTranslationList.add(best);
        }
        ksrTyped += result.ksrTyped;
        ksrTotal += result.ksrTotal;
        wpaCorrect += result.wpaCorrect;
        wpaTotal += result.wpaTotal;
      }
    }

    // Finished reading the input. Wait for threadpool to finish, then process
    // last few translations.
    wrapper.join();
    while (wrapper.peek()) {
      final DecoderOutput result = wrapper.poll();
      if (outputToConsole) {
        processConsoleResult(result.translations, result.bestTranslation, result.sourceLength, result.sourceInputId);
      } else {
        final RichTranslation<IString, String> best = result.translations.size() > 0 ? result.translations.get(0)
            : null;
        bestTranslationList.add(best);
      }
      ksrTyped += result.ksrTyped;
      ksrTotal += result.ksrTotal;
      wpaCorrect += result.wpaCorrect;
      wpaTotal += result.wpaTotal;
    }

    final double totalTime = TimingUtils.elapsedSeconds(startTime);
    final double segmentsPerSec = sourceInputId / totalTime;
    logger.info("Decoding at {} segments/sec (total: {} sec)", segmentsPerSec, totalTime);
    
    reader.close();
    if(refReader != null) refReader.close();
    
    if(ksrTotal > 0) logger.info("KSR: {} / {} = {}", ksrTyped, ksrTotal, ((double) ksrTyped) / ksrTotal);
    if(wpaTotal > 0) logger.info("Word prediction accuracy: {} / {} = {}", wpaCorrect, wpaTotal, ((double) wpaCorrect) / wpaTotal);
    return bestTranslationList;
  }

  /**
   * Decode a tokenized input string. Returns an n-best list of translations as
   * specified by the decoders <code>nbestListSize</code> parameter.
   *
   * NOTE: This call is threadsafe.
   *
   * @param source
   * @param sourceInputId
   * @param threadId
   *          -- Inferer object to use (one per thread)
   */
  public List<RichTranslation<IString, String>> decode(Sequence<IString> source, int sourceInputId, int threadId) {
    final InputProperties inputProps = inputPropertiesList != null && sourceInputId < inputPropertiesList.size()
        ? inputPropertiesList.get(sourceInputId) : new InputProperties();
    final List<Sequence<IString>> targets = forceDecodeReferences == null ? null
        : forceDecodeReferences.get(sourceInputId);
    return decode(source, sourceInputId, threadId, nbestListSize, targets, inputProps);
  }

  /**
   * Decode a tokenized input string with associated {@link InputProperties}.
   *
   * @param source
   * @param sourceInputId
   * @param threadId
   * @param inputProperties
   * @return
   */
  public List<RichTranslation<IString, String>> decode(Sequence<IString> source, int sourceInputId, int threadId,
      InputProperties inputProperties) {
    final List<Sequence<IString>> targets = forceDecodeReferences == null ? null
        : forceDecodeReferences.get(sourceInputId);
    return decode(source, sourceInputId, threadId, this.nbestListSize, targets, inputProperties);
  }

  /**
   * Decode a tokenized input string. Returns an n-best list of translations
   * specified by the parameter.
   *
   * NOTE: This call is threadsafe.
   *
   * @param source
   * @param sourceInputId
   * @param threadId
   *          -- Inferer object to use (one per thread)
   * @param numTranslations
   *          number of translations to generate
   * @param inputProperties
   * 
   */
  @SuppressWarnings("unchecked")
  public List<RichTranslation<IString, String>> decode(Sequence<IString> source, int sourceInputId, int threadId,
      int numTranslations, List<Sequence<IString>> targets, InputProperties inputProperties) {
    Objects.requireNonNull(source);
    if (threadId < 0 || threadId >= numThreads)
      throw new IndexOutOfBoundsException("Thread id out of bounds: " + String.valueOf(threadId));
    if (sourceInputId < 0)
      throw new IndexOutOfBoundsException("Source id must be non-negative: " + String.valueOf(sourceInputId));

    final TimeKeeper timer = TimingUtils.start();
    
    // Wrapping input for TMs with boundary tokens
    if (wrapBoundary) source = Sequences.wrapStartEnd(source, TokenUtils.START_TOKEN, 
        TokenUtils.END_TOKEN);

    // Output space of the decoder
    final boolean targetsArePrefixes = inputProperties.containsKey(InputProperty.TargetPrefix)
        ? (boolean) inputProperties.get(InputProperty.TargetPrefix) : false;
    final OutputSpace<IString, String> outputSpace = OutputSpaceFactory.getOutputSpace(sourceInputId, targets,
        targetsArePrefixes, translationModel.maxLengthSource(), translationModel.maxLengthTarget(), wrapBoundary);

    // Configure the translation model
    if (inputProperties.containsKey(InputProperty.ForegroundTM)) {
      final TranslationModel<IString, String> tm = (TranslationModel<IString, String>) inputProperties
          .get(InputProperty.ForegroundTM);
      tm.setFeaturizer(featurizer);
      tm.setName(TM_FOREGROUND_NAME);
      logger.info("Configured foreground translation model for thread {}: {}", threadId, tm.getName());
    }
    if (inputProperties.containsKey(InputProperty.ModelWeights)) {
      final Counter<String> weights = (Counter<String>) inputProperties.get(InputProperty.ModelWeights);
      this.scorers.get(threadId).updateWeights(weights);
      logger.info("Loaded decoder-local weights for thread {}", threadId);

    } else {
      this.scorers.get(threadId).updateWeights(this.globalModel);
    }
    if (! inputProperties.containsKey(InputProperty.RuleQueryLimit)) {
      inputProperties.put(InputProperty.RuleQueryLimit, ruleQueryLimit);
    }
    timer.mark("setup");
    
    // Decode
    List<RichTranslation<IString, String>> translations = new ArrayList<>(1);
    if (numTranslations > 1) {
      translations = inferers.get(threadId).nbest(source, sourceInputId, inputProperties, outputSpace,
          outputSpace.getAllowableSequences(), numTranslations, distinctNbest, nbestMode);

      // Decoder failure
      if (translations == null) translations = Collections.emptyList();

    } else {
      // The 1-best translation in this case is potentially different from
      // calling nbest() with a list size of 1. Therefore, this call is *not* a
      // special case of the condition above.
      final RichTranslation<IString, String> translation = inferers.get(threadId).translate(source, sourceInputId,
          inputProperties, outputSpace, outputSpace.getAllowableSequences());
      if (translation != null) translations.add(translation);
    }
    timer.mark("decode");
    logger.info("Decode timing: {}", timer);
    return translations;
  }

  /**
   * Word-align a given sentence pair.
   *
   * @param source
   * @param sourceInputId
   * @param threadId
   *          -- Inferer object to use (one per thread)
   * @param target
   * @param inputProperties
   * 
   */
  @SuppressWarnings("unchecked")
  public SymmetricalWordAlignment wordAlign(Sequence<IString> source, int sourceInputId, int threadId,
      Sequence<IString> target, InputProperties inputProperties) {
    Objects.requireNonNull(source);
    Objects.requireNonNull(target);
    
    if (threadId < 0 || threadId >= numThreads)
      throw new IndexOutOfBoundsException("Thread id out of bounds: " + String.valueOf(threadId));
    if (sourceInputId < 0)
      throw new IndexOutOfBoundsException("Source id must be non-negative: " + String.valueOf(sourceInputId));

    final TimeKeeper timer = TimingUtils.start();
    
    // Wrapping input for TMs with boundary tokens
    if (wrapBoundary) source = Sequences.wrapStartEnd(source, TokenUtils.START_TOKEN, 
        TokenUtils.END_TOKEN);
    
    // Configure the translation model
    if (inputProperties.containsKey(InputProperty.ForegroundTM)) {
      final TranslationModel<IString, String> tm = (TranslationModel<IString, String>) inputProperties
          .get(InputProperty.ForegroundTM);
      tm.setFeaturizer(featurizer);
      tm.setName(TM_FOREGROUND_NAME);
      logger.info("Configured foreground translation model for thread {}: {}", threadId, tm.getName());
    }
    if (inputProperties.containsKey(InputProperty.ModelWeights)) {
      final Counter<String> weights = (Counter<String>) inputProperties.get(InputProperty.ModelWeights);
      this.scorers.get(threadId).updateWeights(weights);
      logger.info("Loaded decoder-local weights for thread {}", threadId);

    } else {
      this.scorers.get(threadId).updateWeights(this.globalModel);
    }
    timer.mark("setup");

    // Align
    final SymmetricalWordAlignment alignment = inferers.get(threadId).wordAlign(source, target, sourceInputId, inputProperties);
    
    timer.mark("alignment");
    logger.info("Alignment timing: {}", timer);
    return alignment;

  }
  
  
  /**
   * Free resources and cleanup.
   */
  private void shutdown() {
    if (nbestListWriter != null) {
      logger.info("Closing n-best writer");
      nbestListWriter.close();
    }

    if (alignmentWriter != null) {
      logger.info("Closing alignment writer");
      alignmentWriter.close();
    }
  }

  /**
   * Read a combination of config file and other command line arguments.
   * Command-line arguments supercede those specified in the config file.
   *
   * @param configFile
   * @param options
   * @return
   * @throws IOException
   */
  private static Map<String, List<String>> getConfigurationFrom(String configFile, Properties options)
      throws IOException {
    final Map<String, List<String>> config = configFile == null ? new HashMap<>()
        : IOTools.readConfigFile(configFile);
    // Command-line options supersede config file options
    options.entrySet().stream().forEach(e -> config.put(e.getKey().toString(), 
        Arrays.asList(e.getValue().toString().split("\\s+"))));
    return config;
  }

  /**
   * Load an instance of Phrasal from an ini file.
   *
   * @param phrasalIniFile
   * @throws IOException
   */
  public static Phrasal loadDecoder(String phrasalIniFile) throws IOException {
    return loadDecoder(IOTools.readConfigFile(phrasalIniFile));
  }

  /**
   * Load an instance of Phrasal from a parsed ini file.
   *
   * @param config
   * @return
   */
  public static Phrasal loadDecoder(Map<String, List<String>> config) {
    try {
      Phrasal.initStaticMembers(config);
      final Phrasal phrasal = new Phrasal(config);

      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          phrasal.shutdown();
        }
      });
      return phrasal;

    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | 
        SecurityException | InvocationTargetException | NoSuchMethodException | 
        ClassNotFoundException | IOException e) {
      logger.error("Unable to load Phrasal", e);
    }
    return null;
  }
  

  /**
   * Run Phrasal from the command line.
   *
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    final Properties options = StringUtils.argsToProperties(args);
    final String configFile = options.containsKey("") ? (String) options.get("") : null;
    options.remove("");
    if ((options.size() == 0 && configFile == null) || options.containsKey("help") || options.containsKey("h")) {
      System.err.println(usage());
      System.exit(-1);
    }

    // by default, exit on uncaught exception
    Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
      logger.fatal("Uncaught top-level exception", ex);
      System.exit(-1);
    });

    final Map<String, List<String>> configuration = getConfigurationFrom(configFile, options);
    final Phrasal p = Phrasal.loadDecoder(configuration);
    
    if (options.containsKey("text")) p.decode(new FileInputStream(new File(options.getProperty("text"))), true);
    else p.decode(System.in, true);
  }
}
