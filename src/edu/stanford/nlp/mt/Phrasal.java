// Phrasal -- A Statistical Machine Translation Toolkit
// for Exploring New Model Features.
// Copyright (c) 2007-2010 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    java-nlp-user@lists.stanford.edu
//    http://nlp.stanford.edu/software/phrasal

package edu.stanford.nlp.mt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

import edu.stanford.nlp.mt.decoder.AbstractBeamInferer;
import edu.stanford.nlp.mt.decoder.AbstractBeamInfererBuilder;
import edu.stanford.nlp.mt.decoder.DTUDecoder;
import edu.stanford.nlp.mt.decoder.Inferer;
import edu.stanford.nlp.mt.decoder.InfererBuilderFactory;
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
import edu.stanford.nlp.mt.tm.CombinedPhraseGenerator;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.DTUTable;
import edu.stanford.nlp.mt.tm.ExtendedLexicalReorderingTable;
import edu.stanford.nlp.mt.tm.FlatPhraseTable;
import edu.stanford.nlp.mt.tm.LexicalReorderingTable;
import edu.stanford.nlp.mt.tm.PhraseGenerator;
import edu.stanford.nlp.mt.tm.PhraseGeneratorFactory;
import edu.stanford.nlp.mt.tm.PhraseTable;
import edu.stanford.nlp.mt.tm.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SourceClassMap;
import edu.stanford.nlp.mt.util.SystemLogger;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.mt.util.SystemLogger.LogName;
import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerFactory;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.DTULinearDistortionFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.HierarchicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.LexicalReorderingFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.base.LinearFutureCostFeaturizer;
import edu.stanford.nlp.mt.decoder.h.HeuristicFactory;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Phrasal: a phrase-based machine translation system from the Stanford University
 *          NLP group.
 *
 * NOTE: This object is not threadsafe. To enable programmatic multithreading with Phrasal,
 * specify the number of threads in the *.ini as usual, then use the threadId arguments
 * in the decode() functions to submit to the underlying threadpool. This design permits
 * storage of the LM and phrase table---among other large data structures---in shared memory.
 *
 * @author danielcer
 * @author Michel Galley
 * @author Spence Green
 *
 */
public class Phrasal {

  // TODO(spenceg): Add future cost delay as a parameter. Currently it must be set as a JVM parameter.
  // TODO(spenceg): Add input encoding option. Replace all instances of "UTF-8" in the codebase. 
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(Phrasal.class.getName()).append(" OPTS [ini_file] < input > output").append(nl).append(nl)
      .append("Phrasal: A phrase-based machine translation decoder from the Stanford NLP group.").append(nl).append(nl)
      .append("Command-line arguments override arguments specified in the optional ini_file:").append(nl)
      .append("  -").append(TRANSLATION_TABLE_OPT).append(" filename : Translation model file. Multiple models can be specified by separating filenames with colons.").append(nl)
      .append("  -").append(LANGUAGE_MODEL_OPT).append(" filename : Language model file. For KenLM, prefix filename with 'kenlm:'").append(nl)
      .append("  -").append(OPTION_LIMIT_OPT).append(" num : Translation option limit.").append(nl)
      .append("  -").append(NBEST_LIST_OPT).append(" num : n-best list size.").append(nl)
      .append("  -").append(DISTINCT_NBEST_LIST_OPT).append(" boolean : Generate distinct n-best lists (default: false)").append(nl)
      .append("  -").append(FORCE_DECODE).append(" filename [filename] : Force decode to reference file(s).").append(nl)
      .append("  -").append(BEAM_SIZE).append(" num : Stack/beam size.").append(nl)
      .append("  -").append(SEARCH_ALGORITHM).append(" [cube|multibeam] : Inference algorithm (default:cube)").append(nl)
      .append("  -").append(REORDERING_MODEL).append(" type filename [options] : Lexicalized re-ordering model where type is [classic|hierarchical]. Multiple models can be separating filenames with colons.").append(nl)
      .append("  -").append(WEIGHTS_FILE).append(" filename : Load all model weights from file.").append(nl)
      .append("  -").append(MAX_SENTENCE_LENGTH).append(" num : Maximum input sentence length.").append(nl)
      .append("  -").append(MIN_SENTENCE_LENGTH).append(" num : Minimum input sentence length.").append(nl)
      .append("  -").append(DISTORTION_LIMIT).append(" num : Hard distortion limit.").append(nl)
      .append("  -").append(ADDITIONAL_FEATURIZERS).append(" class [class] : List of additional feature functions.").append(nl)
      .append("  -").append(DISABLED_FEATURIZERS).append(" class [class] : List of baseline featurizers to disable.").append(nl)
      .append("  -").append(NUM_THREADS).append(" num : Number of decoding threads (default: 1)").append(nl)
      .append("  -").append(USE_ITG_CONSTRAINTS).append(" boolean : Use ITG constraints for decoding (multibeam search only)").append(nl)
      .append("  -").append(RECOMBINATION_MODE).append(" name : Recombination mode [pharoah,exact,dtu] (default: exact).").append(nl)
      .append("  -").append(DROP_UNKNOWN_WORDS).append(" boolean : Drop unknown source words from the output (default: false)").append(nl)
      .append("  -").append(INDEPENDENT_PHRASE_TABLES).append(" filename [filename] : Phrase tables that cannot have associated reordering models. Optionally supports custom per-table prefixes for features (e.g., pref:filename).").append(nl)
      .append("  -").append(ALIGNMENT_OUTPUT_FILE).append(" filename : Output word-word alignments to file for each translation.").append(nl)
      .append("  -").append(PREPROCESSOR_FILTER).append(" language [opts] : Pre-processor to apply to source input.").append(nl)
      .append("  -").append(POSTPROCESSOR_FILTER).append(" language [opts] : Post-processor to apply to target output.").append(nl)
      .append("  -").append(SOURCE_CLASS_MAP).append(" filename : Feature API: Line-delimited source word->class mapping (TSV format).").append(nl)
      .append("  -").append(TARGET_CLASS_MAP).append(" filename : Feature API: Line-delimited target word->class mapping (TSV format).").append(nl)
      .append("  -").append(GAPS_OPT).append(" options : DTU: Enable Galley and Manning (2010) gappy decoding.").append(nl)
      .append("  -").append(MAX_PENDING_PHRASES_OPT).append(" num : DTU: Max number of pending phrases for decoding.").append(nl)
      .append("  -").append(GAPS_IN_FUTURE_COST_OPT).append(" boolean : DTU: Allow gaps in future cost estimate (default: true)").append(nl)
      .append("  -").append(LINEAR_DISTORTION_TYPE).append(" type : DTU: See ConcreteRule.LinearDistortionType (default: standard)").append(nl)
      .append("  -").append(PRINT_MODEL_SCORES).append(" boolean : Output model scores with translations (default: false)").append(nl)
      .append("  -").append(LOG_PREFIX).append(" string : Log file prefix").append(nl)
      .append("  -").append(LOG_LEVEL).append(" level : Case-sensitive java.logging log level (default: WARNING)").append(nl)
      .append("  -").append(INPUT_PROPERTIES).append(" file : File specifying properties of each source input.").append(nl)
      .append("  -").append(FEATURE_AUGMENTATION).append(" mode : Feature augmentation mode [all|dense|extended].").append(nl)
      .append("  -").append(WRAP_BOUNDARY).append(" boolean : Add boundary tokens around each input sentence (default: false).")
       // Thang May14
       .append("  -").append(HISTORY_NBEST_LIST_OPT).append(" boolean : append history information to n-best list output (default: false)").append(nl)
       ;
    return sb.toString();
  }

  public static final String TRANSLATION_TABLE_OPT = "ttable-file";
  public static final String LANGUAGE_MODEL_OPT = "lmodel-file";
  public static final String OPTION_LIMIT_OPT = "ttable-limit";
  public static final String NBEST_LIST_OPT = "n-best-list";
  public static final String DISTINCT_NBEST_LIST_OPT = "distinct-n-best-list";
  public static final String FORCE_DECODE = "force-decode";
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
  public static final String LINEAR_DISTORTION_TYPE = "linear-distortion-type";
  public static final String DROP_UNKNOWN_WORDS = "drop-unknown-words";
  public static final String INDEPENDENT_PHRASE_TABLES = "independent-phrase-tables";
  public static final String ALIGNMENT_OUTPUT_FILE = "alignment-output-file";
  public static final String PREPROCESSOR_FILTER = "preprocessor-filter";
  public static final String POSTPROCESSOR_FILTER = "postprocessor-filter";
  public static final String SOURCE_CLASS_MAP = "source-class-map";
  public static final String TARGET_CLASS_MAP = "target-class-map";
  public static final String PRINT_MODEL_SCORES = "print-model-scores";
  public static final String LOG_PREFIX = "log-prefix";
  public static final String LOG_LEVEL = "log-level";
  public static final String INPUT_PROPERTIES = "input-properties";
  public static final String FEATURE_AUGMENTATION = "feature-augmentation";
  public static final String WRAP_BOUNDARY = "wrap-boundary";
  
  // Thang May14: store derivation history in the nbest list
  public static final String HISTORY_NBEST_LIST_OPT = "history-n-best-list";

  private static final Set<String> REQUIRED_FIELDS = Generics.newHashSet();
  private static final Set<String> OPTIONAL_FIELDS = Generics.newHashSet();
  private static final Set<String> ALL_RECOGNIZED_FIELDS = Generics.newHashSet();
  static {
    REQUIRED_FIELDS.addAll(Arrays.asList(TRANSLATION_TABLE_OPT));
    OPTIONAL_FIELDS.addAll(Arrays.asList(WEIGHTS_FILE,
        REORDERING_MODEL, DISTORTION_LIMIT, 
        ADDITIONAL_FEATURIZERS, DISABLED_FEATURIZERS,
        OPTION_LIMIT_OPT, NBEST_LIST_OPT,
        DISTINCT_NBEST_LIST_OPT, FORCE_DECODE,
        RECOMBINATION_MODE, SEARCH_ALGORITHM,
        BEAM_SIZE, WEIGHTS_FILE, MAX_SENTENCE_LENGTH,
        MIN_SENTENCE_LENGTH, USE_ITG_CONSTRAINTS,
        NUM_THREADS, GAPS_OPT, GAPS_IN_FUTURE_COST_OPT,
        LINEAR_DISTORTION_TYPE, MAX_PENDING_PHRASES_OPT,
        DROP_UNKNOWN_WORDS, INDEPENDENT_PHRASE_TABLES,
        LANGUAGE_MODEL_OPT, 
        ALIGNMENT_OUTPUT_FILE, PREPROCESSOR_FILTER, POSTPROCESSOR_FILTER,
        SOURCE_CLASS_MAP,TARGET_CLASS_MAP, PRINT_MODEL_SCORES,
        LOG_PREFIX, LOG_LEVEL, INPUT_PROPERTIES, FEATURE_AUGMENTATION,
        WRAP_BOUNDARY
        , HISTORY_NBEST_LIST_OPT // Thang May14
        ));
    ALL_RECOGNIZED_FIELDS.addAll(REQUIRED_FIELDS);
    ALL_RECOGNIZED_FIELDS.addAll(OPTIONAL_FIELDS);
  }

  /**
   * Number of decoding threads. Setting this parameter to 0 enables
   * multithreading inside the main decoding loop. Generally, it is better
   * to set the desired number of threads here (i.e., set this parameter >= 1).
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
   * DTU options
   * 
   * TODO(spenceg): Remove static members. The Phrasal object itself is not threadsafe.
   */
  private static List<String> gapOpts = null;
  public static boolean withGaps = false;

  /**
   * Inference objects, one per thread
   */
  public List<Inferer<IString, String>> inferers;

  /**
   * Holds the model weights, one per inferer. The model weights have a shared feature index.
   */
  private List<Scorer<String>> scorers;

  /**
   * Phrase table / translation model
   */
  private PhraseGenerator<IString,String> phraseGenerator;

  /**
   * Whether to filter unknown words in the output
   */
  private boolean dropUnknownWords = false;

  /**
   * @return true if unknown words are dropped, and false otherwise.
   */
  public boolean isDropUnknownWords() { return dropUnknownWords; }
  
  /**
   * n-best list options
   */
  private String nbestListOutputType = "moses";
  private PrintStream nbestListWriter;
  private int nbestListSize;
  
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
  private boolean wrapBoundary;
  
  /**
   * Pre/post processing filters.
   */
  private Preprocessor preprocessor;
  private Postprocessor postprocessor;
  
  public Preprocessor getPreprocessor() { return preprocessor; }
  public Postprocessor getPostprocessor() { return postprocessor; }
  
  /**
   * Access the decoder's scorer, which contains the model weights. THere is one scorer
   * per thread.
   *
   * @return the scorer
   */
  public Scorer<String> getScorer(int threadId) {
    if(threadId >= 0 && threadId < numThreads) {
      return scorers.get(threadId);
    }
    throw new RuntimeException("Illegal thread id: " + String.valueOf(threadId));
  }

  /**
   * @return the number of threads specified in the ini file.
   */
  public int getNumThreads() { return numThreads; }
  
  /**
   * Access the decoder's phrase table.
   * 
   * @return
   */
  public PhraseGenerator<IString,String> getPhraseTable() { return phraseGenerator; }
 
  /**
   * @return The wrap boundary property specified in the ini file.
   */
  public boolean getWrapBoundary() {
    return wrapBoundary;
  }
  
  // TODO(spenceg): Remove static members. The Phrasal object itself is not threadsafe.
  public static void initStaticMembers(Map<String, List<String>> config) {
    SystemLogger.disableConsoleLogger();
    if (config.containsKey(LOG_PREFIX)) {
      SystemLogger.setPrefix(config.get(LOG_PREFIX).get(0));
    }
    if (config.containsKey(LOG_LEVEL)) {
      SystemLogger.setLevel(LogName.DECODE, Level.parse(config.get(LOG_LEVEL).get(0)));
    }
    withGaps = config.containsKey(GAPS_OPT);
    gapOpts = withGaps ? config.get(GAPS_OPT) : null;
    if (config.containsKey(GAPS_IN_FUTURE_COST_OPT))
      DTUDecoder.gapsInFutureCost = Boolean.parseBoolean(config.get(
          GAPS_IN_FUTURE_COST_OPT).get(0));
    if (config.containsKey(DISTINCT_NBEST_LIST_OPT))
      if (!AbstractBeamInferer.DISTINCT_SURFACE_TRANSLATIONS)
        AbstractBeamInferer.DISTINCT_SURFACE_TRANSLATIONS = Boolean.parseBoolean(config.get(
            DISTINCT_NBEST_LIST_OPT).get(0));
    if (config.containsKey(LINEAR_DISTORTION_TYPE))
      ConcreteRule.setLinearDistortionType(config.get(
          LINEAR_DISTORTION_TYPE).get(0));
    else if (withGaps)
      ConcreteRule
          .setLinearDistortionType(ConcreteRule.LinearDistortionType.last_contiguous_segment
              .name());
    
    // Thang May14
    if (config.containsKey(HISTORY_NBEST_LIST_OPT)) {
      AbstractBeamInferer.HISTORY_NBEST_LIST = Boolean.parseBoolean(config.get(HISTORY_NBEST_LIST_OPT).get(0));
    }
  }

  @SuppressWarnings("unchecked")
  public Phrasal(Map<String, List<String>> config) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException,
      SecurityException, InvocationTargetException, NoSuchMethodException,
      ClassNotFoundException {
    // Check for required parameters
    if (!config.keySet().containsAll(REQUIRED_FIELDS)) {
      Set<String> missingFields = Generics.newHashSet(REQUIRED_FIELDS);
      missingFields.removeAll(config.keySet());
      throw new RuntimeException(String.format(
          "The following required fields are missing: %s%n", missingFields));
    }
    // Check for unrecognized parameters
    if (!ALL_RECOGNIZED_FIELDS.containsAll(config.keySet())) {
      Set<String> extraFields = Generics.newHashSet(config.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_FIELDS);
      throw new RuntimeException(String.format(
          "The following fields are unrecognized: %s%n", extraFields));
    }

    numThreads = config.containsKey(NUM_THREADS) ? Integer.parseInt(config.get(NUM_THREADS).get(0)) : 1;
    if (numThreads < 1) throw new RuntimeException("Number of threads must be positive: " + numThreads);
    System.err.printf("Number of threads: %d%n", numThreads);

    if (withGaps) {
      recombinationMode = RecombinationFilterFactory.DTU_RECOMBINATION;
    } else if (config.containsKey(RECOMBINATION_MODE)) {
      recombinationMode = config.get(RECOMBINATION_MODE).get(0);
    }
    
    if (config.containsKey(PRINT_MODEL_SCORES)) {
      printModelScores = Boolean.valueOf(config.get(PRINT_MODEL_SCORES).get(0));
    }
    
    inputPropertiesList = config.containsKey(INPUT_PROPERTIES) ? 
        InputProperties.parse(new File(config.get(INPUT_PROPERTIES).get(0))) : new ArrayList<InputProperties>(1);
     
     wrapBoundary  = config.containsKey(WRAP_BOUNDARY) ? 
         Boolean.valueOf(config.get(WRAP_BOUNDARY).get(0)) : false;
         
    // Pre/post processor filters. These may be accessed programmatically, but they
    // are only applied automatically to text read from the console.
    if (config.containsKey(PREPROCESSOR_FILTER)) {
      List<String> parameters = config.get(PREPROCESSOR_FILTER);
      if (parameters.size() == 0) throw new RuntimeException("Preprocessor configuration requires at least one argument");
      String language = parameters.get(0);
      String[] options = parameters.size() > 1 ? parameters.get(1).split("\\s+") : (String[]) null;
      preprocessor = ProcessorFactory.getPreprocessor(language, options);
      System.err.printf("Preprocessor filter: %s%n", preprocessor.getClass().getName());
    }
    if (config.containsKey(POSTPROCESSOR_FILTER)) {
      List<String> parameters = config.get(POSTPROCESSOR_FILTER);
      if (parameters.size() == 0) throw new RuntimeException("Postprocessor configuration requires at least one argument");
      String language = parameters.get(0);
      String[] options = parameters.size() > 1 ? parameters.get(1).split("\\s+") : (String[]) null;
      postprocessor = ProcessorFactory.getPostprocessor(language, options);
      System.err.printf("Postprocessor filter: %s%n", postprocessor.getClass().getName());
    }
    
    // Word->class maps
    if (config.containsKey(SOURCE_CLASS_MAP)) {
      List<String> parameters = config.get(SOURCE_CLASS_MAP);
      if (parameters.size() == 0) throw new RuntimeException("Source class map requires a file argument");
      SourceClassMap map = SourceClassMap.getInstance();
      for (String filename : parameters) {
        map.load(filename);
        System.err.println("Loaded source class map: " + filename);
      }
    }
    if (config.containsKey(TARGET_CLASS_MAP)) {
      List<String> parameters = config.get(TARGET_CLASS_MAP);
      if (parameters.size() == 0) throw new RuntimeException("Target class map requires a file argument");
      TargetClassMap map = TargetClassMap.getInstance();
      for (String filename : parameters) {
        map.load(filename);
        System.err.println("Loaded target class map: " + filename);
      }
    }
    
    if (config.containsKey(FORCE_DECODE)) {
      forceDecodeReferences = MetricUtils.readReferences(config.get(FORCE_DECODE)
          .toArray(new String[config.get(FORCE_DECODE).size()]));
    }

    // int distortionLimit = -1;
    if (config.containsKey(DISTORTION_LIMIT)) {
      List<String> strDistortionLimit = config.get(DISTORTION_LIMIT);
      if (strDistortionLimit.size() != 1) {
        throw new RuntimeException(String.format(
            "Parameter '%s' takes one and only one argument", DISTORTION_LIMIT));
      }
      try {
        distortionLimit = Integer.parseInt(strDistortionLimit.get(0));
      } catch (NumberFormatException e) {
        throw new RuntimeException(
            String
                .format(
                    "Argument '%s' to parameter '%s' can not be parsed as an integer value%n",
                    strDistortionLimit.get(0), DISTORTION_LIMIT));
      }
    }
    
    // DTU decoding
    FeaturizerFactory.GapType gapT = !withGaps ? FeaturizerFactory.GapType.none
        : ((gapOpts.size() > 1) ? FeaturizerFactory.GapType.both
            : FeaturizerFactory.GapType.source);
    String gapType = gapT.name();
    System.err.println("Gap type: " + gapType);

    // Phrase table(s), which is a required parameter
    List<String> ptOpts = config.get(TRANSLATION_TABLE_OPT);
    String phraseTable = ptOpts.get(0);
    int numPhraseFeatures = Integer.MAX_VALUE;
    if (ptOpts.size() == 2) {
      numPhraseFeatures = Integer.valueOf(ptOpts.get(1));
      System.err.printf("Number of features for %s: %d%n", phraseTable, numPhraseFeatures);
    }

    if (withGaps) {
      // Support for gaps:
      if (gapOpts.size() < 1 || gapOpts.size() > 2)
        throw new UnsupportedOperationException();
      int maxSourcePhraseSpan = Integer.parseInt(gapOpts.get(0));
      DTUTable.setMaxPhraseSpan(maxSourcePhraseSpan);

      int maxTargetPhraseSpan = (gapOpts.size() > 1) ? Integer.parseInt(gapOpts
          .get(1)) : -1;
      if (maxTargetPhraseSpan == -1) {
        System.err.println("Phrases with target gaps not loaded into memory.");
        DTUTable.maxNumberTargetSegments = 1;
      }
      if (gapT == FeaturizerFactory.GapType.target
          || gapT == FeaturizerFactory.GapType.both) {
        DTUHypothesis.setMaxTargetPhraseSpan(maxTargetPhraseSpan);
        //AbstractBeamInferer.DISTINCT_SURFACE_TRANSLATIONS = true; // TODO: restore?
      }

      // Support for floating phrases:
      if (config.containsKey(MAX_PENDING_PHRASES_OPT)) {
        List<String> floatOpts = config.get(MAX_PENDING_PHRASES_OPT);
        if (floatOpts.size() != 1)
          throw new UnsupportedOperationException();
        int maxPendingPhrases = Integer.parseInt(floatOpts.get(0));
        DTUHypothesis.setMaxPendingPhrases(maxPendingPhrases);
      }
    }

    // Phrase table query size limit
    if (config.containsKey(OPTION_LIMIT_OPT)) { 
      ruleQueryLimit = Integer.valueOf(config.get(OPTION_LIMIT_OPT).get(0));
    }
    System.err.printf("Phrase table option limit: %d%n", ruleQueryLimit);

    // Create the phrase table(s) 
    final String optionLimitString = String.valueOf(this.ruleQueryLimit);
    final String phraseTableType = withGaps ? PhraseGeneratorFactory.DTU_GENERATOR
        : PhraseGeneratorFactory.PSEUDO_PHARAOH_GENERATOR; 
    Pair<PhraseGenerator<IString,String>,List<PhraseTable<IString>>> phraseTablePair = 
        PhraseGeneratorFactory.<String>factory(phraseTableType, phraseTable,
            makePair(PhraseGeneratorFactory.QUERY_LIMIT_OPTION, optionLimitString));
    phraseGenerator = phraseTablePair.first();
    
    // Load independent phrase tables that do not have associated lexicalized reordering models
    if (config.get(INDEPENDENT_PHRASE_TABLES) != null) {
       List<PhraseGenerator<IString,String>> generators = Generics.newLinkedList();
       generators.add(phraseGenerator);
       for (String filename : config.get(INDEPENDENT_PHRASE_TABLES)) {
         String[] fields = filename.split(":");
         String[] generatorOptions;
         if (fields.length == 1) {
           generatorOptions = new String[1];
           generatorOptions[0] = makePair(PhraseGeneratorFactory.QUERY_LIMIT_OPTION, optionLimitString);
         } else if (fields.length == 2) {
           generatorOptions = new String[2];
           generatorOptions[0] = makePair(PhraseGeneratorFactory.QUERY_LIMIT_OPTION, optionLimitString);
           generatorOptions[1] = makePair(PhraseGeneratorFactory.FEATURE_PREFIX_OPTION, fields[0]);
           filename = fields[1];
         } else {
           throw new RuntimeException("Invalid phrase table specification: " + filename);
         }
         System.err.printf("Loading independent phrase table: %s %s%n", filename, Arrays.toString(generatorOptions));
         Pair<PhraseGenerator<IString,String>,List<PhraseTable<IString>>> generatorPair =  
             PhraseGeneratorFactory.<String>factory(PhraseGeneratorFactory.PSEUDO_PHARAOH_GENERATOR, 
                 filename, generatorOptions); 
         generators.add(generatorPair.first());
       }
       phraseGenerator = new CombinedPhraseGenerator<IString,String>(generators, CombinedPhraseGenerator.Type.CONCATENATIVE, ruleQueryLimit);
    }

    // Add the OOV model
    if (config.containsKey(DROP_UNKNOWN_WORDS)) {
      dropUnknownWords = Boolean.parseBoolean(config.get(DROP_UNKNOWN_WORDS).get(0));
    }
    System.err.printf("Unknown words policy: %s%n", dropUnknownWords ? "Drop" : "Keep");
    phraseGenerator = new CombinedPhraseGenerator<IString,String>(
             Arrays.asList(phraseGenerator, new UnknownWordPhraseGenerator<IString, String>(dropUnknownWords)),
             CombinedPhraseGenerator.Type.STRICT_DOMINANCE, ruleQueryLimit);

    // Load the lexicalized reordering model(s) and associated featurizers
    List<DerivationFeaturizer<IString, String>> lexReorderFeaturizers = Generics.newLinkedList();
    if (config.containsKey(REORDERING_MODEL)) {
      List<PhraseTable<IString>> phraseTables = phraseTablePair.second();
      
      List<String> parameters = config.get(REORDERING_MODEL);
      if (parameters.size() < 3) {
        throw new RuntimeException(REORDERING_MODEL + " parameter requires at least three arguments");
      }
      String modelType = parameters.get(0);
      String[] modelFilenames = parameters.get(1).split(PhraseGeneratorFactory.SEPARATOR);
      String modelSpecification = parameters.get(2);
      parameters = parameters.subList(3, parameters.size());
      if (modelFilenames.length != phraseTables.size()) {
        // Constraint: each phrase table must have an associated lexicalized reordering model
        throw new RuntimeException("Each phrase table must have an associated reordering model: " + phraseTable +
            " ||| " + parameters.get(1));
      }
      
      for (int i = 0, sz = modelFilenames.length; i < sz; ++i) {
        String modelFilename = modelFilenames[i];
        
        if (modelType.equals("classic")) {
          LexicalReorderingTable lrt = new LexicalReorderingTable(modelFilename, phraseTables.get(i), modelSpecification);
          lexReorderFeaturizers.add(new LexicalReorderingFeaturizer(lrt));

        } else if (modelType.equals("hierarchical")) {
          ExtendedLexicalReorderingTable mlrt = new ExtendedLexicalReorderingTable(modelFilename, phraseTables.get(i), modelSpecification);
          lexReorderFeaturizers.add(new HierarchicalReorderingFeaturizer(mlrt, parameters));

        } else {
          throw new RuntimeException("Unsupported reordering model type: " + modelType);
        }
      }
    }

    List<Featurizer<IString, String>> additionalFeaturizers = Generics.newArrayList();
    if (config.containsKey(ADDITIONAL_FEATURIZERS)) {
      List<String> tokens = config.get(ADDITIONAL_FEATURIZERS);
      String featurizerName = null;
      String args = null;
      for (String token : tokens) {
        Featurizer<IString, String> featurizer = null;
        if (featurizerName == null) {
          if (token.endsWith("()")) {
            String name = token.replaceFirst("\\(\\)$", "");
            Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory
                .loadFeaturizer(name);
            featurizer = (Featurizer<IString, String>) featurizerClass
                .newInstance();
            additionalFeaturizers.add(featurizer);
          } else if (token.contains("(")) {
            if (token.endsWith(")")) {
              featurizerName = token.replaceFirst("\\(.*", "");
              args = token.replaceFirst("^.*\\(", "");
              args = args.substring(0, args.length() - 1);
              args = args.replaceAll("\\s*,\\s*", ",");
              args = args.replaceAll("^\\s+", "");
              args = args.replaceAll("\\s+$", "");
              String[] argsList = args.split(",");
              System.err.printf("Additional featurizer: %s.%nArgs: %s%n",
                  featurizerName, Arrays.toString(argsList));
              Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory
                  .loadFeaturizer(featurizerName);
              featurizer = (Featurizer<IString, String>) featurizerClass
                  .getConstructor(argsList.getClass()).newInstance(
                      new Object[] { argsList });
              additionalFeaturizers.add(featurizer);
              featurizerName = null;
              args = null;
            } else {
              featurizerName = token.replaceFirst("\\(.*", "");
              args = token.replaceFirst(".*\\(", "");
            }
          } else {
            System.err.printf(
                "Error: '(' expected immediately after feature name %s", token);
            System.err
                .printf("Note that no whitespace between '(' and the associated feature name is allowed%n");
            System.exit(-1);
          }
        } else {
          if (token.endsWith(")")) {
            args += " " + token.substring(0, token.length() - 1);
            args = args.replaceAll("\\s*,\\s*", ",");
            args = args.replaceAll("^\\s+", "");
            args = args.replaceAll("\\s+$", "");
            String[] argsList = args.split(",");
            System.err.printf("args: %s%n", Arrays.toString(argsList));
            Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory
                .loadFeaturizer(featurizerName);
            featurizer = (Featurizer<IString, String>) featurizerClass
                .getConstructor(argsList.getClass()).newInstance(
                    (Object) argsList);
            additionalFeaturizers.add(featurizer);
            featurizerName = null;
            args = null;
          } else {
            args += " " + token;
          }
        }
      }
      if (featurizerName != null) {
        System.err.printf("Error: no ')' found for featurizer %s%n",
            featurizerName);
        System.exit(-1);
      }
    }

    // Create feature extractor
    String lgModel = config.containsKey(LANGUAGE_MODEL_OPT) ?
        config.get(LANGUAGE_MODEL_OPT).get(0) : null;
    
    String featureAugmentationMode = config.containsKey(FEATURE_AUGMENTATION) ?
        config.get(FEATURE_AUGMENTATION).get(0) : null;

    final String linearDistortion = withGaps ? DTULinearDistortionFeaturizer.class.getName() 
        : LinearFutureCostFeaturizer.class.getName();
    FeatureExtractor<IString, String> featurizer;
    if (lgModel != null) {
      System.err.printf("Language model: %s%n", lgModel);
      featurizer = FeaturizerFactory.factory(
        FeaturizerFactory.MOSES_DENSE_FEATURES,
        makePair(FeaturizerFactory.LINEAR_DISTORTION_PARAMETER,
            linearDistortion),
        makePair(FeaturizerFactory.GAP_PARAMETER, gapType),
        makePair(FeaturizerFactory.ARPA_LM_PARAMETER, lgModel),
        makePair(FeaturizerFactory.NUM_PHRASE_FEATURES, String.valueOf(numPhraseFeatures)));
    } else {
      featurizer = FeaturizerFactory.factory(
          FeaturizerFactory.MOSES_DENSE_FEATURES,
          makePair(FeaturizerFactory.LINEAR_DISTORTION_PARAMETER,
              linearDistortion),
          makePair(FeaturizerFactory.GAP_PARAMETER, gapType),
          makePair(FeaturizerFactory.NUM_PHRASE_FEATURES, String.valueOf(numPhraseFeatures)));
    }

    if (config.containsKey(DISABLED_FEATURIZERS)) {
      Set<String> disabledFeaturizers = Generics.newHashSet(config.get(DISABLED_FEATURIZERS));
      featurizer.deleteFeaturizers(disabledFeaturizers);
    }

    additionalFeaturizers.addAll(lexReorderFeaturizers);

    if (!additionalFeaturizers.isEmpty()) {
      List<Featurizer<IString, String>> allFeaturizers = Generics.newArrayList();
      allFeaturizers.addAll(featurizer.getFeaturizers());
      allFeaturizers.addAll(additionalFeaturizers);
      if (featureAugmentationMode == null) {
        featurizer = new FeatureExtractor<IString, String>(allFeaturizers);
      } else {
        System.err.printf("Feature augmentation mode: %s%n", featureAugmentationMode);
        featurizer = new FeatureExtractor<IString, String>(allFeaturizers, featureAugmentationMode);        
      }
    }
    
    // Link the final featurizer and the phrase table
    phraseGenerator.setFeaturizer(featurizer);

    // Create Scorer / weight vector
    Counter<String> weightVector = new ClassicCounter<String>();

    if (config.containsKey(WEIGHTS_FILE)) {
      System.err.printf("Weights file: %s%n", config.get(WEIGHTS_FILE).get(0));
      weightVector = IOTools.readWeights(config.get(WEIGHTS_FILE).get(0));
    }

    if (config.containsKey(MAX_SENTENCE_LENGTH)) {
      try {
        maxSentenceSize = Integer.parseInt(config.get(MAX_SENTENCE_LENGTH).get(
            0));
        if (maxSentenceSize == 0)
          maxSentenceSize = Integer.MAX_VALUE;
      } catch (NumberFormatException e) {
        throw new RuntimeException(String.format(
            "Argument %s to %s can not be parsed as an integer",
            config.get(MAX_SENTENCE_LENGTH), MAX_SENTENCE_LENGTH));
      }
    }

    if (config.containsKey(MIN_SENTENCE_LENGTH)) {
      try {
        minSentenceSize = Integer.parseInt(config.get(MIN_SENTENCE_LENGTH).get(
            0));
      } catch (NumberFormatException e) {
        throw new RuntimeException(String.format(
            "Argument %s to %s can not be parsed as an integer",
            config.get(MIN_SENTENCE_LENGTH), MIN_SENTENCE_LENGTH));
      }
    }

    System.err.printf("WeightConfig: '%s' %s%n", Counters.toBiggestValuesFirstString(weightVector, 100), (weightVector.size() > 100 ? "..." : ""));


    // Create Recombination Filter
    RecombinationFilter<Derivation<IString, String>> filter = RecombinationFilterFactory
        .factory(recombinationMode, featurizer.getFeaturizers());

    // Create Search Heuristic
    RuleFeaturizer<IString, String> isolatedPhraseFeaturizer = featurizer;
    SearchHeuristic<IString, String> heuristic = HeuristicFactory.factory(
        isolatedPhraseFeaturizer,
        withGaps ? HeuristicFactory.ISOLATED_DTU_SOURCE_COVERAGE
            : HeuristicFactory.ISOLATED_PHRASE_SOURCE_COVERAGE);

    // Create Inferers and scorers
    inferers = Generics.newArrayList(numThreads);
    scorers = Generics.newArrayList(numThreads);

    boolean dtuDecoder = (gapT != FeaturizerFactory.GapType.none);

    String searchAlgorithm = config.containsKey(SEARCH_ALGORITHM) ?
      config.get(SEARCH_ALGORITHM).get(0).trim() : InfererBuilderFactory.DEFAULT_INFERER;
     
    if (dtuDecoder) {
      searchAlgorithm = InfererBuilderFactory.DTU_DECODER;
    }
    System.err.printf("Search algorithm: %s%n", searchAlgorithm);
    // Configure InfererBuilder
    AbstractBeamInfererBuilder<IString, String> infererBuilder = (AbstractBeamInfererBuilder<IString, String>) 
        InfererBuilderFactory.factory(searchAlgorithm);
    
    // Thang Apr14: cube pruning with NNLM reranking
    // TODO(spenceg): This should be loaded by reflection so that it isn't released
    // with the public version.
//    if (searchAlgorithm.equals(InfererBuilderFactory.CUBE_PRUNING_NNLM_DECODER)){ // CubePruningNNLM, load nnlmFile
//      String nnlmFile = config.get(SEARCH_ALGORITHM).get(1).trim();
//      String nnlmType = config.get(SEARCH_ALGORITHM).get(2).trim(); // joint or target
//      int cacheSize = Integer.parseInt(config.get(SEARCH_ALGORITHM).get(3).trim());
//      int miniBatchSize = Integer.parseInt(config.get(SEARCH_ALGORITHM).get(4).trim());
//      ((CubePruningNNLMDecoderBuilder<IString, String>) infererBuilder).loadNNLM(nnlmFile, nnlmType, cacheSize, miniBatchSize);
//    }
    
    for (int i = 0; i < numThreads; i++) {
      try {
        infererBuilder.setFilterUnknownWords(dropUnknownWords);
        infererBuilder.setIncrementalFeaturizer((FeatureExtractor<IString, String>) featurizer.clone());
        infererBuilder.setPhraseGenerator((PhraseGenerator<IString,String>) phraseGenerator.clone());
        Scorer<String> scorer = ScorerFactory.factory(ScorerFactory.SPARSE_SCORER, weightVector, null);
        infererBuilder.setScorer(scorer);
        scorers.add(scorer);
        infererBuilder.setSearchHeuristic((SearchHeuristic<IString, String>) heuristic.clone());
        infererBuilder.setRecombinationFilter((RecombinationFilter<Derivation<IString, String>>) filter.clone());
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }

      // Silently ignored by the cube pruning decoder
      infererBuilder.setBeamType(BeamFactory.BeamType.sloppybeam);

      if (distortionLimit != -1) {
        infererBuilder.setMaxDistortion(distortionLimit);
      }

      if (config.containsKey(USE_ITG_CONSTRAINTS)) {
        infererBuilder.useITGConstraints(Boolean.parseBoolean(config.get(
            USE_ITG_CONSTRAINTS).get(0)));
      }

      if (config.containsKey(BEAM_SIZE)) {
        try {
          int beamSize = Integer.parseInt(config.get(BEAM_SIZE).get(0));
          infererBuilder.setBeamCapacity(beamSize);
        } catch (NumberFormatException e) {
          throw new RuntimeException(
              String
                  .format(
                      "Beam size %s, as specified by argument %s, can not be parsed as an integer value%n",
                      config.get(BEAM_SIZE).get(0), BEAM_SIZE));
        }
      }
      inferers.add(infererBuilder.build());
    }
    System.err.printf("Inferer Count: %d%n", inferers.size());

    // determine if we need to generate n-best lists
    List<String> nbestOpt = config.get(NBEST_LIST_OPT);
    if (nbestOpt != null) {
      if (nbestOpt.size() == 1) {
        nbestListSize = Integer.parseInt(nbestOpt.get(0));
        assert nbestListSize >= 0;
        System.err.printf("Generating n-best lists (size: %d)%n",
            nbestListSize);

      } else if (nbestOpt.size() == 2 || nbestOpt.size() == 3) {
        String nbestListFilename = nbestOpt.get(0);
        nbestListSize = Integer.parseInt(nbestOpt.get(1));
        assert nbestListSize >= 0;
        
        if ( ! nbestListFilename.equals("default")) {
          nbestListWriter = IOTools.getWriterFromFile(nbestListFilename);
        }
        
        if (nbestOpt.size() == 3) {
          nbestListOutputType = nbestOpt.get(2);
        }
        
        System.err.printf("Generating n-best lists to: %s (size: %d)%n",
            nbestListFilename, nbestListSize);

      } else {
        throw new RuntimeException(
            String.format("%s requires 1, 2 or 3 arguments, not %d", NBEST_LIST_OPT,
                nbestOpt.size()));
      }

    } else {
      nbestListSize = -1;
      nbestListWriter = null;
    }
        
    // Determine if we need to generate an alignment file
    List<String> alignmentOpt = config.get(ALIGNMENT_OUTPUT_FILE);
    if (alignmentOpt != null && alignmentOpt.size() == 1) {
      alignmentWriter = IOTools.getWriterFromFile(alignmentOpt.get(0));
    }
  }

  private static String makePair(String label, String value) {
    return String.format("%s:%s", label, value);
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

    public DecoderInput(Sequence<IString> seq, int sourceInputId, List<Sequence<IString>> targets, InputProperties inputProps) {
      this.source = seq;
      this.sourceInputId = sourceInputId;
      this.inputProps = inputProps;
      this.targets = targets;
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

    public DecoderOutput(int sourceLength, List<RichTranslation<IString, String>> translations, Sequence<IString> bestTranslation, int sourceInputId) {
      this.sourceLength = sourceLength;
      this.translations = translations;
      this.bestTranslation = bestTranslation;
      this.sourceInputId = sourceInputId;
    }
  }

  /**
   * Wrapper class to submit this decoder instance to the thread pool.
   *
   * @author Spence Green
   *
   */
  private class PhrasalProcessor implements ThreadsafeProcessor<DecoderInput,DecoderOutput> {
    private final int infererId;
    private int childInfererId;

    /**
     * Constructor.
     *
     * @param parentInfererId - the bast infererId for this instance. Calls to newInstance()
     * will increment from this value.
     */
    public PhrasalProcessor(int parentInfererId) {
      this.infererId = parentInfererId;
      this.childInfererId = parentInfererId+1;
    }

    @Override
    public DecoderOutput process(DecoderInput input) {
      // Generate n-best list
      List<RichTranslation<IString, String>> translations = 
          decode(input.source, input.sourceInputId, infererId, nbestListSize, input.targets, input.inputProps);
      
     
      // Select and process the best translation
      Sequence<IString> bestTranslation = null;
      if (translations.size() > 0) {
        bestTranslation = translations.get(0).translation;
        if (postprocessor != null) {
          try {
            bestTranslation = postprocessor.process(bestTranslation).e();
          } catch (Exception e) {
            // The postprocessor exploded. Silently ignore and return
            // the unprocessed translation.
            bestTranslation = translations.get(0).translation;
          }
        }
        if (wrapBoundary) {
          bestTranslation = bestTranslation.subsequence(1, bestTranslation.size() - 1);
        }
        
        
      }
        
      return new DecoderOutput(input.source.size(), translations, bestTranslation, input.sourceInputId);
    }

    @Override
    public ThreadsafeProcessor<DecoderInput, DecoderOutput> newInstance() {
      return new PhrasalProcessor(childInfererId++);
    }
  }

  /**
   * Output the result of decodeFromConsole(), and write to the n-best list
   * if necessary.
   *
   * NOTE: This call is *not* threadsafe.
   *
   * @param translations n-best list
   * @param bestTranslation if post-processing has been applied, then this is post-processed
   *        sequence at the top of the n-best list
   * @param sourceInputId
   */
  private void processConsoleResult(List<RichTranslation<IString, String>> translations,
      Sequence<IString> bestTranslation, int sourceLength, int sourceInputId) {
    if (translations.size() > 0) {
      RichTranslation<IString,String> bestTranslationInfo = translations.get(0);
      if (printModelScores) {
        System.out.printf("%e\t%s%n", bestTranslationInfo.score, bestTranslation.toString());
      } else {
        System.out.println(bestTranslation.toString());
      }
      
      // log additional information to stderr
      System.err.printf("input %d: 1-best model score: %.3f%n", sourceInputId, bestTranslationInfo.score);

      // Output the n-best list if necessary
      if (nbestListWriter != null) {
        IOTools.writeNbest(translations, sourceInputId, nbestListOutputType, nbestListWriter);
      }
      
      // Output the alignments if necessary
      if (alignmentWriter != null) {
        for (RichTranslation<IString,String> translation : translations) {
          alignmentWriter.printf("%d %s %s%n", sourceInputId, FlatPhraseTable.FIELD_DELIM, 
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
        alignmentWriter.printf("%n");
      }
      
      System.err.printf("<<< decoder failure for id: %d >>>%n", sourceInputId);
    }
  }

  /**
   * Decode input from inputStream and either write 1-best translations to stdout or
   * return them in a <code>List</code>.
   * 
   * @param inputStream 
   * @param outputToConsole if true, output the 1-best translations to the console. Otherwise,
   *                        return them in a <code>List</code>
   * @throws IOException
   */
  public List<RichTranslation<IString,String>> decode(InputStream inputStream, boolean outputToConsole) throws IOException {
    System.err.println("Entering main translation loop");
    final MulticoreWrapper<DecoderInput,DecoderOutput> wrapper =
        new MulticoreWrapper<DecoderInput,DecoderOutput>(numThreads, new PhrasalProcessor(0));
    final LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        inputStream, "UTF-8"));
    final List<RichTranslation<IString,String>> bestTranslationList = outputToConsole ? null :
      new ArrayList<RichTranslation<IString,String>>();
    
    final long startTime = System.nanoTime();
    int sourceInputId = 0;
    for (String line; (line = reader.readLine()) != null; ++sourceInputId) {
      Sequence<IString> source = preprocessor == null ? IStrings.tokenize(line) :
        preprocessor.process(line.trim());
            
      if (source.size() > maxSentenceSize || source.size() < minSentenceSize) {
        System.err.printf("Skipping: %s%n", line);
        System.err.printf("Tokens: %d (min: %d max: %d)%n", source.size(), minSentenceSize,
            maxSentenceSize);
        continue;
      }

      final InputProperties inputProps = inputPropertiesList != null && sourceInputId < inputPropertiesList.size() ? 
          inputPropertiesList.get(sourceInputId) : new InputProperties();
      final List<Sequence<IString>> targets = 
          forceDecodeReferences == null ? null : forceDecodeReferences.get(sourceInputId);
      
      wrapper.put(new DecoderInput(source, sourceInputId, targets, inputProps));
      for (DecoderOutput result; (result = wrapper.poll()) != null;) {
        if (outputToConsole) {
          processConsoleResult(result.translations, result.bestTranslation, result.sourceLength, result.sourceInputId);
        } else {
          RichTranslation<IString,String> best = result.translations.size() > 0 ? result.translations.get(0) : null;
          bestTranslationList.add(best);
        }
      }
    }

    // Finished reading the input. Wait for threadpool to finish, then process
    // last few translations.
    wrapper.join();
    while(wrapper.peek()) {
      DecoderOutput result = wrapper.poll();
      if (outputToConsole) {
        processConsoleResult(result.translations, result.bestTranslation, result.sourceLength, result.sourceInputId);
      } else {
        RichTranslation<IString,String> best = result.translations.size() > 0 ? result.translations.get(0) : null;
        bestTranslationList.add(best);
      }
    }

    double totalTime = ((double) System.nanoTime() - startTime) / 1e9;
    double segmentsPerSec = (double) sourceInputId / totalTime;
    System.err.printf("Decoding at %.2f segments/sec (total: %.2f sec)%n", segmentsPerSec, totalTime);
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
   * @param threadId -- Inferer object to use (one per thread)
   */
  public List<RichTranslation<IString, String>> decode(Sequence<IString> source,
      int sourceInputId, int threadId) {
    final InputProperties inputProps = inputPropertiesList != null && sourceInputId < inputPropertiesList.size() ? 
        inputPropertiesList.get(sourceInputId) : new InputProperties();
    List<Sequence<IString>> targets = 
        forceDecodeReferences == null ? null : forceDecodeReferences.get(sourceInputId);
    return decode(source, sourceInputId, threadId, nbestListSize, targets, inputProps);
  }
  
  /**
   * Decode a tokenized input string. Returns an n-best list of translations
   * specified by the parameter.
   *
   * NOTE: This call is threadsafe.
   *
   * @param source
   * @param sourceInputId
   * @param threadId -- Inferer object to use (one per thread)
   * @param numTranslations number of translations to generate
   * 
   */
  public List<RichTranslation<IString, String>> decode(Sequence<IString> source,
      int sourceInputId, int threadId, int numTranslations, List<Sequence<IString>> targets, 
      InputProperties inputProperties) {
    // Sanity checks
    
    if (wrapBoundary)
      source = Sequences.wrapStartEnd(source, TokenUtils.START_TOKEN, TokenUtils.END_TOKEN);
    
    if (threadId < 0 || threadId >= numThreads) {
      throw new IndexOutOfBoundsException("Thread id out of bounds: " + String.valueOf(threadId));
    }
    if (sourceInputId < 0) {
      throw new IndexOutOfBoundsException("Source id must be non-negative: " + String.valueOf(sourceInputId));
    }

    // Output space of the decoder
    final boolean targetsArePrefixes = inputProperties.containsKey(InputProperty.TargetPrefix) ? 
        (Boolean) inputProperties.get(InputProperty.TargetPrefix) : false;
    OutputSpace<IString, String> outputSpace = OutputSpaceFactory.getOutputSpace(sourceInputId, 
        targets, targetsArePrefixes, phraseGenerator.longestSourcePhrase(), phraseGenerator.longestTargetPhrase(),
        wrapBoundary);

    List<RichTranslation<IString, String>> translations = Generics.newArrayList(1);
    if (numTranslations > 1) {
      translations = inferers.get(threadId).nbest(source, sourceInputId, inputProperties, 
          outputSpace, outputSpace.getAllowableSequences(), numTranslations);

      // Return an empty n-best list
      if (translations == null) translations = Generics.newArrayList(1);

    } else {
      // The 1-best translation in this case is potentially different from
      // calling nbest() with a list size of 1. Therefore, this call is *not* a special
      // case of the condition above.
      RichTranslation<IString, String> translation = 
          inferers.get(threadId).translate(source, sourceInputId, inputProperties, outputSpace, 
              outputSpace.getAllowableSequences());
      if (translation != null) {
        translations.add(translation);
      }
    }
    return translations;
  }

  /**
   * Free resources and cleanup.
   */
  private void shutdown() {
    if (nbestListWriter != null) {
      System.err.println("Closing n-best writer");
      nbestListWriter.close();
    }
    
    if (alignmentWriter != null) {
      System.err.println("Closing alignment writer");
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
  private static Map<String, List<String>> getConfigurationFrom(String configFile, Properties options) throws IOException {
    Map<String, List<String>> config = configFile == null ? new HashMap<String,List<String>>() :
      IOTools.readConfigFile(configFile);
    // Command-line options supercede config file options
    for (Map.Entry<Object, Object> e : options.entrySet()) {
      String key = e.getKey().toString();
      String value = e.getValue().toString();
      config.put(key, Arrays.asList(value.split("\\s+")));
    }
    return config;
  }

  /**
   * Load an instance of Phrasal from an ini file.
   *
   * @param phrasalIniFile
   * @throws IOException
   */
  public static Phrasal loadDecoder(String phrasalIniFile) throws IOException {
    Map<String, List<String>> config = IOTools.readConfigFile(phrasalIniFile);
    return loadDecoder(config);
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
    throw new RuntimeException("Could not load Phrasal from config file!");
  }
  
  /**
   * Run Phrasal from the command line.
   *
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    Properties options = StringUtils.argsToProperties(args);
    String configFile = options.containsKey("") ? (String) options.get("") : null;
    options.remove("");
    if ((options.size() == 0 && configFile == null) ||
        options.containsKey("help") || options.containsKey("h")) {
      System.err.println(usage());
      System.exit(-1);
    }

    // by default, exit on uncaught exception
    Thread
        .setDefaultUncaughtExceptionHandler((t, ex) -> {
          System.err.println("Uncaught exception from thread: " + t.getName());
          ex.printStackTrace();
          System.exit(-1);
        });

    Map<String, List<String>> configuration = getConfigurationFrom(configFile, options);
    Phrasal p = Phrasal.loadDecoder(configuration);
    p.decode(System.in, true);
//    p.decode(new FileInputStream(new File("mt05.prep")), true);
  }
}
