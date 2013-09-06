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

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.decoder.h.*;
import edu.stanford.nlp.mt.decoder.inferer.*;
import edu.stanford.nlp.mt.decoder.inferer.impl.DTUDecoder;
import edu.stanford.nlp.mt.decoder.recomb.*;
import edu.stanford.nlp.mt.decoder.util.*;
import edu.stanford.nlp.mt.metrics.*;
import edu.stanford.nlp.mt.process.Postprocessor;
import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.ProcessorFactory;
import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.annotators.AnnotatorFactory;
import edu.stanford.nlp.mt.decoder.feat.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * Phrasal: a phrase-based machine translation system.
 *
 * @author danielcer
 *
 */
public class Phrasal {

  public static final String TRANSLATION_TABLE_OPT = "ttable-file";
  public static final String LANGUAGE_MODEL_OPT = "lmodel-file";
  public static final String OPTION_LIMIT_OPT = "ttable-limit";
  public static final String DISTORTION_WT_OPT = "weight-d";
  public static final String LANGUAGE_MODEL_WT_OPT = "weight-l";
  public static final String TRANSLATION_MODEL_WT_OPT = "weight-t";
  public static final String WORD_PENALTY_WT_OPT = "weight-w";
  public static final String INPUT_FACTORS_OPT = "input-factors";
  public static final String FACTOR_DELIM_OPT = "factor-delimiter";
  public static final String MAPPING_OPT = "mapping";
  public static final String NBEST_LIST_OPT = "n-best-list";
  public static final String MOSES_NBEST_LIST_OPT = "moses-n-best-list";
  public static final String DISTINCT_NBEST_LIST_OPT = "distinct-n-best-list";
  public static final String FORCE_DECODE = "force-decode";
  public static final String BEAM_SIZE = "stack";
  public static final String SEARCH_ALGORITHM = "search-algorithm";
  public static final String DISTORTION_FILE = "distortion-file";
  public static final String HIER_DISTORTION_FILE = "hierarchical-distortion-file";
  public static final String WEIGHTS_FILE = "weights-file";
  public static final String CONFIG_FILE = "config-file";
  public static final String USE_DISCRIMINATIVE_LM = "discriminative-lm";
  public static final String USE_DISCRIMINATIVE_TM = "discriminative-tm";
  public static final String MAX_SENTENCE_LENGTH = "max-sentence-length";
  public static final String MIN_SENTENCE_LENGTH = "min-sentence-length";
  public static final String FORCE_DECODE_ONLY = "force-decode-only";
  public static final String DISTORTION_LIMIT = "distortion-limit";
  public static final String ADDITIONAL_FEATURIZERS = "additional-featurizers";
  public static final String ADDITIONAL_ANNOTATORS = "additional-annotators";
  public static final String DISABLED_FEATURIZERS = "disabled-featurizers";
  public static final String INLINE_WEIGHTS = "inline-weights";
  public static final String LOCAL_PROCS = "localprocs";
  public static final String ITER_LIMIT = "iter-limit";
  public static final String USE_ITG_CONSTRAINTS = "use-itg-constraints";
  public static final String RECOMBINATION_HEURISTIC = "recombination-heuristic";
  public static final String GAPS_OPT = "gaps";
  public static final String MAX_GAP_SPAN_OPT = "max-gap-span";
  public static final String MAX_PENDING_PHRASES_OPT = "max-pending-phrases";
  public static final String GAPS_IN_FUTURE_COST_OPT = "gaps-in-future-cost";
  public static final String ISTRING_VOC_OPT = "istring-vocabulary";
  public static final String MOSES_COMPATIBILITY_OPT = "moses-compatibility";
  public static final String LINEAR_DISTORTION_TYPE = "linear-distortion-type";
  public static final String DROP_UNKNOWN_WORDS = "drop-unknown-words";
  public static final String ADDITIONAL_PHRASE_GENERATOR = "additional-phrase-generator";
  public static final String ALIGNMENT_OUTPUT_FILE = "alignment-output-file";
  public static final String PREPROCESSOR_FILTER = "preprocessor-filter";
  public static final String POSTPROCESSOR_FILTER = "postprocessor-filter";
  public static final String SOURCE_CLASS_MAP = "source-class-map";
  public static final String TARGET_CLASS_MAP = "target-class-map";
  

  private static final int DEFAULT_DISCRIMINATIVE_LM_ORDER = 0;
  private static final boolean DEFAULT_DISCRIMINATIVE_TM_PARAMETER = false;

  static final Set<String> REQUIRED_FIELDS = new HashSet<String>();
  static final Set<String> OPTIONAL_FIELDS = new HashSet<String>();
  static final Set<String> IGNORED_FIELDS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_FIELDS = new HashSet<String>();

  static {
    REQUIRED_FIELDS.addAll(Arrays.asList(TRANSLATION_TABLE_OPT,WEIGHTS_FILE));
    OPTIONAL_FIELDS.addAll(Arrays.asList(INLINE_WEIGHTS, ITER_LIMIT,
        DISTORTION_FILE, DISTORTION_LIMIT, ADDITIONAL_FEATURIZERS,
        DISABLED_FEATURIZERS, USE_DISCRIMINATIVE_TM, FORCE_DECODE_ONLY,
        OPTION_LIMIT_OPT, NBEST_LIST_OPT, MOSES_NBEST_LIST_OPT,
        DISTINCT_NBEST_LIST_OPT, FORCE_DECODE,
        RECOMBINATION_HEURISTIC, HIER_DISTORTION_FILE, SEARCH_ALGORITHM,
        BEAM_SIZE, WEIGHTS_FILE, USE_DISCRIMINATIVE_LM, MAX_SENTENCE_LENGTH,
        MIN_SENTENCE_LENGTH, USE_ITG_CONSTRAINTS,
        LOCAL_PROCS, GAPS_OPT, GAPS_IN_FUTURE_COST_OPT, MAX_GAP_SPAN_OPT,
        LINEAR_DISTORTION_TYPE, MAX_PENDING_PHRASES_OPT, ISTRING_VOC_OPT,
        MOSES_COMPATIBILITY_OPT, ADDITIONAL_ANNOTATORS, DROP_UNKNOWN_WORDS, ADDITIONAL_PHRASE_GENERATOR,
        LANGUAGE_MODEL_OPT, DISTORTION_WT_OPT, LANGUAGE_MODEL_WT_OPT,
        TRANSLATION_MODEL_WT_OPT, WORD_PENALTY_WT_OPT, 
        ALIGNMENT_OUTPUT_FILE, PREPROCESSOR_FILTER, POSTPROCESSOR_FILTER,
        SOURCE_CLASS_MAP,TARGET_CLASS_MAP));
    IGNORED_FIELDS.addAll(Arrays.asList(INPUT_FACTORS_OPT, MAPPING_OPT,
        FACTOR_DELIM_OPT));
    ALL_RECOGNIZED_FIELDS.addAll(REQUIRED_FIELDS);
    ALL_RECOGNIZED_FIELDS.addAll(OPTIONAL_FIELDS);
    ALL_RECOGNIZED_FIELDS.addAll(IGNORED_FIELDS);
  }

  /**
   * Number of decoding threads. Setting this parameter to 0 enables
   * multithreading inside the main decoding loop. Generally, it is better
   * to set the desired number of threads here (i.e., set this parameter >= 1).
   */
  private static final int DEFAULT_NUM_THREADS = 1;
  private int numThreads = DEFAULT_NUM_THREADS;

  /**
   * Hard distortion limit for phrase-based decoder
   */
  private static final int DEFAULT_DISTORTION_LIMIT = 5;
  private int distortionLimit = DEFAULT_DISTORTION_LIMIT;

  /**
   * DTU options
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
   * Phrase table type
   */
  private PhraseGenerator<IString,String> phraseGenerator;

  /**
   * Whether to filter unknown words in the output
   */
  private static final boolean DROP_UNKNOWN_WORDS_DEFAULT = true;
  private boolean dropUnknownWords = DROP_UNKNOWN_WORDS_DEFAULT;

  /**
   * n-best list options
   */
  private boolean generateMosesNBestList = true;
  private PrintStream nbestListWriter;
  private int nbestListSize;
  private boolean nbestWordInternalAlignments = false;
  
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
   * Recombination configuration.
   */
  private static String recombinationHeuristic = RecombinationFilterFactory.CLASSICAL_TRANSLATION_MODEL;

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
    assert threadId >= 0 && threadId < numThreads;
    return scorers.get(threadId);
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
  
  public static void initStaticMembers(Map<String, List<String>> config) {

    if (config.containsKey(ISTRING_VOC_OPT))
      IString.load(config.get(ISTRING_VOC_OPT).get(0));

    withGaps = config.containsKey(GAPS_OPT)
        || config.containsKey(MAX_GAP_SPAN_OPT);
    if (withGaps)
      gapOpts = config.containsKey(MAX_GAP_SPAN_OPT) ? config
          .get(MAX_GAP_SPAN_OPT) : config.get(GAPS_OPT);
    FlatPhraseTable.createIndex(withGaps);
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

    if (withGaps)
      recombinationHeuristic = RecombinationFilterFactory.DTU_TRANSLATION_MODEL;
  }

  @SuppressWarnings("unchecked")
  public Phrasal(Map<String, List<String>> config) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException,
      SecurityException, InvocationTargetException, NoSuchMethodException,
      ClassNotFoundException {
    if (!config.keySet().containsAll(REQUIRED_FIELDS)) {
      Set<String> missingFields = new HashSet<String>(REQUIRED_FIELDS);
      missingFields.removeAll(config.keySet());
      throw new RuntimeException(String.format(
          "The following required fields are missing: %s\n", missingFields));
    }

    if (!ALL_RECOGNIZED_FIELDS.containsAll(config.keySet())) {
      Set<String> extraFields = new HashSet<String>(config.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_FIELDS);
      throw new RuntimeException(String.format(
          "The following fields are unrecognized: %s\n", extraFields));
    }

    Set<String> ignoredItems = new HashSet<String>(config.keySet());
    ignoredItems.retainAll(IGNORED_FIELDS);

    for (String ignored : ignoredItems) {
      System.err.printf("Ignoring Moses field: %s\n", ignored);
    }

    if (config.containsKey(RECOMBINATION_HEURISTIC)) {
      recombinationHeuristic = config.get(RECOMBINATION_HEURISTIC).get(0);
    }

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
    
    // Word class maps
    if (config.containsKey(SOURCE_CLASS_MAP)) {
      List<String> parameters = config.get(SOURCE_CLASS_MAP);
      if (parameters.size() == 0) throw new RuntimeException("Source class map requires a file argument");
      SourceClassMap.load(parameters.get(0));
    }
    if (config.containsKey(TARGET_CLASS_MAP)) {
      List<String> parameters = config.get(TARGET_CLASS_MAP);
      if (parameters.size() == 0) throw new RuntimeException("Target class map requires a file argument");
      TargetClassMap.load(parameters.get(0));
    }
    
    final boolean mosesMode = config.containsKey(MOSES_COMPATIBILITY_OPT);

    if (config.containsKey(FORCE_DECODE)) {
      forceDecodeReferences = Metrics.readReferences(config.get(FORCE_DECODE)
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
                    "Argument '%s' to parameter '%s' can not be parsed as an integer value\n",
                    strDistortionLimit.get(0), DISTORTION_LIMIT));
      }
    }
    
    // DTU decoding
    FeaturizerFactory.GapType gapT = !withGaps ? FeaturizerFactory.GapType.none
        : ((gapOpts.size() > 1) ? FeaturizerFactory.GapType.both
            : FeaturizerFactory.GapType.source);
    String gapType = gapT.name();
    System.err.println("Gap type: " + gapType);

    // Phrase table(s)
    String phraseTable;
    if (config.get(TRANSLATION_TABLE_OPT).size() == 1) {
      phraseTable = config.get(TRANSLATION_TABLE_OPT).get(0);
    } else if (config.get(TRANSLATION_TABLE_OPT).size() == 4) {
      List<String> ptOpts = config.get(TRANSLATION_TABLE_OPT);
      System.err
          .printf(
              "Ignoring Moses factor & phrase feature count information: %s, %s, %s\n",
              ptOpts.get(0), ptOpts.get(1), ptOpts.get(2));
      phraseTable = ptOpts.get(3);
    } else {
      throw new RuntimeException("Unsupported configuration "
          + config.get(TRANSLATION_TABLE_OPT));
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


    if (config.containsKey(DROP_UNKNOWN_WORDS)) {
      dropUnknownWords = Boolean.parseBoolean(config.get(DROP_UNKNOWN_WORDS).get(0));
    }

    String optionLimit = config.get(OPTION_LIMIT_OPT).get(0);
    System.err.printf("Phrase table: %s Unknown words policy: %s\n", phraseTable, (dropUnknownWords ? "Drop" : "Keep"));

    if (phraseTable.startsWith("bitext:")) {
      phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory.<String>factory(
          false, PhraseGeneratorFactory.NEW_DYNAMIC_GENERATOR, phraseTable) : PhraseGeneratorFactory.<String>factory(false, PhraseGeneratorFactory.NEW_DYNAMIC_GENERATOR,
          phraseTable.replaceFirst("^bitext:", ""),
          optionLimit));
    } else if (phraseTable.endsWith(".db") || phraseTable.contains(".db:")) {

      System.err.println("Dyanamic pt\n========================");
      phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory.<String>factory(
          false, PhraseGeneratorFactory.DYNAMIC_GENERATOR, phraseTable) : PhraseGeneratorFactory.<String>factory(false, PhraseGeneratorFactory.DYNAMIC_GENERATOR,
          phraseTable, optionLimit));
    } else {
      String generatorName = withGaps ? PhraseGeneratorFactory.DTU_GENERATOR
          : PhraseGeneratorFactory.PSEUDO_PHARAOH_GENERATOR;
      phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory.<String>factory(
          false, generatorName, phraseTable)
          : PhraseGeneratorFactory.<String>factory(false, generatorName, phraseTable,
              optionLimit));
    }
    if (config.get(ADDITIONAL_PHRASE_GENERATOR) != null) {
       List<PhraseGenerator<IString,String>> pgens = new LinkedList<PhraseGenerator<IString,String>>();
       pgens.add(phraseGenerator);
       for (String pgenClasspath : config.get(ADDITIONAL_PHRASE_GENERATOR)) {
          PhraseGenerator<IString,String> pgen;
          try {
             pgen = (PhraseGenerator<IString,String>)Class.forName(pgenClasspath).
                getConstructor().newInstance();
          } catch (ClassNotFoundException e) {
             throw new RuntimeException("Invalid PhraseGenerator: "+pgenClasspath);
          }
          pgens.add(pgen);
       }
       phraseGenerator = new CombinedPhraseGenerator<IString,String>(pgens, CombinedPhraseGenerator.Type.CONCATENATIVE, Integer.parseInt(optionLimit));
    }

    // TODO(spenceg) For better or worse, all phrase tables use the source index in FlatPhraseTable
    // Pass it to the UnknownWord generator. Would be better for the source index to be located
    // in a common place.
    phraseGenerator = new CombinedPhraseGenerator<IString,String>(
             Arrays.asList(phraseGenerator, new UnknownWordPhraseGenerator<IString, String>(dropUnknownWords, FlatPhraseTable.foreignIndex)),
             CombinedPhraseGenerator.Type.STRICT_DOMINANCE, Integer.parseInt(optionLimit));
    
    FlatPhraseTable.lockIndex();

    System.err.printf("Phrase table limit (ttable-limit): %d%n",
        ((CombinedPhraseGenerator<IString,String>) phraseGenerator).getPhraseLimit());

    // Lexicalized reordering model
    NeedsReorderingRecombination<IString, String> lexReorderFeaturizer = null;

    boolean msdRecombination = false;
    if (config.containsKey(DISTORTION_FILE)
        || config.containsKey(HIER_DISTORTION_FILE)) {
      if (config.containsKey(DISTORTION_FILE)
          && config.containsKey(HIER_DISTORTION_FILE))
        throw new UnsupportedOperationException(
            "Two distortion files instead of one. "
                + "To use more than one, please use " + ADDITIONAL_FEATURIZERS
                + " field.");
      boolean stdDistFile = config.containsKey(DISTORTION_FILE);
      msdRecombination = true;
      List<String> strDistortionFile = stdDistFile ? config
          .get(DISTORTION_FILE) : config.get(HIER_DISTORTION_FILE);
      String modelType;
      String modelFilename;
      if (strDistortionFile.size() == 2) {
        modelType = strDistortionFile.get(0);
        modelFilename = strDistortionFile.get(1);

      } else if (strDistortionFile.size() == 4) {
        modelType = strDistortionFile.get(1);
        modelFilename = strDistortionFile.get(3);
      } else {
        throw new RuntimeException(
            String
                .format(
                    "Parameter '%s' takes two arguments: distortion-model-type & model-filename)",
                    DISTORTION_FILE));
      }
      lexReorderFeaturizer = mosesMode || stdDistFile ? new LexicalReorderingFeaturizer(
          new LexicalReorderingTable(modelFilename, modelType))
          : new HierarchicalReorderingFeaturizer(modelFilename, modelType);
    }
    int discriminativeLMOrder;
    if (config.containsKey(USE_DISCRIMINATIVE_LM)) {
      String orderStr = config.get(USE_DISCRIMINATIVE_LM).get(0);
      try {
        discriminativeLMOrder = Integer.parseInt(orderStr);
      } catch (NumberFormatException e) {
        throw new RuntimeException(String.format(
            "Parameter %s to %s can not be parsed as an integer value",
            orderStr, USE_DISCRIMINATIVE_LM));
      }
    } else {
      discriminativeLMOrder = DEFAULT_DISCRIMINATIVE_LM_ORDER;
    }

    List<Annotator<IString,String>> additionalAnnotators = new ArrayList<Annotator<IString,String>>();
    if (config.containsKey(ADDITIONAL_ANNOTATORS)) {
    	// todo make some general method that can parse both additional annotators
    	// and additional featurizers
    	List<String> tokens = config.get(ADDITIONAL_ANNOTATORS);
        String annotatorName = null;
        String args = null;
        for (String token : tokens) {
          Annotator<IString,String> annotator = null;
          if (annotatorName == null) {
            if (token.endsWith("()")) {
              String name = token.replaceFirst("\\(\\)$", "");
              Class<Annotator<IString,String>> annotatorClass = AnnotatorFactory
                  .loadAnnotator(name);
              annotator = (Annotator<IString,String>) annotatorClass
                  .newInstance();
              additionalAnnotators.add(annotator);
            } else if (token.contains("(")) {
              if (token.endsWith(")")) {
                annotatorName = token.replaceFirst("\\(.*", "");
                args = token.replaceFirst("^.*\\(", "");
                args = args.substring(0, args.length() - 1);
                args = args.replaceAll("\\s*,\\s*", ",");
                args = args.replaceAll("^\\s+", "");
                args = args.replaceAll("\\s+$", "");
                String[] argsList = args.split(",");
                System.err.printf("Additional annotators: %s.\nArgs: %s\n",
                    annotatorName, Arrays.toString(argsList));
                // TODO(spenceg) Seems like this will throw an exception?
                Class<Featurizer<IString, String>> featurizerClass = FeaturizerFactory
                    .loadFeaturizer(annotatorName);
                annotator = (Annotator<IString,String>) featurizerClass
                    .getConstructor(argsList.getClass()).newInstance(
                        new Object[] { argsList });
                additionalAnnotators.add(annotator);
                annotatorName = null;
                args = null;
              } else {
                annotatorName = token.replaceFirst("\\(.*", "");
                args = token.replaceFirst(".*\\(", "");
              }
            } else {
              System.err.printf(
                  "Error: '(' expected immediately after annotator name %s", token);
              System.err
                  .printf("Note that no whitespace between '(' and the associated annotator name is allowed\n");
              System.exit(-1);
            }
          } else {
            if (token.endsWith(")")) {
              args += " " + token.substring(0, token.length() - 1);
              args = args.replaceAll("\\s*,\\s*", ",");
              args = args.replaceAll("^\\s+", "");
              args = args.replaceAll("\\s+$", "");
              String[] argsList = args.split(",");
              System.err.printf("args: %s\n", Arrays.toString(argsList));
              Class<Annotator<IString,String>> annotatorClass = AnnotatorFactory
                  .loadAnnotator(annotatorName);
              annotator = (Annotator<IString,String>) annotatorClass
                  .getConstructor(argsList.getClass()).newInstance(
                      (Object) argsList);
              additionalAnnotators.add(annotator);
              annotatorName = null;
              args = null;
            } else {
              args += " " + token;
            }
          }
        }
        if (annotatorName != null) {
          System.err.printf("Error: no ')' found for annotator %s\n",
              annotatorName);
          System.exit(-1);
        }
    }
    System.err.printf("Number of additional annotators loaded: %d\n", additionalAnnotators.size());

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
              System.err.printf("Additional featurizer: %s.\nArgs: %s\n",
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
                .printf("Note that no whitespace between '(' and the associated feature name is allowed\n");
            System.exit(-1);
          }
        } else {
          if (token.endsWith(")")) {
            args += " " + token.substring(0, token.length() - 1);
            args = args.replaceAll("\\s*,\\s*", ",");
            args = args.replaceAll("^\\s+", "");
            args = args.replaceAll("\\s+$", "");
            String[] argsList = args.split(",");
            System.err.printf("args: %s\n", Arrays.toString(argsList));
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
        if (featurizer instanceof NeedsInternalAlignments)
          Featurizable.enableAlignments();
        if (featurizer instanceof NeedsReorderingRecombination)
          msdRecombination = true;
      }
      if (featurizerName != null) {
        System.err.printf("Error: no ')' found for featurizer %s\n",
            featurizerName);
        System.exit(-1);
      }
    }

    boolean discriminativeTMParameter;
    if (config.containsKey(USE_DISCRIMINATIVE_TM)) {
      discriminativeTMParameter = Boolean.parseBoolean(config.get(
          USE_DISCRIMINATIVE_TM).get(0));
    } else {
      discriminativeTMParameter = DEFAULT_DISCRIMINATIVE_TM_PARAMETER;
    }

    // Create Featurizer
    String lgModel = null, lgModelVoc = "";
    if (config.containsKey(LANGUAGE_MODEL_OPT)) {
      if (config.get(LANGUAGE_MODEL_OPT).size() == 1) {
        lgModel = config.get(LANGUAGE_MODEL_OPT).get(0);
      } else if (config.get(LANGUAGE_MODEL_OPT).size() == 2) {
        lgModel = config.get(LANGUAGE_MODEL_OPT).get(0);
        lgModelVoc = config.get(LANGUAGE_MODEL_OPT).get(1);
      } else if (config.get(LANGUAGE_MODEL_OPT).size() == 4) {
        List<String> lmOpts = config.get(LANGUAGE_MODEL_OPT);
        System.err.printf(
            "Ignoring Moses factor & model order information: %s, %s, %s\n",
            lmOpts.get(0), lmOpts.get(1), lmOpts.get(2));
        lgModel = lmOpts.get(3);
      } else {
        throw new RuntimeException("Unsupported configuration "
            + config.get(LANGUAGE_MODEL_OPT));
      }

      System.err.printf("Language model: %s\n", lgModel);
    }

    if (discriminativeLMOrder != 0) {
      System.err.printf("Discriminative LM order: %d\n", discriminativeLMOrder);
    }

    CombinedFeaturizer<IString, String> featurizer;

    if (discriminativeTMParameter) {
      System.err.printf("Using Discriminative TM\n");
    }

    String linearDistortion = withGaps ? DTULinearDistortionFeaturizer.class
        .getName() : (mosesMode ? LinearDistortionFeaturizer.class.getName()
        : LinearFutureCostFeaturizer.class.getName());

    if (lgModel != null) {
      featurizer = FeaturizerFactory.factory(
        FeaturizerFactory.PSEUDO_PHARAOH_GENERATOR,
        makePair(FeaturizerFactory.LINEAR_DISTORTION_PARAMETER,
            linearDistortion),
        makePair(FeaturizerFactory.GAP_PARAMETER, gapType),
        makePair(FeaturizerFactory.ARPA_LM_PARAMETER, lgModel),
        makePair(FeaturizerFactory.ARPA_LM_VOC_PARAMETER, lgModelVoc));
    } else {
      featurizer = FeaturizerFactory.factory(
          FeaturizerFactory.PSEUDO_PHARAOH_GENERATOR,
          makePair(FeaturizerFactory.LINEAR_DISTORTION_PARAMETER,
              linearDistortion),
          makePair(FeaturizerFactory.GAP_PARAMETER, gapType));
    }

    if (config.containsKey(DISABLED_FEATURIZERS)) {
      Set<String> disabledFeaturizers = new HashSet<String>();
      for (String f : config.get(DISABLED_FEATURIZERS))
        disabledFeaturizers.add(f);
      featurizer.deleteFeaturizers(disabledFeaturizers);
    }

    if (lexReorderFeaturizer != null) {
      additionalFeaturizers.add(lexReorderFeaturizer);
    }

    if (!additionalFeaturizers.isEmpty()) {
      List<Featurizer<IString, String>> allFeaturizers = Generics.newArrayList();
      allFeaturizers.addAll(featurizer.featurizers);
      allFeaturizers.addAll(additionalFeaturizers);
      featurizer = new CombinedFeaturizer<IString, String>(allFeaturizers);
    }
    
    // Link the final featurizer and the phrase table
    phraseGenerator.setFeaturizer(featurizer);

    // Create Scorer
    Counter<String> weightVector = new ClassicCounter<String>();

    if (config.containsKey(WEIGHTS_FILE)) {
      System.err.printf("Weights file: %s\n", config.get(WEIGHTS_FILE).get(0));

      weightVector = IOTools.readWeights(config.get(WEIGHTS_FILE).get(0));
    } else {
      if (config.containsKey(INLINE_WEIGHTS)) {
        List<String> inlineWts = config.get(TRANSLATION_MODEL_WT_OPT);
        for (String inlineWt : inlineWts) {
          String[] fields = inlineWt.split("=");
          weightVector.setCount(fields[0], Double.parseDouble(fields[1]));
        }
      }

      if (config.containsKey(LANGUAGE_MODEL_WT_OPT)) {
        weightVector.setCount(NGramLanguageModelFeaturizer.FEATURE_NAME,
          Double.parseDouble(config.get(LANGUAGE_MODEL_WT_OPT).get(0)));
      }
      if (config.containsKey(DISTORTION_WT_OPT)) {
        weightVector.setCount(LinearDistortionFeaturizer.FEATURE_NAME,
          Double.parseDouble(config.get(DISTORTION_WT_OPT).get(0)));


        if (config.get(DISTORTION_WT_OPT).size() > 1) {
          int numAdditionalWts = config.get(DISTORTION_WT_OPT).size() - 1;
          if (lexReorderFeaturizer == null) {
            throw new RuntimeException(
                String
                    .format(
                        "Additional weights given for parameter %s but no lexical reordering file was specified",
                        DISTORTION_WT_OPT));
          }
          if (lexReorderFeaturizer instanceof LexicalReorderingFeaturizer) {
            LexicalReorderingFeaturizer mosesLexReorderFeaturizer = (LexicalReorderingFeaturizer) lexReorderFeaturizer;
            if (numAdditionalWts != mosesLexReorderFeaturizer.mlrt.positionalMapping.length) {
              throw new RuntimeException(
                  String
                      .format(
                          "%d re-ordering weights given with parameter %s, but %d expected",
                          numAdditionalWts, DISTORTION_WT_OPT,
                          mosesLexReorderFeaturizer.mlrt.positionalMapping.length));
            }
            for (int i = 0; i < mosesLexReorderFeaturizer.mlrt.positionalMapping.length; i++) {
              weightVector.setCount(mosesLexReorderFeaturizer.featureTags[i],
                  Double.parseDouble(config.get(DISTORTION_WT_OPT).get(i + 1)));
            }
          }
        }
      }

      if (config.containsKey(WORD_PENALTY_WT_OPT)) {
        weightVector.setCount(WordPenaltyFeaturizer.FEATURE_NAME,
            Double.parseDouble(config.get(WORD_PENALTY_WT_OPT).get(0)));
      }

      weightVector.setCount(UnknownWordFeaturizer.FEATURE_NAME, 1.0);

      if (config.containsKey(TRANSLATION_MODEL_WT_OPT)) {
        System.err.printf("Warning: Ignoring old translation model weights set with %s", TRANSLATION_MODEL_WT_OPT);
      }
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

    System.err.printf("WeightConfig: '%s' %s\n", Counters.toBiggestValuesFirstString(weightVector, 100), (weightVector.size() > 100 ? "..." : ""));


    // Create Recombination Filter
    RecombinationFilter<Derivation<IString, String>> filter = RecombinationFilterFactory
        .factory(featurizer.getNestedFeaturizers(), msdRecombination,
            recombinationHeuristic);

    // Create Search Heuristic
    RuleFeaturizer<IString, String> isolatedPhraseFeaturizer = featurizer;
    SearchHeuristic<IString, String> heuristic = HeuristicFactory.factory(
        isolatedPhraseFeaturizer,
        withGaps ? HeuristicFactory.ISOLATED_DTU_SOURCE_COVERAGE
            : HeuristicFactory.ISOLATED_PHRASE_SOURCE_COVERAGE);

    // Create Inferers and scorers
    if (config.containsKey(LOCAL_PROCS))
      numThreads = Integer.parseInt(config.get(LOCAL_PROCS).get(0));
    if (numThreads < 1) throw new RuntimeException("Number of threads must be positive: " + numThreads);
    System.err.printf("Number of threads: %d%n", numThreads);

    inferers = new ArrayList<Inferer<IString, String>>(numThreads);
    scorers = new ArrayList<Scorer<String>>(numThreads);

    boolean dtuDecoder = (gapT != FeaturizerFactory.GapType.none);

    String searchAlgorithm = config.containsKey(SEARCH_ALGORITHM) ?
      config.get(SEARCH_ALGORITHM).get(0).trim() : InfererBuilderFactory.DEFAULT_INFERER;
    if (dtuDecoder) {
      searchAlgorithm = InfererBuilderFactory.DTU_DECODER;
    }
    System.err.printf("Search algorithm: %s%n", searchAlgorithm);
    
    for (int i = 0; i < numThreads; i++) {
      // Configure InfererBuilder
      AbstractBeamInfererBuilder<IString, String> infererBuilder = (AbstractBeamInfererBuilder<IString, String>) 
          InfererBuilderFactory.factory(searchAlgorithm);
      try {
        infererBuilder.setAnnotators(additionalAnnotators);
        infererBuilder
            .setIncrementalFeaturizer((CombinedFeaturizer<IString, String>) featurizer
                .clone());
        infererBuilder
            .setPhraseGenerator((PhraseGenerator<IString,String>) phraseGenerator
                .clone());
        Scorer<String> scorer = ScorerFactory.factory(ScorerFactory.SPARSE_SCORER, weightVector, null);
        infererBuilder.setScorer(scorer);
        scorers.add(scorer);
        infererBuilder
            .setSearchHeuristic((SearchHeuristic<IString, String>) heuristic
                .clone());
        infererBuilder
            .setRecombinationFilter((RecombinationFilter<Derivation<IString, String>>) filter
                .clone());
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
                      "Beam size %s, as specified by argument %s, can not be parsed as an integer value\n",
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

      } else if (nbestOpt.size() == 2) {
        String nbestListFilename = nbestOpt.get(0);
        nbestListSize = Integer.parseInt(nbestOpt.get(1));
        assert nbestListSize >= 0;
        nbestListWriter = IOTools.getWriterFromFile(nbestListFilename);
        System.err.printf("Generating n-best lists to: %s (size: %d)\n",
            nbestListFilename, nbestListSize);

      } else {
        throw new RuntimeException(
            String.format("%s requires 1 or 2 arguments, not %d", NBEST_LIST_OPT,
                nbestOpt.size()));
      }

    } else {
      nbestListSize = -1;
      nbestListWriter = null;
    }
    
    List<String> mosesNbestOpt = config.get(MOSES_NBEST_LIST_OPT);
    if (mosesNbestOpt != null && mosesNbestOpt.size() > 0) {
      generateMosesNBestList = Boolean.parseBoolean(mosesNbestOpt.get(0));
      if (mosesNbestOpt.size() > 1) {
        nbestWordInternalAlignments = Boolean.parseBoolean(mosesNbestOpt.get(1));
      }
    }
        
    // Determine if we need to generate an alignment file
    List<String> alignmentOpt = config.get(ALIGNMENT_OUTPUT_FILE);
    if (alignmentOpt != null && alignmentOpt.size() == 1) {
      alignmentWriter = IOTools.getWriterFromFile(alignmentOpt.get(0));
    }
    
    // Should we enable word-internal alignments?
    if (nbestWordInternalAlignments || alignmentWriter != null) {
      Featurizable.enableAlignments();
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
    public final int sourceInputId;

    public DecoderInput(Sequence<IString> seq, int sourceInputId) {
      this.source = seq;
      this.sourceInputId = sourceInputId;
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
          decode(input.source, input.sourceInputId, infererId);
      
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
      System.out.println(bestTranslation.toString());

      // log additional information to stderr
      RichTranslation<IString,String> bestTranslationInfo = translations.get(0);
      System.err.printf("Best Translation: %s%n", bestTranslation);
      System.err.printf("Final score: %.3f%n", (float) bestTranslationInfo.score);
      if (bestTranslationInfo.sourceCoverage != null) {
        System.err.printf("Coverage: %s%n", bestTranslationInfo.sourceCoverage);
        System.err.printf(
            "Foreign words covered: %d (/%d) - %.3f %%%n",
            bestTranslationInfo.sourceCoverage.cardinality(),
            sourceLength,
            bestTranslationInfo.sourceCoverage.cardinality() * 100.0
            / sourceLength);
      } else {
        System.err.println("Coverage: {}");
      }

      // Output the n-best list if necessary
      if (nbestListWriter != null) {
        IOTools.writeNbest(translations, sourceInputId, generateMosesNBestList, nbestListWriter, nbestWordInternalAlignments);
      }
      
      // Output the alignments if necessary
      if (alignmentWriter != null) {
        for (RichTranslation<IString,String> translation : translations) {
          alignmentWriter.printf("%d %s %s%n", sourceInputId, FlatNBestList.FIELD_DELIM, 
              translation.sourceTargetAlignmentString());
        }
      }

    } else {
      // Decoder failure. Print an empty line.
      System.out.println();
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
        preprocessor.process(line.trim()).e();
      if (source.size() > maxSentenceSize || source.size() < minSentenceSize) {
        System.err.printf("Skipping: %s%n", line);
        System.err.printf("Tokens: %d (min: %d max: %d)%n", source.size(), minSentenceSize,
            maxSentenceSize);
        continue;
      }

      wrapper.put(new DecoderInput(source, sourceInputId));
      while(wrapper.peek()) {
        DecoderOutput result = wrapper.poll();
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
    List<Sequence<IString>> targets = 
        forceDecodeReferences == null ? null : forceDecodeReferences.get(sourceInputId);
    return decode(source, sourceInputId, threadId, nbestListSize, targets, false);
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
      boolean targetsArePrefixes) {
    assert threadId >= 0 && threadId < numThreads;
    assert sourceInputId >= 0;

    // Output space of the decoder
    OutputSpace<IString, String> outputSpace = OutputSpaceFactory.getOutputSpace(source, sourceInputId, 
        targets, phraseGenerator.longestSourcePhrase(), phraseGenerator.longestTargetPhrase(), targetsArePrefixes);

    List<RichTranslation<IString, String>> translations =
        new ArrayList<RichTranslation<IString, String>>(1);

    if (numTranslations > 1) {
      translations = inferers
          .get(threadId).nbest(
              source,
              sourceInputId,
              outputSpace,
              outputSpace.getAllowableSequences(), numTranslations);

      // Return an empty n-best list
      if (translations == null) translations = new ArrayList<RichTranslation<IString,String>>(1);

    } else {
      // The 1-best translation in this case is potentially different from
      // calling nbest() with a list size of 1. Therefore, this call is *not* a special
      // case of the condition above.
      RichTranslation<IString, String> translation = inferers.get(threadId).translate(
          source,
          sourceInputId,
          outputSpace,
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
  public void shutdown() {
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
   * Load options from a Moses-style ini file.
   *
   * @param filename
   * @throws IOException
   */
  public static Map<String, List<String>> readConfig(String filename)
      throws IOException {
    Map<String, List<String>> config = new HashMap<String, List<String>>();
    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    for (String line; (line = reader.readLine()) != null;) {
      line = line.trim().replaceAll("#.*$", "");
      if (line.length() == 0)
        continue;
      if (line.charAt(0) != '[' || line.charAt(line.length() - 1) != ']') {
        reader.close();
        throw new RuntimeException(
            String
                .format(
                    "Expected bracketing of option name by '[',']', line: %d label: %s",
                    reader.getLineNumber(), line));
      }
      String nextArgLine = line;

      while (nextArgLine != null) {
        String key = line.substring(1, nextArgLine.length() - 1);
        nextArgLine = null;
        List<String> entries = new ArrayList<String>();
        while ((line = reader.readLine()) != null) {
          if (line.matches("^\\s*$"))
            break;
          if (line.startsWith("[")) {
            nextArgLine = line;
            break;
          }
          if (line.charAt(0) == '#')
            break;
          line = line.replaceAll("#.*$", "");
          String[] fields = line.split("\\s+");
          entries.addAll(Arrays.asList(fields));
        }

        if (!entries.isEmpty())
          config.put(key, entries);
      }
    }
    reader.close();
    return config;
  }

  /**
   * Read a combination of config file and other command line arguments.
   *
   * @param args
   * @return
   * @throws IOException
   */
  private static Map<String, List<String>> readArgs(String[] args) throws IOException {
    Map<String, List<String>> configArgs = new HashMap<String, List<String>>();
    Map<String, List<String>> configFile = new HashMap<String, List<String>>();
    Map<String, List<String>> configFinal = new HashMap<String, List<String>>();

    for (Map.Entry<Object, Object> e : StringUtils.argsToProperties(args)
        .entrySet()) {
      String key = e.getKey().toString();
      String value = e.getValue().toString();
      if (CONFIG_FILE.equals(key)) {
        configFile.putAll(readConfig(value));
      } else {
        configArgs.put(key, Arrays.asList(value.split(" ")));
      }
    }
    configFinal.putAll(configFile);
    configFinal.putAll(configArgs); // command line args overwrite config file options
    return configFinal;
  }

  /**
   * Load an instance of phrasal from an ini file.
   *
   * @param phrasalIniFile
   * @throws IOException
   */
  public static Phrasal loadDecoder(String phrasalIniFile) throws IOException {
    Map<String, List<String>> config = Phrasal.readConfig(phrasalIniFile);
    return loadDecoder(config);
  }

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
    if (args.length < 1) {
      System.err.printf("Usage: java %s (model.ini) [OPTS]%n", Phrasal.class.getName());
      System.exit(-1);
    }

    // by default, exit on uncaught exception
    Thread
        .setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread t, Throwable ex) {
            System.err.println("Uncaught exception from thread: " + t.getName());
            System.err.println(ex.toString());
            ex.printStackTrace();
            System.exit(-1);
          }
        });

    Map<String, List<String>> config = (args.length == 1) ? readConfig(args[0])
        : readArgs(args);
    Phrasal p = Phrasal.loadDecoder(config);
    p.decode(System.in, true);

    // [spenceg]: For execution in the debugger
    //    p.decode(new FileInputStream(new File("debug.1")), true);
  }
}
