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
import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.annotators.AnnotatorFactory;
import edu.stanford.nlp.mt.decoder.feat.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.OAIndex;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
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
  public static final String CONSTRAIN_TO_REFS = "constrain-to-refs";
  public static final String PREFERED_REF_STRUCTURE = "use-prefered-ref-structure";
  public static final String LEARN_WEIGHTS_USING_REFS = "learn-weights-using-refs";
  public static final String PREFERED_REF_INTERNAL_STATE = "prefered-internal-state";
  public static final String LEARNING_ALGORITHM = "learning-algorithm";
  public static final String LEARNING_TARGET = "learning-target";
  public static final String SAVE_WEIGHTS = "save-weights";
  public static final String BEAM_SIZE = "stack";
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
  public static final String LEARNING_RATE = "lrate";
  public static final String MAX_EPOCHS = "max-epochs";
  public static final String MOMENTUM = "momentum";
  public static final String CONSTRAIN_MANUAL_WTS = "constrain-manual-wts";
  public static final String LOCAL_PROCS = "localprocs";
  public static final String ITER_LIMIT = "iter-limit";
  public static final String USE_ITG_CONSTRAINTS = "use-itg-constraints";
  public static final String EVAL_METRIC = "eval-metric";
  public static final String LEARNING_METRIC = "learning-metric";
  public static final String RECOMBINATION_HEURISTIC = "recombination-heuristic";
  public static final String GAPS_OPT = "gaps";
  public static final String MAX_GAP_SPAN_OPT = "max-gap-span";
  public static final String MAX_PENDING_PHRASES_OPT = "max-pending-phrases";
  public static final String GAPS_IN_FUTURE_COST_OPT = "gaps-in-future-cost";
  public static final String ISTRING_VOC_OPT = "istring-vocabulary";
  public static final String MOSES_COMPATIBILITY_OPT = "moses-compatibility";
  public static final String LINEAR_DISTORTION_TYPE = "linear-distortion-type";
  public static final String DROP_UNKNOWN_WORDS = "drop-unknown-words";
  
  public static final int DEFAULT_DISCRIMINATIVE_LM_ORDER = 0;
  public static final boolean DEFAULT_DISCRIMINATIVE_TM_PARAMETER = false;

  static final Set<String> REQUIRED_FIELDS = new HashSet<String>();
  static final Set<String> OPTIONAL_FIELDS = new HashSet<String>();
  static final Set<String> IGNORED_FIELDS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_FIELDS = new HashSet<String>();

  public static double DEFAULT_LEARNING_RATE = 0.01;
  public static double DEFAULT_MOMENTUM_TERM = 0.9;
  static final int DEFAULT_LOCAL_PROCS = 1;
  static final int DEFAULT_MAX_EPOCHS = 5;
  static final int DEFAULT_DISTORTION_LIMIT = 5;
  static final String DEFAULT_RECOMBINATION_HEURISTIC = RecombinationFilterFactory.CLASSICAL_TRANSLATION_MODEL;
  public static boolean DROP_UNKNOWN_WORDS_DEFAULT = true;
  static final boolean VERBOSE_LEARNER = true;

  static {
    REQUIRED_FIELDS.addAll(Arrays.asList(TRANSLATION_TABLE_OPT,
        LANGUAGE_MODEL_OPT, DISTORTION_WT_OPT, LANGUAGE_MODEL_WT_OPT,
        TRANSLATION_MODEL_WT_OPT, WORD_PENALTY_WT_OPT));
    OPTIONAL_FIELDS.addAll(Arrays.asList(INLINE_WEIGHTS, ITER_LIMIT,
        DISTORTION_FILE, DISTORTION_LIMIT, ADDITIONAL_FEATURIZERS,
        DISABLED_FEATURIZERS, USE_DISCRIMINATIVE_TM, FORCE_DECODE_ONLY,
        OPTION_LIMIT_OPT, NBEST_LIST_OPT, MOSES_NBEST_LIST_OPT,
        DISTINCT_NBEST_LIST_OPT, CONSTRAIN_TO_REFS, PREFERED_REF_STRUCTURE,
        RECOMBINATION_HEURISTIC, HIER_DISTORTION_FILE,
        LEARN_WEIGHTS_USING_REFS, LEARNING_ALGORITHM,
        PREFERED_REF_INTERNAL_STATE, SAVE_WEIGHTS, LEARNING_TARGET, BEAM_SIZE,
        WEIGHTS_FILE, USE_DISCRIMINATIVE_LM, MAX_SENTENCE_LENGTH,
        MIN_SENTENCE_LENGTH, CONSTRAIN_MANUAL_WTS, LEARNING_RATE, MOMENTUM,
        USE_ITG_CONSTRAINTS, LEARNING_METRIC, EVAL_METRIC, LOCAL_PROCS,
        GAPS_OPT, GAPS_IN_FUTURE_COST_OPT, MAX_GAP_SPAN_OPT,
        LINEAR_DISTORTION_TYPE, MAX_PENDING_PHRASES_OPT, ISTRING_VOC_OPT,
        MOSES_COMPATIBILITY_OPT, ADDITIONAL_ANNOTATORS, DROP_UNKNOWN_WORDS));
    IGNORED_FIELDS.addAll(Arrays.asList(INPUT_FACTORS_OPT, MAPPING_OPT,
        FACTOR_DELIM_OPT));
    ALL_RECOGNIZED_FIELDS.addAll(REQUIRED_FIELDS);
    ALL_RECOGNIZED_FIELDS.addAll(OPTIONAL_FIELDS);
    ALL_RECOGNIZED_FIELDS.addAll(IGNORED_FIELDS);
  }

  public static final Map<String, Double> IDEALIZED_TARGETS = new HashMap<String, Double>();

  static {
    IDEALIZED_TARGETS.put("TM:lex(f|t)", 0.0);
    IDEALIZED_TARGETS.put("TM:lex(t|f)", 0.0);
    IDEALIZED_TARGETS.put("TM:phi(f|t)", 0.0);
    IDEALIZED_TARGETS.put("TM:phi(t|f)", 0.0);
    IDEALIZED_TARGETS.put("LM", 0.0);
    IDEALIZED_TARGETS.put("LinearDistortion", 0.0);
    IDEALIZED_TARGETS.put("LexR:discontinuousWithNext", 0.0);
    IDEALIZED_TARGETS.put("LexR:discontinuousWithPrevious", 0.0);
    IDEALIZED_TARGETS.put("LexR:monotoneWithNext", 0.0);
    IDEALIZED_TARGETS.put("LexR:monotoneWithPrevious", 0.0);
    IDEALIZED_TARGETS.put("LexR:swapWithNext", 0.0);
    IDEALIZED_TARGETS.put("LexR:swapWithPrevious", 0.0);
    IDEALIZED_TARGETS.put("UnknownWord", 0.0);
    IDEALIZED_TARGETS.put("SentenceBoundary", 0.0);
  }

  public static final String PERCEPTRON_LEARNING = "perceptron";
  public static final String AVG_PERCEPTRON_LEARNING = "avgperceptron";
  public static final String MIRA_LEARNING = "mira";
  public static final String SSVM_LEARNING = "ssvm";
  public static final String MMSG_LEARNING = "mmsg";
  public static final String COST_MARGIN_LEARNING = "costmargin";
  public static final String SGDLL = "sgdll";
  public static final String MAXMARGIN_C = "C";

  public static final String DEFAULT_LEARNING_ALGORITHM = PERCEPTRON_LEARNING;
  // public static final String DEFAULT_SAVE_WEIGHTS = "unname_model_"
  // + System.currentTimeMillis();

  public static int local_procs = DEFAULT_LOCAL_PROCS;
  public static boolean withGaps = false;
  public static int distortionLimit = DEFAULT_DISTORTION_LIMIT;

  static List<String> gapOpts = null;

  public List<Inferer<IString, String>> inferers;
  // Inferer<IString, String> refInferer;
  PhraseGenerator<IString> phraseGenerator;
  boolean dropUnknownWords = DROP_UNKNOWN_WORDS_DEFAULT;
  final BufferedWriter nbestListWriter;
  int nbestListSize;
  String saveWeights = "saved.wts";

  List<List<Sequence<IString>>> constrainedToRefs = null;

  boolean learnWeights;
  boolean constrainManualWeights;
  boolean generateMosesNBestList;
  List<List<Sequence<IString>>> learnFromReferences;
  String learningAlgorithm;
  List<String> learningAlgorithmConfig;
  Scorer<String> scorer;
  double maxMarginC = 1.0;
  NBestListContainer<IString, String> preferedInternalState;
  int maxSentenceSize = Integer.MAX_VALUE;
  int minSentenceSize = 0;

  double[] learningRate = new double[0];
  double momentumTerm = DEFAULT_MOMENTUM_TERM;
  int maxEpochs = DEFAULT_MAX_EPOCHS;

  // double cTarget = 0.001;
  // double cRisky = 0.010;
  static String recombinationHeuristic = DEFAULT_RECOMBINATION_HEURISTIC;

  public static enum LearningTarget {
    REFERENCE, BEST_ON_N_BEST_LIST
  }

  public static LearningTarget DEFAULT_LEARNING_TARGET = LearningTarget.BEST_ON_N_BEST_LIST;
  LearningTarget learningTarget = DEFAULT_LEARNING_TARGET;

  EvaluationMetric<IString, String> learningMetric = null;
  EvaluationMetric<IString, String> evalMetric = null;

  public static final Map<String, LearningTarget> configToLearningTarget = new HashMap<String, LearningTarget>();
  static {
    configToLearningTarget.put("best-on-n-best",
        LearningTarget.BEST_ON_N_BEST_LIST);
    configToLearningTarget.put("reference", LearningTarget.REFERENCE);
  }

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
      ConcreteTranslationOption.setLinearDistortionType(config.get(
          LINEAR_DISTORTION_TYPE).get(0));
    else if (withGaps)
      ConcreteTranslationOption
          .setLinearDistortionType(ConcreteTranslationOption.LinearDistortionType.last_contiguous_segment
              .name());
    if (config.containsKey(LOCAL_PROCS))
      local_procs = Integer.parseInt(config.get(LOCAL_PROCS).get(0));

    if (withGaps)
      recombinationHeuristic = RecombinationFilterFactory.DTU_TRANSLATION_MODEL;
  }

  static public Map<String, List<String>> readConfig(String filename)
      throws IOException {
    Map<String, List<String>> config = new HashMap<String, List<String>>();
    LineNumberReader reader;
    try {
      reader = new LineNumberReader(new FileReader(filename));
    } catch (FileNotFoundException e) {
      throw new RuntimeException(String.format("Can't open configuration file %s\n", filename));
    }
    for (String line; (line = reader.readLine()) != null;) {
      if (line.matches("^\\s*$"))
        continue;
      if (line.charAt(0) == '#')
        continue;
      line = line.replaceAll("#.*$", "");
      if (line.charAt(0) != '[' || line.charAt(line.length() - 1) != ']') {
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

  static Map<String, List<String>> readArgs(String[] args) throws IOException {
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
        configArgs.put(key, Arrays.asList(value));
      }
    }
    configFinal.putAll(configFile);
    configFinal.putAll(configArgs); // command line args overwrite config file options
    return configFinal;
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

    if (config.containsKey(ITER_LIMIT)) {
      MAX_LEARN_NBEST_ITER = Integer.parseInt(config.get(ITER_LIMIT).get(0));
    }

    if (config.containsKey(CONSTRAIN_MANUAL_WTS)) {
      constrainManualWeights = Boolean.parseBoolean(config.get(
          CONSTRAIN_MANUAL_WTS).get(0));
    }

    if (config.containsKey(LEARNING_RATE)) {
      learningRate = new double[config.get(LEARNING_RATE).size()];
      for (int i = 0; i < learningRate.length; i++) {
        learningRate[i] = Double.parseDouble(config.get(LEARNING_RATE).get(i));
      }
    } else {
      learningRate = new double[1];
      learningRate[0] = DEFAULT_LEARNING_RATE;
    }

    if (config.containsKey(MAXMARGIN_C)) {
      maxMarginC = Double.parseDouble(config.get(MAXMARGIN_C).get(0));
    }

    if (config.containsKey(MAX_EPOCHS)) {
      maxEpochs = Integer.parseInt(config.get(MAX_EPOCHS).get(0));
    }

    if (config.containsKey(MOMENTUM)) {
      momentumTerm = Double.parseDouble(config.get(MOMENTUM).get(0));
    }

    if (config.containsKey(RECOMBINATION_HEURISTIC)) {
      recombinationHeuristic = config.get(RECOMBINATION_HEURISTIC).get(0);
    }

    boolean mosesMode = config.containsKey(MOSES_COMPATIBILITY_OPT);

    // System.err.printf("C - Target: %e Risky: %e\n", cTarget, cRisky);

    if (config.containsKey(CONSTRAIN_TO_REFS)) {
      constrainedToRefs = Metrics.readReferences(config.get(CONSTRAIN_TO_REFS)
          .toArray(new String[config.get(CONSTRAIN_TO_REFS).size()]));
    }

    if (config.containsKey(LEARNING_TARGET)) {
      List<String> strLearningTarget = config.get(LEARNING_TARGET);
      if (strLearningTarget.size() != 1) {
        throw new RuntimeException(String.format(
            "Parameter '%s' takes one and only one argument", LEARNING_TARGET));
      }
      learningTarget = configToLearningTarget.get(strLearningTarget.get(0));
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

    MSDFeaturizer<IString, String> lexReorderFeaturizer = null;

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

    List<Annotator<IString>> additionalAnnotators = new ArrayList<Annotator<IString>>();    
    if (config.containsKey(ADDITIONAL_ANNOTATORS)) {
    	// todo make some general method that can parse both additional annotators 
    	// and additional featurizers
    	List<String> tokens = config.get(ADDITIONAL_ANNOTATORS);
        String annotatorName = null;
        String args = null;
        for (String token : tokens) {
          Annotator<IString> annotator = null;
          if (annotatorName == null) {
            if (token.endsWith("()")) {
              String name = token.replaceFirst("\\(\\)$", "");
              Class<Annotator<IString>> annotatorClass = AnnotatorFactory
                  .loadAnnotator(name);
              annotator = (Annotator<IString>) annotatorClass
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
                Class<IncrementalFeaturizer<IString, String>> featurizerClass = FeaturizerFactory
                    .loadFeaturizer(annotatorName);
                annotator = (Annotator<IString>) featurizerClass
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
              Class<Annotator<IString>> annotatorClass = AnnotatorFactory
                  .loadAnnotator(annotatorName);
              annotator = (Annotator<IString>) annotatorClass
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
    
    List<IncrementalFeaturizer<IString, String>> additionalFeaturizers = new ArrayList<IncrementalFeaturizer<IString, String>>();
    if (config.containsKey(ADDITIONAL_FEATURIZERS)) {
      List<String> tokens = config.get(ADDITIONAL_FEATURIZERS);
      String featurizerName = null;
      String args = null;
      for (String token : tokens) {
        IncrementalFeaturizer<IString, String> featurizer = null;
        if (featurizerName == null) {
          if (token.endsWith("()")) {
            String name = token.replaceFirst("\\(\\)$", "");
            Class<IncrementalFeaturizer<IString, String>> featurizerClass = FeaturizerFactory
                .loadFeaturizer(name);
            featurizer = (IncrementalFeaturizer<IString, String>) featurizerClass
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
              Class<IncrementalFeaturizer<IString, String>> featurizerClass = FeaturizerFactory
                  .loadFeaturizer(featurizerName);
              featurizer = (IncrementalFeaturizer<IString, String>) featurizerClass
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
            Class<IncrementalFeaturizer<IString, String>> featurizerClass = FeaturizerFactory
                .loadFeaturizer(featurizerName);
            featurizer = (IncrementalFeaturizer<IString, String>) featurizerClass
                .getConstructor(argsList.getClass()).newInstance(
                    (Object) argsList);
            additionalFeaturizers.add(featurizer);
            featurizerName = null;
            args = null;
          } else {
            args += " " + token;
          }
        }
        if (featurizer instanceof AlignmentFeaturizer)
          Featurizable.enableAlignments();
        if (featurizer instanceof MSDFeaturizer)
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
    String lgModel, lgModelVoc = "";
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

    FeaturizerFactory.GapType gapT = !withGaps ? FeaturizerFactory.GapType.none
        : ((gapOpts.size() > 1) ? FeaturizerFactory.GapType.both
            : FeaturizerFactory.GapType.source);
    String gapType = gapT.name();
    System.err.println("Gap type: " + gapType);

    featurizer = FeaturizerFactory.factory(
        FeaturizerFactory.PSEUDO_PHARAOH_GENERATOR,
        makePair(FeaturizerFactory.LINEAR_DISTORTION_PARAMETER,
            linearDistortion),
        makePair(FeaturizerFactory.GAP_PARAMETER, gapType),
        makePair(FeaturizerFactory.ARPA_LM_PARAMETER, lgModel),
        makePair(FeaturizerFactory.ARPA_LM_VOC_PARAMETER, lgModelVoc),
        makePair(FeaturizerFactory.DISCRIMINATIVE_LM_PARAMETER, ""
            + discriminativeLMOrder),
        makePair(FeaturizerFactory.DISCRIMINATIVE_TM_PARAMETER, ""
            + discriminativeTMParameter));

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
      List<IncrementalFeaturizer<IString, String>> allFeaturizers = new ArrayList<IncrementalFeaturizer<IString, String>>();
      allFeaturizers.addAll(featurizer.featurizers);
      allFeaturizers.addAll(additionalFeaturizers);
      featurizer = new CombinedFeaturizer<IString, String>(allFeaturizers);
    }

    // Create Scorer
    Counter<String> weightConfig = new ClassicCounter<String>();

    if (config.containsKey(WEIGHTS_FILE)) {
      System.err.printf("Weights file: %s\n", config.get(WEIGHTS_FILE).get(0));
      if (config.get(WEIGHTS_FILE).get(0).endsWith(".binwts")) {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
            config.get(WEIGHTS_FILE).get(0)));
        weightConfig = (Counter<String>) ois.readObject();
        ois.close();
      } else {

        BufferedReader reader = new BufferedReader(new FileReader(config.get(
            WEIGHTS_FILE).get(0)));
        for (String line; (line = reader.readLine()) != null;) {
          String[] fields = line.split("\\s+");
          weightConfig.incrementCount(fields[0], Double.parseDouble(fields[1]));
        }
        reader.close();
      }
    } else {
      if (config.containsKey(INLINE_WEIGHTS)) {
        List<String> inlineWts = config.get(TRANSLATION_MODEL_WT_OPT);
        for (String inlineWt : inlineWts) {
          String[] fields = inlineWt.split("=");
          weightConfig.setCount(fields[0], Double.parseDouble(fields[1]));
        }
      }
      weightConfig.setCount(NGramLanguageModelFeaturizer.FEATURE_NAME,
          Double.parseDouble(config.get(LANGUAGE_MODEL_WT_OPT).get(0)));
      weightConfig.setCount(LinearDistortionFeaturizer.FEATURE_NAME,
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
            weightConfig.setCount(mosesLexReorderFeaturizer.featureTags[i],
                Double.parseDouble(config.get(DISTORTION_WT_OPT).get(i + 1)));
          }
        }
      }
      weightConfig.setCount(WordPenaltyFeaturizer.FEATURE_NAME,
          Double.parseDouble(config.get(WORD_PENALTY_WT_OPT).get(0)));
      weightConfig.setCount(UnknownWordFeaturizer.FEATURE_NAME, 1.0);
      weightConfig.setCount(SentenceBoundaryFeaturizer.FEATURE_NAME, 1.0);

      List<String> tmodelWtsStr = config.get(TRANSLATION_MODEL_WT_OPT);
      if (tmodelWtsStr.size() == 5) {
        weightConfig.setCount(
            makePair(PhraseTableScoresFeaturizer.PREFIX,
                FlatPhraseTable.FIVESCORE_PHI_e_f), Double
                .parseDouble(tmodelWtsStr.get(0)));
        weightConfig.setCount(
            makePair(PhraseTableScoresFeaturizer.PREFIX,
                FlatPhraseTable.FIVESCORE_LEX_e_f), Double
                .parseDouble(tmodelWtsStr.get(1)));
        weightConfig.setCount(
            makePair(PhraseTableScoresFeaturizer.PREFIX,
                FlatPhraseTable.FIVESCORE_PHI_f_e), Double
                .parseDouble(tmodelWtsStr.get(2)));
        weightConfig.setCount(
            makePair(PhraseTableScoresFeaturizer.PREFIX,
                FlatPhraseTable.FIVESCORE_LEX_f_e), Double
                .parseDouble(tmodelWtsStr.get(3)));
        weightConfig.setCount(
            makePair(PhraseTableScoresFeaturizer.PREFIX,
                FlatPhraseTable.FIVESCORE_PHRASE_PENALTY), Double
                .parseDouble(tmodelWtsStr.get(4)));
      } else if (tmodelWtsStr.size() == 1) {
        weightConfig.setCount(
            makePair(PhraseTableScoresFeaturizer.PREFIX,
                FlatPhraseTable.ONESCORE_P_t_f), Double
                .parseDouble(tmodelWtsStr.get(0)));
      } else {
        throw new RuntimeException(String.format(
            "Unsupported weight count for translation model: %d",
            tmodelWtsStr.size()));
      }
    }

    if (learnWeights = config.containsKey(LEARN_WEIGHTS_USING_REFS)
        && !config.containsKey(FORCE_DECODE_ONLY)) {
      if (config.containsKey(LEARNING_ALGORITHM)) {
        learningAlgorithmConfig = config.get(LEARNING_ALGORITHM);
        learningAlgorithm = learningAlgorithmConfig.get(0);
      } else {
        learningAlgorithm = DEFAULT_LEARNING_ALGORITHM;
        learningAlgorithmConfig = null;
      }
      learnFromReferences = Metrics.readReferences(config.get(
          LEARN_WEIGHTS_USING_REFS).toArray(
          new String[config.get(LEARN_WEIGHTS_USING_REFS).size()]));
      learningMetric = (config.containsKey(LEARNING_METRIC) ? MetricFactory
          .metric(config.get(LEARNING_METRIC).get(0), learnFromReferences)
          : MetricFactory.metric(learnFromReferences));
      evalMetric = (config.containsKey(EVAL_METRIC) ? MetricFactory.metric(
          config.get(EVAL_METRIC).get(0), learnFromReferences) : MetricFactory
          .metric(learnFromReferences));
    }

    if (config.containsKey(PREFERED_REF_INTERNAL_STATE)) {
      preferedInternalState = new FlatNBestList(config.get(
          PREFERED_REF_INTERNAL_STATE).get(0));
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

    if (config.containsKey(SAVE_WEIGHTS)) {
      int cntSaveWeights = config.get(SAVE_WEIGHTS).size();
      if (cntSaveWeights != 1) {
        throw new RuntimeException(
            String
                .format(
                    "One and only one file must be specified for the parameter %s not %d",
                    SAVE_WEIGHTS, cntSaveWeights));
      }
      saveWeights = config.get(SAVE_WEIGHTS).get(0);
    }


    System.err.printf("WeightConfig: '%s' %s\n", Counters.toBiggestValuesFirstString(weightConfig, 100), (weightConfig.size() > 100 ? "..." : ""));
    scorer = ScorerFactory.factory(ScorerFactory.STATIC_SCORER, weightConfig);

    // Create phrase generator
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

    if (config.containsKey(MOSES_NBEST_LIST_OPT)) {
      generateMosesNBestList = Boolean.parseBoolean(config.get(
          MOSES_NBEST_LIST_OPT).get(0));
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
      phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory.factory(
          featurizer, scorer, dropUnknownWords, PhraseGeneratorFactory.NEW_DYNAMIC_GENERATOR,
          phraseTable) : PhraseGeneratorFactory.factory(featurizer, scorer, dropUnknownWords,
          PhraseGeneratorFactory.NEW_DYNAMIC_GENERATOR,
          phraseTable.replaceFirst("^bitext:", ""), optionLimit));
    } else if (phraseTable.endsWith(".db") || phraseTable.contains(".db:")) {

      System.err.println("Dyanamic pt\n========================");
      phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory.factory(
          featurizer, scorer, dropUnknownWords, PhraseGeneratorFactory.DYNAMIC_GENERATOR,
          phraseTable) : PhraseGeneratorFactory.factory(featurizer, scorer, dropUnknownWords,
          PhraseGeneratorFactory.DYNAMIC_GENERATOR, phraseTable, optionLimit));
    } else {
      String generatorName = withGaps ? PhraseGeneratorFactory.DTU_GENERATOR
          : PhraseGeneratorFactory.PSEUDO_PHARAOH_GENERATOR;
      phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory.factory(
          featurizer, scorer, dropUnknownWords, generatorName, phraseTable)
          : PhraseGeneratorFactory.factory(featurizer, scorer, dropUnknownWords, generatorName,
              phraseTable, optionLimit));
    }

    System.err.printf("Phrase Limit: %d\n",
        ((CombinedPhraseGenerator<IString>) phraseGenerator).getPhraseLimit());

    // Create Recombination Filter
    RecombinationFilter<Hypothesis<IString, String>> filter = RecombinationFilterFactory
        .factory(featurizer.getNestedFeaturizers(), msdRecombination,
            recombinationHeuristic);

    // Create Search Heuristic
    IsolatedPhraseFeaturizer<IString, String> isolatedPhraseFeaturizer = featurizer;
    SearchHeuristic<IString, String> heuristic = HeuristicFactory.factory(
        isolatedPhraseFeaturizer, scorer,
        withGaps ? HeuristicFactory.ISOLATED_DTU_FOREIGN_COVERAGE
            : HeuristicFactory.ISOLATED_PHRASE_FOREIGN_COVERAGE);
    // Create Inferer
    inferers = new ArrayList<Inferer<IString, String>>(local_procs == 0 ? 1
        : local_procs);

    boolean dtuDecoder = (gapT != FeaturizerFactory.GapType.none);
    // boolean dtuDecoder = (gapT == FeaturizerFactory.GapType.none || gapT ==
    // FeaturizerFactory.GapType.both);
    for (int i = 0; i < (local_procs == 0 ? 1 : local_procs); i++) {
      // Configure InfererBuilder
      AbstractBeamInfererBuilder<IString, String> infererBuilder = (AbstractBeamInfererBuilder<IString, String>) InfererBuilderFactory
          .factory(dtuDecoder ? InfererBuilderFactory.DTU_DECODER
              : InfererBuilderFactory.MULTIBEAM_DECODER);
      try {
    	infererBuilder.setAnnotators(additionalAnnotators);
        infererBuilder
            .setIncrementalFeaturizer((CombinedFeaturizer<IString, String>) featurizer
                .clone());
        infererBuilder
            .setPhraseGenerator((PhraseGenerator<IString>) phraseGenerator
                .clone());
        infererBuilder.setScorer(scorer);
        infererBuilder
            .setSearchHeuristic((SearchHeuristic<IString, String>) heuristic
                .clone());
        infererBuilder
            .setRecombinationFilter((RecombinationFilter<Hypothesis<IString, String>>) filter
                .clone());
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }

      infererBuilder.setBeamType(HypothesisBeamFactory.BeamType.sloppybeam);
      if (local_procs == 0) {
        infererBuilder.setInternalMultiThread(true);
      }

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
    if (local_procs == 0)
      local_procs = 1;
    System.err.printf("Inferer Count: %d\n", inferers.size());

    // determine if we need to generate n-best lists
    List<String> nbestOpt = config.get(NBEST_LIST_OPT);
    if (nbestOpt != null) {
      if (nbestOpt.size() != 2) {
        throw new RuntimeException(
            String
                .format(
                    "%s requires that 2 and only 2 values are passed as arguments, not %d",
                    NBEST_LIST_OPT, nbestOpt.size()));
      }
      String nbestListFilename = nbestOpt.get(0);

      try {
        nbestListSize = Integer.parseInt(nbestOpt.get(1));
      } catch (NumberFormatException e) {
        throw new RuntimeException(String.format(
            "%s size argument, %s, can not be parsed as an integer value",
            NBEST_LIST_OPT, nbestOpt.get(1)));
      }
      if (nbestListSize <= 0) {
        throw new RuntimeException(
            String.format("%s size argmument, %d, must be > 0", NBEST_LIST_OPT,
                nbestListSize));
      }
      System.err.printf("Generating n-best lists to: %s (size: %d)\n",
          nbestListFilename, nbestListSize);
      nbestListWriter = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(nbestListFilename), "UTF8"));
    } else {
      nbestListSize = -1;
      nbestListWriter = null;
    }
  }

  private static String makePair(String label, String value) {
    return String.format("%s:%s", label, value);
  }

  private class ProcDecode implements Runnable {

    int infererid;
    private List<String> lines;
    private List<Integer> ids;

    public ProcDecode(int infererid, List<String> lines, List<Integer> ids) {
      this.infererid = infererid;
      this.lines = lines;
      this.ids = ids;
    }

    @Override
    public void run() {
      int len = ids.size();
      try {
        for (int i = 0; i < len; i++) {
          String[] tokens = lines.get(i).split("\\s+");
          RichTranslation<IString, String> translation = decodeOnly(tokens,
              ids.get(i), ids.get(i) - 1, infererid);

          if (translation != null) {
            // notice we reproduce the lameness of moses in that an extra space
            // is
            // inserted after each translation
            synchronized (System.out) {
              System.out
                  .printf("%d:%s \n", ids.get(i), translation.translation);
            }
            synchronized (System.err) {
              System.err.printf("Final Translation: %s\n",
                  translation.translation);
              System.err.printf("Score: %f\n", translation.score);
            }
          } else {
            synchronized (System.out) {
              System.out.printf("<<<decoder failure %d>>>\n", ids.get(i));
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(-1);
      }
    }
  }

  private void decodeFromConsole() throws IOException {

    System.err.println("Entering main translation loop");

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in, "UTF-8"));
    int translationId = 0;
    long startTime = System.currentTimeMillis();
    if (local_procs == 1) {
      for (String line; (line = reader.readLine()) != null; translationId++) {
        String[] tokens = line.split("\\s+");
        if (tokens.length > maxSentenceSize) {
          System.err.printf("Skipping: %s\n", line);
          System.err.printf("Tokens: %d (max: %d)\n", tokens.length,
              maxSentenceSize);
          continue;
        }
        if (tokens.length < minSentenceSize) {
          System.err.printf("Skipping: %s\n", line);
          System.err.printf("Tokens: %d (min: %d)\n", tokens.length,
              minSentenceSize);
          continue;
        }

        int lineNumber = reader.getLineNumber();
        RichTranslation<IString, String> translation = decodeOnly(tokens,
            translationId, lineNumber, 0);

        // display results
        if (translation != null) {
          // notice we reproduce the lameness of moses in that an extra space is
          // inserted after each translation
          System.out.printf("%s \n", translation.translation);
          System.err.printf("Final Translation: %s\n", translation.translation);
          System.err.printf("Score: %f\n", translation.score);
        } else {
          System.out.println("<<<decoder failure>>>");
        }
      }
    } else {

      List<List<String>> lines = new ArrayList<List<String>>();
      List<List<Integer>> ids = new ArrayList<List<Integer>>();

      for (int i = 0; i < local_procs; i++) {
        lines.add(new ArrayList<String>());
        ids.add(new ArrayList<Integer>());
      }

      for (String line; (line = reader.readLine()) != null; translationId++) {
        lines.get(translationId % local_procs).add(line);
        ids.get(translationId % local_procs).add(translationId);
      }

      List<Thread> threads = new ArrayList<Thread>();
      for (int i = 0; i < local_procs; i++) {
        threads.add(new Thread(new ProcDecode(i, lines.get(i), ids.get(i))));
        threads.get(i).start();
      }

      for (int i = 0; i < local_procs; i++) {
        try {
          threads.get(i).join();
        } catch (InterruptedException e) {
          System.err.printf("Interrupted while waiting for thread %d\n", i);
        }
      }

    }
    long totalTime = System.currentTimeMillis() - startTime;
    System.err.printf("Total Decoding time: %.3f\n", totalTime / 1000.0);

    if (nbestListWriter != null) {
      System.err.printf("Closing n-best writer\n");
      nbestListWriter.close();
    }
  }

  public RichTranslation<IString, String> decodeOnly(String[] tokens,
      int translationId, int lineNumber, int procid) throws IOException {

    Sequence<IString> foreign = new SimpleSequence<IString>(true,
        IStrings.toSyncIStringArray(tokens));
    // log foreign sentence
    synchronized (System.err) {
      System.err.printf("Translating(%d): %s\n", procid, foreign);
    }

    // do translation
    long startTime = System.currentTimeMillis();
    RichTranslation<IString, String> translation;

    ConstrainedOutputSpace<IString, String> constrainedOutputSpace = (constrainedToRefs == null ? null
        : new EnumeratedConstrainedOutputSpace<IString, String>(
            constrainedToRefs.get(translationId),
            phraseGenerator.longestForeignPhrase()));

    if (nbestListSize == -1) {
      translation = inferers.get(procid).translate(
          foreign,
          lineNumber - 1,
          constrainedOutputSpace,
          (constrainedOutputSpace == null ? null : constrainedOutputSpace
              .getAllowableSequences()));
    } else {
      List<RichTranslation<IString, String>> translations = inferers
          .get(procid).nbest(
              foreign,
              lineNumber - 1,
              constrainedOutputSpace,
              (constrainedOutputSpace == null ? null : constrainedOutputSpace
                  .getAllowableSequences()), nbestListSize);
      if (translations != null) {
        translation = translations.get(0);

        StringBuilder sb = new StringBuilder(translations.size() * 500); // initialize
                                                                         // it
                                                                         // as
                                                                         // reasonably
                                                                         // large
        for (RichTranslation<IString, String> tran : translations) {
          if (generateMosesNBestList) {
            tran.nbestToMosesStringBuilder(translationId, sb);
          } else {
            tran.nbestToStringBuilder(translationId, sb);
          }
          sb.append('\n');
        }
        synchronized (nbestListWriter) {
          nbestListWriter.append(sb.toString());
        }
      } else {
        translation = null;
      }
    }
    long translationTime = System.currentTimeMillis() - startTime;

    // log additional information to stderr
    synchronized (System.err) {
      if (translation != null) {
        System.err.printf("Best Translation: %s\n", translation.translation);
        System.err.printf("Final score: %.3f\n", (float) translation.score);
        if (translation.foreignCoverage != null) {
          System.err.printf("Coverage: %s\n", translation.foreignCoverage);
          System.err.printf(
              "Foreign words covered: %d (/%d)  - %.3f %%\n",
              translation.foreignCoverage.cardinality(),
              foreign.size(),
              translation.foreignCoverage.cardinality() * 100.0
                  / foreign.size());
        } else {
          System.err.println("Coverage: {}");
        }
      } else {
        System.err.println("No best Translation: <<<decoder failure>>>");
      }

      System.err.printf("Time: %f seconds\n", translationTime / (1000.0));
    }

    return translation;
  }

  public static int MAX_LEARN_NBEST_ITER = 100;

  // public static int LEARNING_NBEST_LIST_SIZE = 1000;

  static List<ScoredFeaturizedTranslation<IString, String>> filterLowScoring(
      List<ScoredFeaturizedTranslation<IString, String>> oracleEvalTranslations,
      double dropFrac) {
    List<ScoredFeaturizedTranslation<IString, String>> filtered = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
        oracleEvalTranslations.size());
    double[] scores = new double[oracleEvalTranslations.size()];
    int scoreI = 0;
    int nullCount = 0;
    for (ScoredFeaturizedTranslation<IString, String> tran : oracleEvalTranslations) {
      scores[scoreI++] = (tran == null ? Double.NEGATIVE_INFINITY : tran.score);
      if (tran == null)
        nullCount++;
    }
    Arrays.sort(scores);
    int effectiveLen = (scores.length - nullCount);
    int cutPoint = scores.length - effectiveLen
        + (int) (effectiveLen * dropFrac);
    double cutValue = scores[cutPoint];
    int filterCnt = 0;
    System.err.printf("Cut Point: %d Cut Value: %f\n", cutPoint, cutValue);
    for (ScoredFeaturizedTranslation<IString, String> tran : oracleEvalTranslations) {
      if (tran == null) {
        filtered.add(null);
      } else if (tran.score >= cutValue) {
        filtered.add(tran);
      } else {
        filtered.add(null);
        filterCnt++;
      }
    }
    System.err
        .printf(
            "Translation Filter (bottom %.3f %%) : original non-null: %d filter count: %d\n",
            dropFrac * 100, scores.length - nullCount, filterCnt);
    return filtered;
  }

  static List<ScoredFeaturizedTranslation<IString, String>> filterHighLowScoring(
      List<ScoredFeaturizedTranslation<IString, String>> oracleEvalTranslations,
      double dropFracTop, double dropFracBottom) {
    List<ScoredFeaturizedTranslation<IString, String>> filtered = new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
        oracleEvalTranslations.size());
    double[] scores = new double[oracleEvalTranslations.size()];
    int scoreI = 0;
    int nullCount = 0;
    for (ScoredFeaturizedTranslation<IString, String> tran : oracleEvalTranslations) {
      scores[scoreI++] = (tran == null ? Double.POSITIVE_INFINITY : tran.score);
      if (tran == null)
        nullCount++;
    }
    Arrays.sort(scores);
    int effectiveLen = (scores.length - nullCount);
    int cutPointTop = effectiveLen - 1 - (int) (dropFracTop * effectiveLen);
    int cutPointBottom = (int) (effectiveLen * dropFracBottom);
    double cutValueBottom = scores[cutPointBottom];
    double cutValueTop = scores[cutPointTop];
    int filterCnt = 0;

    for (ScoredFeaturizedTranslation<IString, String> tran : oracleEvalTranslations) {
      if (tran == null) {
        filtered.add(null);
      } else if (tran.score >= cutValueBottom && tran.score <= cutValueTop) {
        filtered.add(tran);
      } else {
        filtered.add(null);
        filterCnt++;
      }
    }
    System.err
        .printf(
            "Translation Filter (top: %.3f %% bottom %.3f %%) : original non-null: %d filter count: %d\ndr",
            dropFracTop * 100, dropFracBottom * 100, scores.length - nullCount,
            filterCnt);
    return filtered;
  }

  private interface Learner extends Scorer<String> {
    void weightUpdate(int epoch, int id,
        RichTranslation<IString, String> target,
        RichTranslation<IString, String> argmax, double loss);

    public void saveWeights(String filename) throws IOException;
  }

  private static class PerceptronLearner implements Learner {
    private final double[] lrate;
    OAIndex<String> featureIndex = new OAIndex<String>();
    double[] weights = new double[0];
    static final double DEFAULT_WT = 0.001;

    public PerceptronLearner(double lrate[]) {
      this.lrate = lrate;
    }

    void addMulWeight(String name, double m, double b) {
      int idx = featureIndex.indexOf(name, true);
      if (idx >= weights.length) {
        double[] newWeights = new double[(idx + 1) * 2];
        System.arraycopy(weights, 0, newWeights, 0, weights.length);

        for (int i = weights.length; i < newWeights.length; i++) {
          newWeights[i] = DEFAULT_WT;
        }

        weights = newWeights;
      }
      weights[idx] = m * weights[idx] + b;
    }

    @Override
    public void weightUpdate(int epoch, int id,
        RichTranslation<IString, String> target,
        RichTranslation<IString, String> argmax, double loss) {
      if (VERBOSE_LEARNER)
        System.err.printf("Target features:\n");
      ClassicCounter<String> tVec = new ClassicCounter<String>();
      for (FeatureValue<String> feature : target.features) {
        tVec.incrementCount(feature.name, feature.value);
      }

      for (Map.Entry<String, Double> e : tVec.entrySet()) {
        addMulWeight(e.getKey(), 1.0, lrate[Math.min(epoch, lrate.length - 1)]
            * e.getValue());
        if (VERBOSE_LEARNER)
          System.err.printf("\t%s +%f\n", e.getKey(), e.getValue());
      }

      if (VERBOSE_LEARNER)
        System.err.printf("Argmax features\n");
      ClassicCounter<String> aVec = new ClassicCounter<String>();
      for (FeatureValue<String> feature : argmax.features) {
        aVec.incrementCount(feature.name, feature.value);
      }
      for (Map.Entry<String, Double> e : aVec.entrySet()) {
        addMulWeight(e.getKey(), 1.0, -lrate[Math.min(epoch, lrate.length - 1)]
            * e.getValue());
        if (VERBOSE_LEARNER)
          System.err.printf("\t%s +%f\n", e.getKey(), e.getValue());
      }
      int ldIdx = featureIndex.indexOf("LinearDistortion");
      if (weights[ldIdx] < 0)
        weights[ldIdx] = 0;
    }

    @Override
    public double getIncrementalScore(Collection<FeatureValue<String>> features) {
      double score = 0;

      for (FeatureValue<String> feature : features) {
        int index = featureIndex.indexOf(feature.name);
        if (index >= 0)
          score += weights[index] * feature.value;
        else
          score += DEFAULT_WT * feature.value;
      }

      return score;
    }

    @Override
    public void saveWeights(String filename) throws IOException {
      System.err.printf("Saving weights to: %s\n", filename);
      BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
      PriorityQueue<ComparableWtPair> q = new PriorityQueue<ComparableWtPair>();

      for (String featureName : featureIndex.keySet()) {
        int idx = featureIndex.indexOf(featureName);
        double value;
        if (idx < 0 || idx >= weights.length) {
          value = 0;
        } else {
          value = weights[idx];
        }
        if (value == 0)
          continue;
        q.add(new ComparableWtPair(featureName, value));
      }
      for (ComparableWtPair cwp = q.poll(); cwp != null; cwp = q.poll()) {
        writer.append(cwp.featureName).append(" ")
            .append(String.format("%e", cwp.value)).append("\n");
      }
      writer.close();
    }
  }

  private static class ComparableWtPair implements Comparable<ComparableWtPair> {
    String featureName;
    double value;

    public ComparableWtPair(String featureName, double value) {
      this.featureName = featureName;
      this.value = value;
    }

    @Override
    public int compareTo(ComparableWtPair o) {
      int signum = (int) Math.signum(Math.abs(o.value) - Math.abs(this.value));
      if (signum != 0)
        return signum;
      return this.featureName.compareTo(o.featureName);
    }
  }

  private static class AvgPerceptronLearner implements Learner {
    double[] lrate;
    ClassicCounter<String> wts = new ClassicCounter<String>();
    ClassicCounter<String> wtsSum = new ClassicCounter<String>();
    int updateCount = 0;

    public AvgPerceptronLearner(double lrate[]) {
      this.lrate = lrate;
      wts.setDefaultReturnValue(0.1);
    }

    @Override
    public void saveWeights(String filename) throws IOException {
      BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

      for (Pair<String, Double> p : Counters
          .toDescendingMagnitudeSortedListWithCounts(wtsSum)) {
        writer.append(p.first).append(" ")
            .append((new Double(p.second / updateCount)).toString())
            .append("\n");
      }
      writer.close();
    }

    @Override
    public void weightUpdate(int epoch, int id,
        RichTranslation<IString, String> target,
        RichTranslation<IString, String> argmax, double loss) {
      for (FeatureValue<String> feature : target.features) {
        if (!wts.containsKey(feature.name))
          wts.setCount(feature.name, 0.1);
        wts.incrementCount(feature.name,
            lrate[Math.min(epoch, lrate.length - 1)] * feature.value);
      }

      for (FeatureValue<String> feature : argmax.features) {
        if (!wts.containsKey(feature.name))
          wts.setCount(feature.name, 0.1);
        wts.incrementCount(feature.name,
            -lrate[Math.min(epoch, lrate.length - 1)] * feature.value);
      }

      wtsSum.addAll(wts);
      updateCount++;
    }

    @Override
    public double getIncrementalScore(Collection<FeatureValue<String>> features) {
      double sum = 0;

      for (FeatureValue<String> feature : features) {
        sum += feature.value * wts.getCount(feature.name);
      }

      return sum;
    }
  }

  private static class MiraLearner implements Learner {
    ClassicCounter<String> wts = new ClassicCounter<String>();
    final double C;

    public MiraLearner(double C) {
      this.C = C;
    }

    @Override
    public void saveWeights(String filename) throws IOException {
      BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

      for (Pair<String, Double> p : Counters
          .toDescendingMagnitudeSortedListWithCounts(wts)) {
        writer.append(p.first).append(" ").append(p.second.toString())
            .append("\n");
      }
      writer.close();
    }

    @Override
    public void weightUpdate(int epoch, int id,
        RichTranslation<IString, String> target,
        RichTranslation<IString, String> argmax, double loss) {
      ClassicCounter<String> diffNorm = new ClassicCounter<String>();
      for (FeatureValue<String> fv : target.features) {
        diffNorm.incrementCount(fv.name, fv.value);
      }

      for (FeatureValue<String> fv : target.features) {
        diffNorm.incrementCount(fv.name, -fv.value);
      }

      double diffNormL2 = Counters.L2Norm(diffNorm);
      double scoreDiff = this.getIncrementalScore(target.features)
          - this.getIncrementalScore(argmax.features);
      double alpha = Math.max(0, Math.min(C, (loss - scoreDiff) / diffNormL2));

      for (FeatureValue<String> feature : target.features) {
        if (!wts.containsKey(feature.name))
          wts.setCount(feature.name, 0.1);
        wts.incrementCount(feature.name, alpha * feature.value);
      }

      for (FeatureValue<String> feature : argmax.features) {
        if (!wts.containsKey(feature.name))
          wts.setCount(feature.name, 0.1);
        wts.incrementCount(feature.name, alpha * feature.value);
      }
    }

    @Override
    public double getIncrementalScore(Collection<FeatureValue<String>> features) {
      double sum = 0;

      for (FeatureValue<String> feature : features) {
        sum += feature.value * wts.getCount(feature.name);
      }

      return sum;
    }

  }


  private static class SGDLogLinearLearner implements Learner {
    ClassicCounter<String> wts = new ClassicCounter<String>();
    final double R;
    final double[] lrate;

    public SGDLogLinearLearner(double r, double lrate[]) {
      this.R = r;
      this.lrate = lrate;
    }

    @Override
    public void saveWeights(String filename) throws IOException {
      BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

      for (Pair<String, Double> p : Counters
          .toDescendingMagnitudeSortedListWithCounts(wts)) {
        writer.append(p.first).append(" ").append(p.second.toString())
            .append("\n");
      }
      writer.close();
    }

    @Override
    public void weightUpdate(int epoch, int id,
        RichTranslation<IString, String> target,
        RichTranslation<IString, String> argmax, double loss) {
      throw new RuntimeException();
    }

    public void weightUpdate(
        int epoch, // int id,
        List<RichTranslation<IString, String>> target,
        List<RichTranslation<IString, String>> argmax) {// , double loss) {
      double tZ = 0;
      double aZ = 0;
      int tInfs = 0;
      int aInfs = 0;

      for (RichTranslation<IString, String> trans : target) {
        double v;
        tZ += v = Math.exp(getIncrementalScore(trans.features));
        if (v == Double.POSITIVE_INFINITY) {
          tInfs++;
        }
      }

      for (RichTranslation<IString, String> trans : argmax) {
        double v;
        aZ += v = Math.exp(getIncrementalScore(trans.features));
        if (v == Double.POSITIVE_INFINITY) {
          aInfs++;
        }
      }

      System.out.printf("tZ is approximated as (infs: %f): %d\n", tZ, tInfs);
      System.out.printf("aZ is approximated as (infs: %f): %d\n", aZ, aInfs);

      Set<String> featureNames = new HashSet<String>(wts.keySet());

      // E_p(H|F,E) [f]
      ClassicCounter<String> expectedCountsH_g_E_F = new ClassicCounter<String>();
      for (RichTranslation<IString, String> trans : target) {
        double n = Math.exp(getIncrementalScore(trans.features));
        double p;
        if (tZ == Double.POSITIVE_INFINITY) {
          if (n == tZ)
            p = 1.0 / tInfs;
          else
            p = 0.0;
        } else {
          p = n / tZ;
        }
        for (FeatureValue<String> fv : trans.features) {
          expectedCountsH_g_E_F.incrementCount(fv.name, fv.value * p);
          featureNames.add(fv.name);
        }
      }

      // E_p(E, H|F) [f]
      ClassicCounter<String> expectedCountsE_H_g_F = new ClassicCounter<String>();
      for (RichTranslation<IString, String> trans : argmax) {
        double n = Math.exp(getIncrementalScore(trans.features));
        double p;
        if (aZ == Double.POSITIVE_INFINITY) {
          if (n == aZ)
            p = 1.0 / aInfs;
          else
            p = 0.0;
        } else {
          p = n / aZ;
        }
        for (FeatureValue<String> fv : trans.features) {
          expectedCountsE_H_g_F.incrementCount(fv.name, fv.value * p);
          featureNames.add(fv.name);
        }
      }

      // delta w
      ClassicCounter<String> dW = new ClassicCounter<String>();
      for (String featureName : featureNames) {
        double dw = expectedCountsH_g_E_F.getCount(featureName)
            - expectedCountsE_H_g_F.getCount(featureName) - R
            * wts.getCount(featureName);
        dW.incrementCount(featureName, dw);
        System.err.printf(
            "%s:\n   E_p(h|e,f):%e - E_p(e,h|f)%e - R*w:%e = %e\n",
            featureName, expectedCountsH_g_E_F.getCount(featureName),
            expectedCountsE_H_g_F.getCount(featureName),
            R * wts.getCount(featureName), dw);
      }

      for (String featureName : featureNames) {
        wts.incrementCount(featureName,
            lrate[Math.min(epoch, lrate.length - 1)] * dW.getCount(featureName));
      }
    }

    @Override
    public double getIncrementalScore(Collection<FeatureValue<String>> features) {
      double sum = 0;

      for (FeatureValue<String> feature : features) {
        sum += feature.value * wts.getCount(feature.name);
      }

      return sum;
    }
  }

  private void learningLoop(Learner learner, String inputFilename,
      int maxEpoch, String saveWeights) throws IOException {

    for (int epoch = 0; epoch < maxEpoch; epoch++) {
      LineNumberReader reader = new LineNumberReader(new InputStreamReader(
              new FileInputStream(inputFilename), "UTF-8")); // = null;
      int translationId = -1;

      IncrementalEvaluationMetric<IString, String> incEval = evalMetric
          .getIncrementalMetric();
      for (String line; (line = reader.readLine()) != null; ) {
        translationId++;
        String[] tokens = line.split("\\s+");
        Sequence<IString> foreign = new SimpleSequence<IString>(true,
            IStrings.toSyncIStringArray(tokens));
        List<RichTranslation<IString, String>> targetNBest = null;
        List<RichTranslation<IString, String>> argmaxNBest;

        System.err.printf("Source: %s\n", foreign);
        RichTranslation<IString, String> target;
        if (learningTarget == LearningTarget.BEST_ON_N_BEST_LIST) {
          argmaxNBest = inferers.get(0).nbest(learner, foreign, translationId,
              null, null, 1000);
          target = listArgMax(argmaxNBest, incEval, translationId);
        } else {
          System.err.println("Attempting to generate target");
          System.err.println("==========================================");

          ConstrainedOutputSpace<IString, String> constrainedOutputSpace = new EnumeratedConstrainedOutputSpace<IString, String>(
              learnFromReferences.get(translationId),
              phraseGenerator.longestForeignPhrase());
          long goldTime = -System.currentTimeMillis();

          if (learner instanceof SGDLogLinearLearner) {
            targetNBest = inferers.get(0).nbest(learner, foreign,
                translationId, constrainedOutputSpace,
                learnFromReferences.get(translationId), 1000);
            if (targetNBest == null) {
              System.err.println("Can't generate reference, skipping....");
              continue;
            }
            target = targetNBest.get(0);
          } else {
            target = inferers.get(0).translate(learner, foreign, translationId,
                constrainedOutputSpace, learnFromReferences.get(translationId));

            if (target == null) {
              System.err.println("Can't generate reference, skipping....");
              continue;
            }
          }
          goldTime += System.currentTimeMillis();
          System.err.printf("Forced decoding time: %f s\n", goldTime / 1000.0);

          System.err.println("Generating model translation n-best list");
          System.err.println("==========================================");
          long argmaxTime = -System.currentTimeMillis();
          argmaxNBest = inferers.get(0).nbest(learner, foreign, translationId,
              null, null, 1000);
          argmaxTime += System.currentTimeMillis();
          System.err
              .printf("Argmax decoding time: %f s\n", argmaxTime / 1000.0);
        }

        RichTranslation<IString, String> argmax = argmaxNBest.get(0);

        incEval.replace(translationId, target);
        double evalTarget = optionalSmoothScoring(incEval, translationId,
            target);

        System.err.printf("Target: %s\n", target.translation);
        System.err.printf("Target Score: %f Smooth Score: %f\n",
            incEval.score(), evalTarget);

        double evalArgmax = optionalSmoothScoring(incEval, translationId,
            argmax);
        System.err.printf("Argmax: %s\n", argmax.translation);
        System.err.printf("Argmax Score: %f Smooth Score: %f\n",
            incEval.score(), evalArgmax);

        double loss = evalTarget - evalArgmax;
        if (learner instanceof SGDLogLinearLearner) {
          ((SGDLogLinearLearner) learner).weightUpdate(epoch, /* translationId, */
          targetNBest, argmaxNBest); // , loss);
        } else {
          learner.weightUpdate(epoch, translationId, target, argmax, loss);
        }
        if (translationId % 50 == 0) {
          learner.saveWeights(saveWeights + ".epoch." + epoch + ".tran."
              + translationId + ".wts");
        }
      }
      reader.close();
      learner.saveWeights(saveWeights + ".epoch." + epoch);
      System.err.printf("--> epoch %d score: %f\n", epoch, incEval.score());
    }
    System.err.printf("Saving weights to %s\n", saveWeights);
    learner.saveWeights(saveWeights);
  }

  private static double optionalSmoothScoring(
      IncrementalEvaluationMetric<IString, String> incEval, int pos,
      RichTranslation<IString, String> trans) {
    return (incEval instanceof HasSmoothScore ? ((HasSmoothScore) incEval
        .replace(pos, trans)).smoothScore() : incEval.replace(pos, trans)
        .score());
  }

  private static RichTranslation<IString, String> listArgMax(
      List<RichTranslation<IString, String>> list,
      IncrementalEvaluationMetric<IString, String> incEval, int pos) {
    double best = Double.NEGATIVE_INFINITY;
    RichTranslation<IString, String> bestTrans = null;
    for (RichTranslation<IString, String> trans : list) {
      double score = optionalSmoothScoring(incEval, pos, trans);
      if (score > best) {
        best = score;
        bestTrans = trans;
      }
    }
    return bestTrans;
  }

  /*
   * @SuppressWarnings("unchecked") private void learnWeights(String
   * inputFilename) throws IOException {
   *
   * double maxEvalScore = Double.NaN; NBestListContainer<IString, String>
   * nbestLists = null;
   *
   * int chunkSize = (learningAlgorithm.equals(EVALUE_LEARNING)? 1 : 5);
   *
   * LineNumberReader reader = null; int translationId = 0; for (int nbestIter =
   * 0; nbestIter < MAX_LEARN_NBEST_ITER; nbestIter++) {
   * IncrementalEvaluationMetric<IString, String> actualPostIncEval =
   * evalMetric.getIncrementalMetric(); IncrementalEvaluationMetric<IString,
   * String> actualPreIncEval = evalMetric.getIncrementalMetric();
   *
   * double initialEvalSum = 0; double postEvalSum = 0; int evalCount = 0;
   * boolean doneStream = false; reader = new LineNumberReader(new
   * InputStreamReader(new FileInputStream(inputFilename), "UTF-8"));
   * translationId = 0;
   *
   * double initialCScore = 0; for (int chunk = 0; !doneStream; chunk++) {
   *
   * String nbestFilename; if (!learningAlgorithm.equals(EVALUE_LEARNING)) {
   * nbestFilename = String.format("%s.nbest.c%d.%d", saveWeights, chunk,
   * nbestIter); } else { nbestFilename = String.format("/tmp/%s.nbest.c%d.%d",
   * saveWeights.replaceAll("[^A-Za-z0-9]","_"), chunk, nbestIter); }
   *
   * System.err.printf("Generating n-best list to: %s\n", nbestFilename); //
   * Generate new nbest list System.err.printf("n-best list iter: %d\n",
   * nbestIter); System.err.printf("Generating n-best list: %s\n",
   * nbestFilename);
   *
   * // if (nbestIter < -1) { BufferedWriter nbestListWriter = new
   * BufferedWriter(new FileWriter(nbestFilename));
   *
   * int skipped = 0; int included = 0;
   *
   * long decodingTime = -System.currentTimeMillis(); int
   * foreignTokensTranslated = 0; for (String line; included < chunkSize;
   * translationId++, included++) { line = reader.readLine(); if (line == null)
   * { reader.close(); doneStream = true; break; }
   *
   * String[] tokens = line.split("\\s+"); foreignTokensTranslated +=
   * tokens.length; if (tokens.length > maxSentenceSize) {
   * System.err.printf("Skipping: %s\n", line);
   * System.err.printf("Tokens: %d (Max: %d)\n", tokens.length,
   * maxSentenceSize); skipped++; continue; }
   *
   * if (tokens.length < minSentenceSize) { System.err.printf("Skipping: %s\n",
   * line); System.err.printf("Tokens: %d (Min: %d)\n", tokens.length,
   * minSentenceSize); skipped++; continue; }
   *
   * Sequence<IString> foreign = new SimpleSequence<IString>(true,
   * IStrings.toIStringArray(tokens));
   *
   * // log foreign sentence System.err.printf("Translating(%d): %s\n",
   * reader.getLineNumber(), foreign); long translationTime =
   * -System.currentTimeMillis(); // scorer.setRandomizeTag(nbestIter == 0);
   *
   * List<RichTranslation<IString, String>> translations = new
   * ArrayList(LEARNING_NBEST_LIST_SIZE); List<List<RichTranslation<IString,
   * String>>> nbestNBad = null;
   *
   *
   * if (nbestIter == 0 && !learningAlgorithm.equals(EVALUE_LEARNING)) {
   * scorer.setWeightMultipliers(1.0, 0.0);
   * System.err.printf("Doing Manual Weight Decode.\n"); nbestNBad =
   * ((AbstractBeamInferer) inferers.get(0)).nbestNBad(foreign,
   * reader.getLineNumber() - 1, null, LEARNING_NBEST_LIST_SIZE, 0);
   * translations.addAll(nbestNBad.get(0));
   * translations.addAll(nbestNBad.get(1)); scorer.setWeightMultipliers(0.0,
   * 1.0); }
   *
   * if (!(nbestIter == 0 && chunk == 0) ||
   * learningAlgorithm.equals(EVALUE_LEARNING)) { nbestNBad =
   * ((AbstractBeamInferer) inferers.get(0)) .nbestNBad(foreign,
   * reader.getLineNumber() - 1, null, LEARNING_NBEST_LIST_SIZE, 0); }
   *
   * translations.addAll(nbestNBad.get(0));
   * translations.addAll(nbestNBad.get(1));
   *
   * translationTime += System.currentTimeMillis(); System.err.printf(
   * "Foreign length: %d Argmax Translation length: %s Translation time: %.3f s\n"
   * , foreign.size(), (translations == null ? "NA" : translations
   * .get(0).translation.size()), translationTime / (1000.0)); if (translations
   * != null) { System.err.printf("Arg-max translation:%s\n\n",
   * translations.get(0).translation); for (RichTranslation<IString, String>
   * tran : translations) {
   * nbestListWriter.append(tran.nbestToString(translationId)).append( "\n"); }
   * } else { System.err.printf("<<<decoder failure>>>\n"); } } decodingTime +=
   * System.currentTimeMillis();
   *
   * if (included == 0) continue; nbestListWriter.close();
   *
   * if (skipped == translationId) { throw new RuntimeException(String
   * .format("Error: all foreign sentences skipped")); } // } // perform loss
   * augmented inference over n-best list until convergence
   * System.err.printf("Loading n-best list\n"); nbestLists = new
   * MosesNBestList(nbestFilename);
   *
   * int maxNbestListSize = 0; int minNbestListSize = Integer.MAX_VALUE; for
   * (List a : nbestLists.nbestLists()) { if (a.size() == 0) continue; if (a ==
   * null) continue; int aSz = a.size(); if (aSz > maxNbestListSize)
   * maxNbestListSize = aSz; if (aSz < minNbestListSize) minNbestListSize = aSz;
   * } System.err.printf("Largest  cummalative n-best list size: %d\n",
   * maxNbestListSize);
   * System.err.printf("Smallest cummalative n-best list size: %d\n",
   * minNbestListSize);
   *
   * int translations = 0; for (List<? extends
   * ScoredFeaturizedTranslation<IString, String>> transList : nbestLists
   * .nbestLists()) { if (transList.size() > 0) translations++; }
   *
   * System.err.printf("Translations in chunk: %d\n", translations);
   *
   * double l2Of1Best = 0; double scoreSum1Best = 0; double scoreSum1Worst = 0;
   * int outOf = 0; for (List<? extends ScoredFeaturizedTranslation<IString,
   * String>> nbestlist : nbestLists .nbestLists()) { if (nbestlist.size() == 0)
   * continue; for (FeatureValue<String> fv : nbestlist.get(0).features) {
   * l2Of1Best += fv.value * fv.value; } outOf++; scoreSum1Best +=
   * nbestlist.get(0).score; scoreSum1Worst += nbestlist.get(nbestlist.size() -
   * 1).score; } l2Of1Best = Math.sqrt(l2Of1Best); double scoreSumDiff =
   * scoreSum1Best - scoreSum1Worst;
   *
   * System.err.printf(
   * "Argmax cScore: %e N-best argmin cScore: %e (diff %f)\n", scoreSum1Best,
   * scoreSum1Worst, scoreSumDiff);
   *
   * EvaluationMetric<IString, String> bestScoreMetric = new
   * MarginRescaleEvaluationMetric( null, scorer);
   *
   * MultiTranslationMetricMax<IString, String> bestScoreSearch = new
   * HillClimbingMultiTranslationMetricMax<IString, String>( bestScoreMetric);
   * MultiTranslationMetricMax<IString, String> oracleEvalSearch = new
   * HillClimbingMultiTranslationMetricMax<IString, String>( evalMetric);
   *
   * System.err.printf(
   * "Finding best scoring translations over cummulative n-best list...\n");
   * List<ScoredFeaturizedTranslation<IString, String>>
   * bestScoreTranslationsInit = bestScoreSearch.maximize(nbestLists);
   * System.err.printf("Done.\n");
   *
   * IncrementalEvaluationMetric<IString, String> initialEvalMetric =
   * evalMetric.getIncrementalMetric(); { int tI = 0; initialCScore = 0.0; int
   * posT = -1; for (ScoredFeaturizedTranslation<IString, String> trans :
   * bestScoreTranslationsInit) { posT++; if (actualPreIncEval.size() <= posT) {
   * actualPreIncEval.add(trans); } else { if (trans != null)
   * actualPreIncEval.replace(posT, trans); }
   *
   * initialEvalMetric.add(trans); if (trans != null) initialCScore +=
   * scorer.getIncrementalScore(trans.features); tI++; } }
   *
   * System.err.printf(
   * "Finding oracle translations over cummulative n-best list....\n");
   * List<ScoredFeaturizedTranslation<IString, String>> oracleEvalTranslations =
   * oracleEvalSearch.maximize(nbestLists); System.err.printf("Done.\n");
   * IncrementalEvaluationMetric<IString, String> oracleEvalMetric =
   * evalMetric.getIncrementalMetric();
   *
   * for (ScoredFeaturizedTranslation<IString, String> trans :
   * oracleEvalTranslations) { //System.err.printf("%s\n", (trans == null ?
   * trans : trans.translation)); oracleEvalMetric.add(trans); } double
   * oracleScore = oracleEvalMetric.score();
   *
   *
   *
   * initialEvalSum += initialEvalMetric.score(); evalCount++;
   * System.err.printf(
   * "> Init eS (%d:%d): %.2f (c: %.2e) Orcl: %.2f Avg: %.2f Actl eS: %.2f\n",
   * nbestIter, chunk, 100 initialEvalMetric.score(), initialCScore, 100 *
   * oracleScore, 100*initialEvalSum/evalCount, actualPreIncEval.score()*100);
   *
   * scorer.setWeightMultipliers(0.0, 1.0);
   *
   * long learningTime = -System.currentTimeMillis();
   *
   * if (learningAlgorithm.equals(EVALUE_LEARNING)) { EValueLearningScorer
   * eScorer = (EValueLearningScorer)scorer; int transIdx = -1;
   * IncrementalEvaluationMetric<IString, String> incEvalMetric =
   * learningMetric.getIncrementalMetric(); for (int i = 0; i <
   * nbestLists.nbestLists().size(); i++) { if
   * (nbestLists.nbestLists().get(i).size() != 0) { if (transIdx == -1) transIdx
   * = i; else throw new RuntimeException(); } incEvalMetric.add(null); }
   *
   * //nbestLists.nbestLists(); List<? extends
   * ScoredFeaturizedTranslation<IString, String>> sfTrans =
   * nbestLists.nbestLists().get(transIdx); List<List<FeatureValue<String>>>
   * featureVectors = new ArrayList<List<FeatureValue<String>>>(sfTrans.size());
   *
   * /* { int tI = -1; for (ScoredFeaturizedTranslation<IString, String> sfTran
   * : sfTrans) { tI++; double score =
   * -((TERMetric)learningMetric).calcTER(sfTran, transIdx); if (score >
   * trueOracle) { trueOracle = score; loc = tI; } } }
   * System.err.printf("true oracle: %f (%d)\n", trueOracle, loc); * / double[]
   * us = new double[sfTrans.size()]; //System.err.printf("nbest\n");
   * System.err.printf("eval scores (%d)\n", sfTrans.size()); for
   * (ScoredFeaturizedTranslation<IString, String> sfTran : sfTrans) {
   * incEvalMetric.replace(transIdx, sfTran); us[featureVectors.size()] =
   * incEvalMetric.score(); //System.err.printf("%d: %f\n",
   * featureVectors.size(), us[featureVectors.size()]);
   * featureVectors.add(sfTran.features); } if (DEBUG_LEVEL >= 2) {
   * System.err.printf("Old Weights\n"); eScorer.displayWts(); } double objInit
   * = eScorer.objectiveValue(featureVectors, us);
   * eScorer.wtUpdate(featureVectors, us, nbestIter+1); double objPost =
   * eScorer.objectiveValue(featureVectors, us);
   * System.err.printf("Obj Delta: %e (%e-%e)\n", objPost - objInit, objPost,
   * objInit);
   *
   * if (DEBUG_LEVEL >= 2) { System.err.printf("New Weights\n");
   * eScorer.displayWts(); } }
   *
   * learningTime += System.currentTimeMillis();
   *
   * List<ScoredFeaturizedTranslation<IString, String>> bestScoreTranslations =
   * bestScoreSearch .maximize(nbestLists);
   *
   * IncrementalEvaluationMetric<IString, String> bestScoringEvalMetric =
   * evalMetric.getIncrementalMetric();
   *
   * List<FeatureValue<String>> allSelectedFeatures = new
   * LinkedList<FeatureValue<String>>(); double finalCScore = 0; int posBT = -1;
   * for (ScoredFeaturizedTranslation<IString, String> trans :
   * bestScoreTranslations) { posBT++;
   *
   * if (actualPostIncEval.size() <= posBT) { actualPostIncEval.add(trans); }
   * else { if (trans != null) actualPostIncEval.replace(posBT, trans); }
   * bestScoringEvalMetric.add(trans); if (trans != null) {
   * allSelectedFeatures.addAll(trans.features); } }
   *
   * postEvalSum += bestScoringEvalMetric.score(); System.err.printf(
   * "> Post eS (%d:%d): %.2f (c: %.2e) Orcl: %.2f Avg: %.2f Actl eS: %.2f\n",
   * nbestIter, chunk, 100 bestScoringEvalMetric.score(), finalCScore, 100
   * oracleEvalMetric.score(), 100*postEvalSum/evalCount,
   * actualPostIncEval.score()*100); System.err.printf(
   * "> Time Summary Decoding: %.3f s Learning (incl loss infer): %.3f s\n",
   * decodingTime/1000.0, learningTime/1000.0);
   * System.err.printf("Max eval score: %f\n", maxEvalScore); if (chunk % 250 ==
   * 0) scorer.saveWeights(String.format("%s.nbestitr_%d.chunk_%d.wts",
   * saveWeights, nbestIter, chunk)); if (DEBUG_LEVEL >= 2) {
   * System.err.printf("Final Weights for nbestitr: %d chunk: %d", nbestIter,
   * chunk); scorer.displayWeights(); }
   *
   * } scorer.saveWeights(String.format("%s.nbestitr_%d.final.wts", saveWeights,
   * nbestIter)); System.err.printf(
   * ">> %d: Avg eS: %.2f~>%.2f  Actl eS: %.2f~>%.2f (diff: %.3f)\n", nbestIter,
   * 100*initialEvalSum/evalCount, 100*postEvalSum/evalCount,
   * actualPreIncEval.score()*100, actualPostIncEval.score()*100,
   * actualPostIncEval.score()*100 - actualPreIncEval.score()*100); } }
   */

  public void executiveLoop() throws IOException {
    if (learnWeights) {
      String inputFilename = saveWeights + ".in";
      LineNumberReader reader = new LineNumberReader(new InputStreamReader(
          System.in, "UTF-8"));
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
          new FileOutputStream(inputFilename), "UTF-8"));
      for (String line; (line = reader.readLine()) != null;) {
        writer.write(line);
        writer.write("\n");
      }
      reader.close();
      writer.close();

      if (learningAlgorithm.equals(PERCEPTRON_LEARNING)) {
        Learner learner = new PerceptronLearner(learningRate);
        learningLoop(learner, inputFilename, maxEpochs, saveWeights);
      } else if (learningAlgorithm.equals(AVG_PERCEPTRON_LEARNING)) {
        Learner learner = new AvgPerceptronLearner(learningRate);
        learningLoop(learner, inputFilename, maxEpochs, saveWeights);
      } else if (learningAlgorithm.equals(MIRA_LEARNING)) {
        Learner learner = new MiraLearner(maxMarginC);
        learningLoop(learner, inputFilename, maxEpochs, saveWeights);
      } else if (learningAlgorithm.equals(COST_MARGIN_LEARNING)) {
        Learner learner = new PerceptronLearner(learningRate);
        learningLoop(learner, inputFilename, maxEpochs, saveWeights);
      } else if (learningAlgorithm.equals(SGDLL)) {
        Learner learner = new SGDLogLinearLearner(0.5, learningRate);
        learningLoop(learner, inputFilename, maxEpochs, saveWeights);
      } else {
        throw new RuntimeException("Unrecognized learning algorithm");
      }
    } else {
      decodeFromConsole();
    }
  }

  public static void main(String[] args) throws Exception {

    if (args.length < 1) {
      System.err.println("Usage:\n\tjava ...Phrasal (model.ini)");
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
    initStaticMembers(config);
    Phrasal p = new Phrasal(config);
    FlatPhraseTable.lockIndex();
    p.executiveLoop();
    System.exit(0);
  }

}
