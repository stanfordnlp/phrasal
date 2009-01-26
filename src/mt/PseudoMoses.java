package mt;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;


import mt.base.*;
import mt.decoder.feat.*;
import mt.decoder.h.*;
import mt.decoder.inferer.*;
import mt.decoder.inferer.impl.MultiBeamDecoder;
import mt.decoder.recomb.*;
import mt.decoder.util.*;
import mt.decoder.efeat.SentenceBoundaryFeaturizer;
import mt.metrics.*;
import mt.tune.*;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.util.StringUtils;

/**
 * 
 * @author danielcer
 * 
 */
public class PseudoMoses {
	static public final String TRANSLATION_TABLE_OPT = "ttable-file";
	static public final String LANGUAGE_MODEL_OPT = "lmodel-file";
	static public final String OPTION_LIMIT_OPT = "ttable-limit";
	static public final String DISTORTION_WT_OPT = "weight-d";
	static public final String LANGUAGE_MODEL_WT_OPT = "weight-l";
	static public final String TRANSLATION_MODEL_WT_OPT = "weight-t";
	static public final String WORD_PENALTY_WT_OPT = "weight-w";
	static public final String INPUT_FACTORS_OPT = "input-factors";
	static public final String FACTOR_DELIM_OPT = "factor-delimiter";
	static public final String MAPPING_OPT = "mapping";
	static public final String NBEST_LIST_OPT = "n-best-list";
  static public final String MOSES_NBEST_LIST_OPT = "moses-n-best-list";
  static public final String CONSTRAIN_TO_REFS = "constrain-to-refs";
	static public final String PREFERED_REF_STRUCTURE = "use-prefered-ref-structure";
	static public final String LEARN_WEIGHTS_USING_REFS = "learn-weights-using-refs";
	static public final String PREFERED_REF_INTERNAL_STATE = "prefered-internal-state";
	static public final String LEARNING_ALGORITHM = "learning-algorithm";
	static public final String LEARNING_TARGET = "learning-target";
	static public final String SAVE_WEIGHTS = "save-weights";
	static public final String BEAM_SIZE = "stack";
	static public final String DISTORTION_FILE = "distortion-file";
	static public final String WEIGHTS_FILE = "weights-file";
  static public final String CONFIG_FILE = "config-file";
	static public final String USE_DISCRIMINATIVE_LM = "discriminative-lm";
	static public final String USE_DISCRIMINATIVE_TM = "discriminative-tm";
	static public final String MAX_SENTENCE_LENGTH = "max-sentence-length";
	static public final String MIN_SENTENCE_LENGTH = "min-sentence-length";
	static public final String FORCE_DECODE_ONLY = "force-decode-only";
	static public final String DISTORTION_LIMIT = "distortion-limit";
	static public final String ADDITIONAL_FEATURIZERS = "additional-featurizers";
	static public final String INLINE_WEIGHTS = "inline-weights";
	static public final String LEARNING_RATE = "lrate";
	static public final String MOMENTUM = "momentum";
	static public final String CONSTRAIN_MANUAL_WTS = "constrain-manual-wts";
	static public final String LOCAL_PROCS = "localprocs";
	static public final String ITER_LIMIT = "iter-limit";
	static public final String USE_ITG_CONSTRAINTS = "use-itg-constraints";
	static public final String EVAL_METRIC = "eval-metric";
	static public final String LEARNING_METRIC = "learning-metric";
	
	static public final int DEFAULT_DISCRIMINATIVE_LM_ORDER = 0;
	static public final boolean DEFAULT_DISCRIMINATIVE_TM_PARAMETER = false;
	static final Set<String> REQUIRED_FIELDS = new HashSet<String>();
	static final Set<String> OPTIONAL_FIELDS = new HashSet<String>();
	static final Set<String> IGNORED_FIELDS = new HashSet<String>();
	static final Set<String> ALL_RECOGNIZED_FIELDS = new HashSet<String>();


	static public double DEFAULT_LEARNING_RATE = 0.01;
	static public double DEFAULT_MOMENTUM_TERM = 0.9;
	static final int DEFAULT_LOCAL_PROCS = 1;

	static final boolean DO_PAIRED = Boolean.parseBoolean(System.getProperty("DO_PAIRED", "false"));
	

	static {
		REQUIRED_FIELDS.addAll(Arrays.asList(new String[] { TRANSLATION_TABLE_OPT,
				LANGUAGE_MODEL_OPT, DISTORTION_WT_OPT, LANGUAGE_MODEL_WT_OPT,
				TRANSLATION_MODEL_WT_OPT, WORD_PENALTY_WT_OPT }));
		OPTIONAL_FIELDS.addAll(Arrays.asList(new String[] { INLINE_WEIGHTS,ITER_LIMIT,
				DISTORTION_FILE, DISTORTION_LIMIT, ADDITIONAL_FEATURIZERS,
				USE_DISCRIMINATIVE_TM, FORCE_DECODE_ONLY, OPTION_LIMIT_OPT,
				NBEST_LIST_OPT, MOSES_NBEST_LIST_OPT,
        CONSTRAIN_TO_REFS, PREFERED_REF_STRUCTURE,
				LEARN_WEIGHTS_USING_REFS, LEARNING_ALGORITHM,
				PREFERED_REF_INTERNAL_STATE, SAVE_WEIGHTS, LEARNING_TARGET, BEAM_SIZE,
				WEIGHTS_FILE, USE_DISCRIMINATIVE_LM, MAX_SENTENCE_LENGTH,
				MIN_SENTENCE_LENGTH, CONSTRAIN_MANUAL_WTS, LEARNING_RATE, MOMENTUM, USE_ITG_CONSTRAINTS,
				LEARNING_METRIC, EVAL_METRIC, LOCAL_PROCS}));
		IGNORED_FIELDS.addAll(Arrays.asList(new String[] { INPUT_FACTORS_OPT,
				MAPPING_OPT, FACTOR_DELIM_OPT }));
		ALL_RECOGNIZED_FIELDS.addAll(REQUIRED_FIELDS);
		ALL_RECOGNIZED_FIELDS.addAll(OPTIONAL_FIELDS);
		ALL_RECOGNIZED_FIELDS.addAll(IGNORED_FIELDS);
	}
	
	static public final Map<String,Double> IDEALIZED_TARGETS = new HashMap<String,Double>();
	static {
		IDEALIZED_TARGETS.put("TM:lex(f|t)", 0.0);
		IDEALIZED_TARGETS.put("TM:lex(t|f)", 0.0);
		IDEALIZED_TARGETS.put("TM:phi(f|t)", 0.0);
		IDEALIZED_TARGETS.put("TM:phi(t|f)", 0.0);
		IDEALIZED_TARGETS.put("LM", 0.0);
	  IDEALIZED_TARGETS.put("LinearDistortion", 0.0);
	  IDEALIZED_TARGETS.put("LexR::discontinousWithNext", 0.0);
	  IDEALIZED_TARGETS.put("LexR::discontinousWithPrevious", 0.0);
	  IDEALIZED_TARGETS.put("LexR::monotoneWithNext", 0.0);
	  IDEALIZED_TARGETS.put("LexR::monotoneWithPrevious", 0.0);
	  IDEALIZED_TARGETS.put("LexR::swapWithNext", 0.0);
	  IDEALIZED_TARGETS.put("LexR::swapWithPrevious", 0.0);
	  IDEALIZED_TARGETS.put("UnknownWord", 0.0);
    IDEALIZED_TARGETS.put("SentenceBoundary", 0.0);
	}

	static public final String EVALUE_LEARNING = "evalue";
	static public final String DEFAULT_LEARNING_ALGORITHM = EVALUE_LEARNING;
	static public final String DEFAULT_SAVE_WEIGHTS = "unname_model_"
		+ System.currentTimeMillis();

	List<Inferer<IString, String>> inferers;
	Inferer<IString, String> refInferer;

	BufferedWriter nbestListWriter;
	int nbestListSize;
	String saveWeights;

	List<List<Sequence<IString>>> constrainedToRefs = null;

	boolean learnWeights;
  boolean constrainManualWeights;
  boolean generateMosesNBestList;
  boolean uniqNBestList;
  int local_procs = DEFAULT_LOCAL_PROCS;
  List<List<Sequence<IString>>> learnFromReferences;
	String learningAlgorithm;
	List<String> learningAlgorithmConfig;
	Scorer<String> scorer;
	NBestListContainer<IString, String> preferedInternalState;
	int maxSentenceSize = Integer.MAX_VALUE;
	int minSentenceSize = 0;
	
	double learningRate = DEFAULT_LEARNING_RATE;
	double momentumTerm = DEFAULT_MOMENTUM_TERM;

	double cTarget = 0.001;
	double cRisky =  0.010;
	
	public static enum LearningTarget {
		NONE, REVERSE_LOSS_INFERENCE, BEST_ON_N_BEST_LIST, ONE_CLASS,
	};

	public static LearningTarget DEFAULT_LEARNING_TARGET = LearningTarget.REVERSE_LOSS_INFERENCE;
	LearningTarget learningTarget = DEFAULT_LEARNING_TARGET;

	static final int DEBUG_LEVEL = 0;
	EvaluationMetric<IString,String> learningMetric = null;
	EvaluationMetric<IString,String> evalMetric = null;
	
	public static final Map<String, LearningTarget> configToLearningTarget = new HashMap<String, LearningTarget>();
	static {
		configToLearningTarget.put("best-on-n-best",
				LearningTarget.BEST_ON_N_BEST_LIST);
		configToLearningTarget.put("rloss-inference",
				LearningTarget.REVERSE_LOSS_INFERENCE);
		configToLearningTarget.put("one-class", LearningTarget.ONE_CLASS);
	}

  static Map<String, List<String>> readConfig(String filename) throws IOException {
		Map<String, List<String>> config = new HashMap<String, List<String>>();
		LineNumberReader reader = new LineNumberReader(new FileReader(filename));
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
  			for (; (line = reader.readLine()) != null;) {
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
  				for (String field : fields) {
  					entries.add(field);
  				}
  			}
  
  			if (entries.size() != 0)
  				config.put(key, entries);
  		}
		}
		return config;
	}

  static Map<String, List<String>> readArgs(String[] args) throws IOException {
    Map<String, List<String>> config = new HashMap<String, List<String>>();
    for(Map.Entry<Object,Object> e : StringUtils.argsToProperties(args).entrySet()) {
      String key = e.getKey().toString();
      String value = e.getValue().toString();
      if(CONFIG_FILE.equals(key)) {
        config.putAll(readConfig(value));
      } else {
        config.put(key,Arrays.asList(value));
      }
    }
    return config;
  }

  @SuppressWarnings("unchecked")
	public PseudoMoses(Map<String, List<String>> config) throws IOException,
	InstantiationException, IllegalAccessException, IllegalArgumentException,
	SecurityException, InvocationTargetException, NoSuchMethodException {
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

		Set<String> ignoredItems = new HashSet(config.keySet());
		ignoredItems.retainAll(IGNORED_FIELDS);

		for (String ignored : ignoredItems) {
			System.err.printf("Ignoring Moses field: %s\n", ignored);
		}

		if (config.containsKey(ITER_LIMIT)) {
			MAX_LEARN_NBEST_ITER = Integer.parseInt(config.get(ITER_LIMIT).get(0));
		}

		if (config.containsKey(CONSTRAIN_MANUAL_WTS)) {
			constrainManualWeights = Boolean.parseBoolean(config.get(CONSTRAIN_MANUAL_WTS).get(0));
		}
		
		if (config.containsKey(LEARNING_RATE)) {
			learningRate = Double.parseDouble(config.get(LEARNING_RATE).get(0));
		}
		
		if (config.containsKey(MOMENTUM)) {
			momentumTerm = Double.parseDouble(config.get(MOMENTUM).get(0));			
		}
		
		if (config.containsKey(LOCAL_PROCS)) {
			local_procs = Integer.parseInt(config.get(LOCAL_PROCS).get(0));
		}
		
		
		System.err.printf("C - Target: %e Risky: %e\n", cTarget, cRisky);
		
		if (config.containsKey(CONSTRAIN_TO_REFS)) {
			constrainedToRefs = Metrics.readReferences(config.get(CONSTRAIN_TO_REFS)
					.toArray(new String[0]));
		}

		if (config.containsKey(LEARNING_TARGET)) {
			List<String> strLearningTarget = config.get(LEARNING_TARGET);
			if (strLearningTarget.size() != 1) {
				throw new RuntimeException(String.format(
						"Parameter '%s' takes one and only one argument", LEARNING_TARGET));
			}
			learningTarget = configToLearningTarget.get(strLearningTarget.get(0));
		}

		int distortionLimit = -1;
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

		LexicalReorderingFeaturizer lexReorderFeaturizer = null;

		if (config.containsKey(DISTORTION_FILE)) {
			List<String> strDistortionFile = config.get(DISTORTION_FILE);
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
			lexReorderFeaturizer = new LexicalReorderingFeaturizer(
					new MosesLexicalReorderingTable(modelFilename, modelType));
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

		List<IncrementalFeaturizer<IString, String>> additionalFeaturizers = new ArrayList<IncrementalFeaturizer<IString, String>>();
		if (config.containsKey(ADDITIONAL_FEATURIZERS)) {
			List<String> tokens = config.get(ADDITIONAL_FEATURIZERS);
			String featurizerName = null;
			String args = null;
			for (String token : tokens) {
				if (featurizerName == null) {
					if (token.endsWith("()")) {
						String name = token.replaceFirst("\\(\\)$", "");
						Class featurizerClass = FeaturizerFactory.loadFeaturizer(name);
						IncrementalFeaturizer<IString, String> featurizer = (IncrementalFeaturizer<IString, String>) featurizerClass
						.newInstance();
						additionalFeaturizers.add(featurizer);
					} else if (token.contains("(")) {
						if (token.endsWith(")")) {
							featurizerName = token.replaceFirst("\\(.*", "");
							args = token.replaceFirst("^.*\\(", "");
							args = args.substring(0, args.length() - 1);
							System.err.printf("featurizerName: %s\n", featurizerName);
							args = args.replaceAll("\\s*,\\s*", ",");
							args = args.replaceAll("^\\s+", "");
							args = args.replaceAll("\\s+$", "");
							String[] argsList = args.split(",");
							System.err.printf("args: %s\n", Arrays.toString(argsList));
							Class featurizerClass = FeaturizerFactory
							.loadFeaturizer(featurizerName);
							IncrementalFeaturizer<IString, String> featurizer = (IncrementalFeaturizer<IString, String>) featurizerClass
							.getConstructor(argsList.getClass()).newInstance(
									new Object[]{argsList});
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
						Class featurizerClass = FeaturizerFactory
						.loadFeaturizer(featurizerName);
						IncrementalFeaturizer<IString, String> featurizer = (IncrementalFeaturizer<IString, String>) featurizerClass
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
		String lgModel;
		if (config.get(LANGUAGE_MODEL_OPT).size() == 1) {
			lgModel = config.get(LANGUAGE_MODEL_OPT).get(0);
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
		featurizer = FeaturizerFactory.factory(
				FeaturizerFactory.PSEUDO_PHARAOH_GENERATOR, makePair(
						FeaturizerFactory.ARPA_LM_PARAMETER, lgModel), makePair(
								FeaturizerFactory.DISCRIMINATIVE_LM_PARAMETER, ""
								+ discriminativeLMOrder), makePair(
										FeaturizerFactory.DISCRIMINATIVE_TM_PARAMETER, ""
										+ discriminativeTMParameter));

		if (lexReorderFeaturizer != null) {
			additionalFeaturizers.add(lexReorderFeaturizer);
		}

		if (additionalFeaturizers.size() != 0) {
			List<IncrementalFeaturizer<IString, String>> allFeaturizers = new ArrayList<IncrementalFeaturizer<IString, String>>();
			allFeaturizers.addAll(featurizer.featurizers);
			allFeaturizers.addAll(additionalFeaturizers);
			featurizer = new CombinedFeaturizer<IString, String>(allFeaturizers);
		}

		// Create Scorer
		List<String> weightConfig = new LinkedList<String>();
		weightConfig.add(ScorerFactory.STATIC_SCORER_INLINE);

		if (config.containsKey(WEIGHTS_FILE)) {
			BufferedReader reader = new BufferedReader(new FileReader(config.get(
					WEIGHTS_FILE).get(0)));
			for (String line; (line = reader.readLine()) != null;) {
				String[] fields = line.split("\\s+");
				weightConfig.add(makePair(fields[0], fields[1]));
			}
			reader.close();
		} else if (config.containsKey(PREFERED_REF_STRUCTURE)) {
			weightConfig.add(makePair(LinearDistortionFeaturizer.FEATURE_NAME,
			"100.0"));
			List<String> tmodelWtsStr = config.get(TRANSLATION_MODEL_WT_OPT);
			if (tmodelWtsStr.size() == 5) {
				weightConfig.add(makePair(makePair(PhraseTableScoresFeaturizer.PREFIX,
						PharaohPhraseTable.FIVESCORE_LEX_t_f), "1.0"));
				weightConfig.add(makePair(makePair(PhraseTableScoresFeaturizer.PREFIX,
						PharaohPhraseTable.FIVESCORE_PHRASE_PENALTY), "-10.0"));
			} else if (tmodelWtsStr.size() == 1) {
				weightConfig.add(makePair(makePair(PhraseTableScoresFeaturizer.PREFIX,
						PharaohPhraseTable.ONESCORE_P_t_f), "1.0"));
			} else {
				throw new RuntimeException(String.format(
						"Unsupported weight count for translation model: %d", tmodelWtsStr
						.size()));
			}
		} else {
			if (config.containsKey(INLINE_WEIGHTS)) {
				List<String> inlineWts = config.get(TRANSLATION_MODEL_WT_OPT);
				for (String inlineWt : inlineWts) {
					String[] fields = inlineWt.split("=");
					weightConfig.add(makePair(fields[0], fields[1]));
				}
			}
			weightConfig.add(makePair(NGramLanguageModelFeaturizer.FEATURE_NAME,
					config.get(LANGUAGE_MODEL_WT_OPT).get(0)));
			weightConfig.add(makePair(LinearDistortionFeaturizer.FEATURE_NAME, config
					.get(DISTORTION_WT_OPT).get(0)));
			if (config.get(DISTORTION_WT_OPT).size() > 1) {
				int numAdditionalWts = config.get(DISTORTION_WT_OPT).size() - 1;
				if (lexReorderFeaturizer == null) {
					throw new RuntimeException(
							String
							.format(
									"Additional weights given for parameter %s but no lexical reordering file was specified",
									DISTORTION_WT_OPT));
				}
				if (numAdditionalWts != lexReorderFeaturizer.mlrt.positionalMapping.length) {
					throw new RuntimeException(
							String
							.format(
									"%d re-ordering weights given with parameter %s, but %d expected",
									numAdditionalWts, DISTORTION_WT_OPT,
									lexReorderFeaturizer.mlrt.positionalMapping.length));
				}
				for (int i = 0; i < lexReorderFeaturizer.mlrt.positionalMapping.length; i++) {
					weightConfig.add(makePair(lexReorderFeaturizer.featureTags[i], config
							.get(DISTORTION_WT_OPT).get(i + 1)));
				}
			}
			weightConfig.add(makePair(WordPenaltyFeaturizer.FEATURE_NAME, config.get(
					WORD_PENALTY_WT_OPT).get(0)));
			weightConfig.add(makePair(UnknownWordFeaturizer.FEATURE_NAME, "" + 1.0));
      weightConfig.add(makePair(SentenceBoundaryFeaturizer.FEATURE_NAME, "" + 1.0));

			List<String> tmodelWtsStr = config.get(TRANSLATION_MODEL_WT_OPT);
			if (tmodelWtsStr.size() == 5) {
				weightConfig.add(makePair(makePair(PhraseTableScoresFeaturizer.PREFIX,
						PharaohPhraseTable.FIVESCORE_PHI_t_f), tmodelWtsStr.get(0)));
				weightConfig.add(makePair(makePair(PhraseTableScoresFeaturizer.PREFIX,
						PharaohPhraseTable.FIVESCORE_LEX_t_f), tmodelWtsStr.get(1)));
				weightConfig.add(makePair(makePair(PhraseTableScoresFeaturizer.PREFIX,
						PharaohPhraseTable.FIVESCORE_PHI_f_t), tmodelWtsStr.get(2)));
				weightConfig.add(makePair(makePair(PhraseTableScoresFeaturizer.PREFIX,
						PharaohPhraseTable.FIVESCORE_LEX_f_t), tmodelWtsStr.get(3)));
				weightConfig.add(makePair(makePair(PhraseTableScoresFeaturizer.PREFIX,
						PharaohPhraseTable.FIVESCORE_PHRASE_PENALTY), tmodelWtsStr.get(4)));
			} else if (tmodelWtsStr.size() == 1) {
				weightConfig.add(makePair(makePair(PhraseTableScoresFeaturizer.PREFIX,
						PharaohPhraseTable.ONESCORE_P_t_f), tmodelWtsStr.get(0)));
			} else {
				throw new RuntimeException(String.format(
						"Unsupported weight count for translation model: %d", tmodelWtsStr
						.size()));
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
					LEARN_WEIGHTS_USING_REFS).toArray(new String[0]));
			learningMetric = (config.containsKey(LEARNING_METRIC) ? MetricFactory.metric(config.get(LEARNING_METRIC).get(0), learnFromReferences) : MetricFactory.metric(learnFromReferences));
			evalMetric = (config.containsKey(EVAL_METRIC) ? MetricFactory.metric(config.get(EVAL_METRIC).get(0), learnFromReferences) :  MetricFactory.metric(learnFromReferences));
		}

		if (config.containsKey(PREFERED_REF_INTERNAL_STATE)) {
			preferedInternalState = new MosesNBestList(config.get(
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
						"Argument %s to %s can not be parsed as an integer", config
						.get(MAX_SENTENCE_LENGTH), MAX_SENTENCE_LENGTH));
			}
		}

		if (config.containsKey(MIN_SENTENCE_LENGTH)) {
			try {
				minSentenceSize = Integer.parseInt(config.get(MIN_SENTENCE_LENGTH).get(
						0));
			} catch (NumberFormatException e) {
				throw new RuntimeException(String.format(
						"Argument %s to %s can not be parsed as an integer", config
						.get(MIN_SENTENCE_LENGTH), MIN_SENTENCE_LENGTH));
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

		System.err.printf("WeightConfig: '%s'\n", weightConfig);

		if (learnWeights) {
			Map<String, Double> weights = new HashMap<String, Double>();
			for (String pair : weightConfig) {
				if (pair.equals("inline"))
					continue;
				System.err.printf("%s\n", pair);
				String[] fields = pair.split(":");
				String featureName = fields[0];
				for (int fi = 1; fi < fields.length - 1; fi++)
					featureName += ":" + fields[fi];
				weights.put(featureName, Double.valueOf(fields[fields.length - 1]));
			}
			
			if (learningAlgorithm.equals(EVALUE_LEARNING)) {
				scorer = new EValueLearningScorer(weights, learningRate, momentumTerm); 
			}else {
				throw new RuntimeException(String.format(
						"Unrecognized learning algorithm: %s", learningAlgorithm));
			}
		} else {
			scorer = ScorerFactory.factory(ScorerFactory.STATIC_SCORER, weightConfig
					.toArray(new String[0]));
		}

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
      generateMosesNBestList = Boolean.parseBoolean(config.get(MOSES_NBEST_LIST_OPT).get(0));
    }

    String optionLimit = config.get(OPTION_LIMIT_OPT).get(0);
		System.err.printf("Phrase table: %s\n", phraseTable);
		PhraseGenerator<IString> phraseGenerator = (optionLimit == null ? PhraseGeneratorFactory
				.<String> factory(featurizer, scorer,
						PhraseGeneratorFactory.PSEUDO_PHARAOH_GENERATOR, phraseTable)
						: PhraseGeneratorFactory.<String> factory(featurizer, scorer,
								PhraseGeneratorFactory.PSEUDO_PHARAOH_GENERATOR, phraseTable,
								optionLimit));


		System.err.printf("Phrase Limit: %d\n",
				((CombinedPhraseGenerator) phraseGenerator).getPhraseLimit());

		// Create Recombination Filter
    RecombinationFilter<Hypothesis<IString, String>> filter = RecombinationFilterFactory
    .factory(featurizer.getNestedFeaturizers(), RecombinationFilterFactory.CLASSICAL_TRANSLATION_MODEL);

		// Create Search Heuristic
		IsolatedPhraseFeaturizer<IString, String> isolatedPhraseFeaturizer = featurizer;
		SearchHeuristic<IString, String> heuristic = HeuristicFactory.factory(
				isolatedPhraseFeaturizer, scorer,
				HeuristicFactory.ISOLATED_PHRASE_FOREIGN_COVERAGE);

		// Configure InfererBuilder
		MultiBeamDecoder.MultiBeamDecoderBuilder infererBuilder = (MultiBeamDecoder.MultiBeamDecoderBuilder) InfererBuilderFactory
		.factory(InfererBuilderFactory.MULTIBEAM_DECODER);
		infererBuilder.setIncrementalFeaturizer(featurizer);
		infererBuilder.setPhraseGenerator(phraseGenerator);
		infererBuilder.setScorer(scorer);
		infererBuilder.setSearchHeuristic(heuristic);
		infererBuilder.setRecombinationFilter(filter);
		infererBuilder.setBeamType(HypothesisBeamFactory.BeamType.sloppybeam);
		
		if (distortionLimit != -1) {
			infererBuilder.setMaxDistortion(distortionLimit);
		}
		
		if (config.containsKey(USE_ITG_CONSTRAINTS)) {
			infererBuilder.useITGConstraints(Boolean.parseBoolean(config.get(USE_ITG_CONSTRAINTS).get(0)));
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

		// Create Inferer
    inferers = new ArrayList<Inferer<IString, String>>(local_procs);
    for (int i = 0; i < local_procs; i++) {
		  inferers.add(infererBuilder.build());
    }

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
				throw new RuntimeException(String
						.format("%s size argmument, %d, must be > 0", NBEST_LIST_OPT,
								nbestListSize));
			}
			System.err.printf("Generating n-best lists to: %s (size: %d)\n",
					nbestListFilename, nbestListSize);
			nbestListWriter = new BufferedWriter(new FileWriter(nbestListFilename));
		} else {
			nbestListSize = -1;
			nbestListWriter = null;
		}
	}

	private String makePair(String label, String value) {
		return String.format("%s:%s", label, value);
	}

	double computeDelta(double[] oldWeights, double[] newWeights) {
		double ssdiff = 0;
		int max = Math.min(oldWeights.length, newWeights.length);
		for (int i = 0; i < max; i++) {
			double diff = oldWeights[i] - newWeights[i];
			ssdiff += diff * diff;
		}
		for (int i = max; i < oldWeights.length; i++) {
			ssdiff += oldWeights[i] * oldWeights[i];
		}
		for (int i = max; i < newWeights.length; i++) {
			ssdiff += newWeights[i] * newWeights[i];
		}

		return Math.sqrt(ssdiff);
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
		public void run()  {
			int len = ids.size();
			try {
  			for (int i = 0; i < len; i++) {
  				String[] tokens = lines.get(i).split("\\s+");
  				RichTranslation<IString, String> translation = decodeOnly(tokens, ids.get(i), ids.get(i) -1, infererid);
  				
  				if (translation != null) {
  	  				// notice we reproduce the lameness of moses in that an extra space is
  	  				// inserted after each translation
  					  synchronized(System.out) { System.out.printf("%d:%s \n", ids.get(i), translation.translation); }
  					  synchronized(System.err) { 
  	  				System.err.printf("Final Translation: %s\n", translation.translation);
  	  				System.err.printf("Score: %f\n", translation.score);
  					  }
  	  			} else {
  	  				synchronized(System.out) { System.out.printf("<<<decoder failure %d>>>\n", ids.get(i)); }
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
  			RichTranslation<IString, String> translation = decodeOnly(tokens, translationId, lineNumber, 0);
  			
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
      
    	
    }

    if (nbestListWriter != null) {
			System.err.printf("Closing n-best writer\n");
			nbestListWriter.close();
		}
  }

	public RichTranslation<IString, String> decodeOnly(String[] tokens, int translationId, int lineNumber, int procid) throws IOException {

    Sequence<IString> foreign = new SimpleSequence<IString>(true, IStrings
      .toIStringArray(tokens));
    // log foreign sentence
    synchronized(System.err) { System.err.printf("Translating(%d): %s\n", procid, foreign); }

    // do translation
    long startTime = System.currentTimeMillis();
    RichTranslation<IString, String> translation;

    ConstrainedOutputSpace<IString, String> constrainedOutputSpace = (constrainedToRefs == null ? null
        : new EnumeratedConstrainedOutputSpace<IString, String>(
            constrainedToRefs.get(translationId)));

    if (nbestListSize == -1) {
      translation = inferers.get(procid).translate(foreign, lineNumber - 1,
          constrainedOutputSpace);
    } else {
      List<RichTranslation<IString, String>> translations = inferers.get(procid).nbest(
          foreign, lineNumber - 1, constrainedOutputSpace,
          nbestListSize);
      if (translations != null) {
        translation = translations.get(0);
        synchronized(nbestListWriter) {
          for (RichTranslation<IString, String> tran : translations) {
            nbestListWriter.append(generateMosesNBestList ?
            tran.nbestToMosesString(translationId) :
            tran.nbestToString(translationId)).append("\n");
          }
        }
      } else {
        translation = null;
      }
    }
    long translationTime = System.currentTimeMillis() - startTime;

    // log additional information to stderr
    synchronized(System.err) {
      if (translation != null) {
        System.err.printf("Best Translation: %s\n", translation.translation);
        System.err.printf("Final score: %.3f\n", (float) translation.score);
        System.err.printf("Coverage: %s\n", translation.foreignCoverage);
        System.err.printf("Foreign words covered: %d (/%d)  - %.3f %%\n",
            translation.foreignCoverage.cardinality(), foreign.size(),
            translation.foreignCoverage.cardinality() * 100.0 / foreign.size());
      } else {
        System.err.println("No best Translation: <<<decoder failure>>>");
      }
  
      System.err.printf("Time: %f seconds\n", translationTime / (1000.0));
    }
    
		return translation;
	}

	static public int MAX_LEARN_NBEST_ITER = 100;
	static public int LEARNING_NBEST_LIST_SIZE = 1000;

	List<ScoredFeaturizedTranslation<IString, String>> filterLowScoring(
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

	List<ScoredFeaturizedTranslation<IString, String>> filterHighLowScoring(
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
	

	@SuppressWarnings("unchecked")
	private void learnWeights(String inputFilename) throws IOException {
		BufferedWriter nbestListWriter;

		double maxEvalScore = Double.NaN;
		NBestListContainer<IString, String> nbestLists = null;

		int chunkSize = (learningAlgorithm.equals(EVALUE_LEARNING)? 1 : 5);

		LineNumberReader reader = null;
		int translationId = 0;
		for (int nbestIter = 0; nbestIter < MAX_LEARN_NBEST_ITER; nbestIter++) {
			IncrementalEvaluationMetric<IString, String> actualPostIncEval = evalMetric.getIncrementalMetric();
			IncrementalEvaluationMetric<IString, String> actualPreIncEval  = evalMetric.getIncrementalMetric();
			
			double initialEvalSum = 0;
			double postEvalSum = 0;
			int evalCount = 0;
			boolean doneStream = false;
			reader = new LineNumberReader(new InputStreamReader(new FileInputStream(inputFilename), "UTF-8"));
			translationId = 0;
			
			double initialCScore = 0;
			for (int chunk = 0; !doneStream; chunk++) {
							
				String nbestFilename;
				if (!learningAlgorithm.equals(EVALUE_LEARNING)) {
					nbestFilename = String.format("%s.nbest.c%d.%d", saveWeights,
							chunk, nbestIter);
				} else {
					nbestFilename = String.format("/tmp/%s.nbest.c%d.%d", saveWeights.replaceAll("[^A-Za-z0-9]","_"),
							chunk, nbestIter);
				}

				System.err.printf("Generating n-best list to: %s\n", nbestFilename);
				// Generate new nbest list
				System.err.printf("n-best list iter: %d\n", nbestIter);
				System.err.printf("Generating n-best list: %s\n", nbestFilename);

				// if (nbestIter < -1) {
				nbestListWriter = new BufferedWriter(new FileWriter(nbestFilename));

				int skipped = 0;
				int included = 0;
			
				long decodingTime = -System.currentTimeMillis();
				int foreignTokensTranslated = 0;
				for (String line; included < chunkSize; translationId++, included++) {
					line = reader.readLine();
					if (line == null) {
						reader.close();
						doneStream = true;
						break;
					}

					String[] tokens = line.split("\\s+");
					foreignTokensTranslated += tokens.length;
					if (tokens.length > maxSentenceSize) {
						System.err.printf("Skipping: %s\n", line);
						System.err.printf("Tokens: %d (Max: %d)\n", tokens.length,
								maxSentenceSize);
						skipped++;
						continue;
					}

					if (tokens.length < minSentenceSize) {
						System.err.printf("Skipping: %s\n", line);
						System.err.printf("Tokens: %d (Min: %d)\n", tokens.length,
								minSentenceSize);
						skipped++;
						continue;
					}

					Sequence<IString> foreign = new SimpleSequence<IString>(true,
							IStrings.toIStringArray(tokens));

					// log foreign sentence
					System.err.printf("Translating(%d): %s\n", reader.getLineNumber(),
							foreign);
					long translationTime = -System.currentTimeMillis();
					// scorer.setRandomizeTag(nbestIter == 0);

					List<RichTranslation<IString, String>> translations = new ArrayList(LEARNING_NBEST_LIST_SIZE);
					List<List<RichTranslation<IString, String>>> nbestNBad = null;
					
					
					if (nbestIter == 0 && !learningAlgorithm.equals(EVALUE_LEARNING)) {
						scorer.setWeightMultipliers(1.0, 0.0);
						System.err.printf("Doing Manual Weight Decode.\n");
						nbestNBad = ((AbstractBeamInferer) inferers.get(0)).nbestNBad(foreign,
								reader.getLineNumber() - 1, null, LEARNING_NBEST_LIST_SIZE, 0);
						translations.addAll(nbestNBad.get(0));
						translations.addAll(nbestNBad.get(1));
						scorer.setWeightMultipliers(0.0, 1.0);
					} 

					if (!(nbestIter == 0 && chunk == 0) || learningAlgorithm.equals(EVALUE_LEARNING)) {
					nbestNBad = ((AbstractBeamInferer) inferers.get(0))
					.nbestNBad(foreign, reader.getLineNumber() - 1, null,
							LEARNING_NBEST_LIST_SIZE, 0);
					}
			
					translations.addAll(nbestNBad.get(0));
					translations.addAll(nbestNBad.get(1));

					translationTime += System.currentTimeMillis();
					System.err.printf(
							"Foreign length: %d Argmax Translation length: %s Translation time: %.3f s\n",
							foreign.size(), (translations == null ? "NA" : translations
									.get(0).translation.size()), translationTime / (1000.0));
					if (translations != null) {
						System.err.printf("Arg-max translation:%s\n\n", translations.get(0).translation);
						for (RichTranslation<IString, String> tran : translations) {
							nbestListWriter.append(tran.nbestToString(translationId)).append(
							"\n");
						}
					} else {
						System.err.printf("<<<decoder failure>>>\n");
					}
				}
				decodingTime += System.currentTimeMillis();
				
				if (included == 0)
					continue;
				nbestListWriter.close();
				
				if (skipped == translationId) {
					throw new RuntimeException(String
							.format("Error: all foreign sentences skipped"));
				}
				// }
				// perform loss augmented inference over n-best list until convergence
				System.err.printf("Loading n-best list\n");
				nbestLists = new MosesNBestList(nbestFilename);

				int maxNbestListSize = 0;
				int minNbestListSize = Integer.MAX_VALUE;
				for (List a : nbestLists.nbestLists()) {
					if (a.size() == 0) continue;
					if (a == null)
						continue;
					int aSz = a.size();
					if (aSz > maxNbestListSize) maxNbestListSize = aSz;
					if (aSz < minNbestListSize) minNbestListSize = aSz;
				}
				System.err.printf("Largest  cummalative n-best list size: %d\n", maxNbestListSize);
				System.err.printf("Smallest cummalative n-best list size: %d\n", minNbestListSize);

				int translations = 0;
				for (List<? extends ScoredFeaturizedTranslation<IString, String>> transList : nbestLists
						.nbestLists()) {
					if (transList.size() > 0)
						translations++;
				}

				System.err.printf("Translations in chunk: %d\n", translations);

				double l2Of1Best = 0;
				double scoreSum1Best = 0;
				double scoreSum1Worst = 0;
				int outOf = 0;
				for (List<? extends ScoredFeaturizedTranslation<IString, String>> nbestlist : nbestLists
						.nbestLists()) {
					if (nbestlist.size() == 0)
						continue;
					for (FeatureValue<String> fv : nbestlist.get(0).features) {
						l2Of1Best += fv.value * fv.value;
					}
					outOf++;
					scoreSum1Best += nbestlist.get(0).score;
					scoreSum1Worst += nbestlist.get(nbestlist.size() - 1).score;
				}
				l2Of1Best = Math.sqrt(l2Of1Best);
				double scoreSumDiff = scoreSum1Best - scoreSum1Worst;

				System.err.printf(
						"Argmax cScore: %e N-best argmin cScore: %e (diff %f)\n",
						scoreSum1Best, scoreSum1Worst, scoreSumDiff);
				
				EvaluationMetric<IString, String> bestScoreMetric = new MarginRescaleEvaluationMetric(
						null, scorer);

				MultiTranslationMetricMax<IString, String> bestScoreSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(
						bestScoreMetric);
				MultiTranslationMetricMax<IString, String> oracleEvalSearch = new HillClimbingMultiTranslationMetricMax<IString, String>(
						evalMetric);

				System.err.printf("Finding best scoring translations over cummulative n-best list...\n");
				List<ScoredFeaturizedTranslation<IString, String>> bestScoreTranslationsInit = bestScoreSearch.maximize(nbestLists);
				System.err.printf("Done.\n");
				
				IncrementalEvaluationMetric<IString, String> initialEvalMetric = evalMetric.getIncrementalMetric();
				{
					int tI = 0;
					initialCScore = 0.0;
					int posT = -1;
					for (ScoredFeaturizedTranslation<IString, String> trans : bestScoreTranslationsInit) { posT++;						
						if (actualPreIncEval.size() <= posT) { 
							actualPreIncEval.add(trans);
						} else {
							if (trans != null) actualPreIncEval.replace(posT, trans);
						}
						
						initialEvalMetric.add(trans);
						if (trans != null) initialCScore += scorer.getIncrementalScore(trans.features);
						tI++;
					}
				}
				
				System.err.printf("Finding oracle translations over cummulative n-best list....\n");
				List<ScoredFeaturizedTranslation<IString, String>> oracleEvalTranslations = oracleEvalSearch.maximize(nbestLists);
				System.err.printf("Done.\n");
				IncrementalEvaluationMetric<IString, String> oracleEvalMetric = evalMetric.getIncrementalMetric();
				
				for (ScoredFeaturizedTranslation<IString, String> trans : oracleEvalTranslations) {
					//System.err.printf("%s\n", (trans == null ? trans : trans.translation));
					oracleEvalMetric.add(trans);
				}
				double oracleScore = oracleEvalMetric.score();

				
									
				initialEvalSum += initialEvalMetric.score();
				evalCount++;
				System.err.printf(
						"> Init eS (%d:%d): %.2f (c: %.2e) Orcl: %.2f Avg: %.2f Actl eS: %.2f\n", nbestIter, chunk, 100
						* initialEvalMetric.score(), initialCScore, 100 * oracleScore, 100*initialEvalSum/evalCount, actualPreIncEval.score()*100);
				
				scorer.setWeightMultipliers(0.0, 1.0);

				long learningTime = -System.currentTimeMillis();
				
				if (learningAlgorithm.equals(EVALUE_LEARNING)) {
					EValueLearningScorer eScorer = (EValueLearningScorer)scorer;
					int transIdx = -1;
					IncrementalEvaluationMetric<IString, String> incEvalMetric = learningMetric.getIncrementalMetric();
					for (int i = 0; i < nbestLists.nbestLists().size(); i++) {
						if (nbestLists.nbestLists().get(i).size() != 0) {
							if (transIdx == -1) transIdx = i;
							else throw new RuntimeException();
						}
						incEvalMetric.add(null);
					}
					
					//nbestLists.nbestLists();
				  List<? extends ScoredFeaturizedTranslation<IString, String>> sfTrans = nbestLists.nbestLists().get(transIdx);
				  List<List<FeatureValue<String>>> featureVectors = new ArrayList<List<FeatureValue<String>>>(sfTrans.size());
				  
				 /*  { int tI = -1;
				  for (ScoredFeaturizedTranslation<IString, String> sfTran : sfTrans) { tI++;
				  	double score = -((TERMetric)learningMetric).calcTER(sfTran, transIdx);
				  	if (score > trueOracle) { trueOracle = score; loc = tI; } 
				  }
				  }
				  System.err.printf("true oracle: %f (%d)\n", trueOracle, loc); */
				  double[] us = new double[sfTrans.size()];
				  //System.err.printf("nbest\n");
				  System.err.printf("eval scores (%d)\n", sfTrans.size());
				  for (ScoredFeaturizedTranslation<IString, String> sfTran : sfTrans) {
				  	incEvalMetric.replace(transIdx, sfTran);
				  	us[featureVectors.size()] = incEvalMetric.score();
				  	//System.err.printf("%d: %f\n", featureVectors.size(), us[featureVectors.size()]);
				  	featureVectors.add(sfTran.features);
				  }	
				  if (DEBUG_LEVEL >= 2) {
				  	System.err.printf("Old Weights\n");
				  	eScorer.displayWts();
				  }
				  double objInit = eScorer.objectiveValue(featureVectors, us);
				  eScorer.wtUpdate(featureVectors, us, nbestIter+1);
				  double objPost = eScorer.objectiveValue(featureVectors, us);
				  System.err.printf("Obj Delta: %e (%e-%e)\n", objPost - objInit, objPost, objInit);
				  
				  if (DEBUG_LEVEL >= 2) {
				  	System.err.printf("New Weights\n");
				  	eScorer.displayWts();
				  }
				}
				
				learningTime += System.currentTimeMillis();
				
				List<ScoredFeaturizedTranslation<IString, String>> bestScoreTranslations = bestScoreSearch
				.maximize(nbestLists);

				IncrementalEvaluationMetric<IString, String> bestScoringEvalMetric = evalMetric.getIncrementalMetric();
				
				List<FeatureValue<String>> allSelectedFeatures = new LinkedList<FeatureValue<String>>();
				double finalCScore = 0;
				int posBT = -1;
				for (ScoredFeaturizedTranslation<IString, String> trans : bestScoreTranslations) { posBT++;
				  
					if (actualPostIncEval.size() <= posBT) { 
						actualPostIncEval.add(trans);
					} else {
						if (trans != null) actualPostIncEval.replace(posBT, trans);
					}
					bestScoringEvalMetric.add(trans);
					if (trans != null) {
						allSelectedFeatures.addAll(trans.features);
					}
				}
				
				postEvalSum += bestScoringEvalMetric.score();
				System.err.printf(
						"> Post eS (%d:%d): %.2f (c: %.2e) Orcl: %.2f Avg: %.2f Actl eS: %.2f\n", nbestIter, chunk, 100
						* bestScoringEvalMetric.score(), finalCScore, 100
						* oracleEvalMetric.score(), 100*postEvalSum/evalCount, actualPostIncEval.score()*100);
				System.err.printf("> Time Summary Decoding: %.3f s Learning (incl loss infer): %.3f s\n", decodingTime/1000.0, learningTime/1000.0);
				System.err.printf("Max eval score: %f\n", maxEvalScore);
				if (chunk % 250 == 0) scorer.saveWeights(String.format("%s.nbestitr_%d.chunk_%d.wts", saveWeights, nbestIter, chunk));
				if (DEBUG_LEVEL >= 2) {
					System.err.printf("Final Weights for nbestitr: %d chunk: %d", nbestIter, chunk);
					scorer.displayWeights();
				}
			
			}
			scorer.saveWeights(String.format("%s.nbestitr_%d.final.wts", saveWeights, nbestIter));
		  System.err.printf(">> %d: Avg eS: %.2f~>%.2f  Actl eS: %.2f~>%.2f (diff: %.3f)\n", nbestIter, 100*initialEvalSum/evalCount,
		  		100*postEvalSum/evalCount, actualPreIncEval.score()*100, actualPostIncEval.score()*100,
		  		actualPostIncEval.score()*100 - actualPreIncEval.score()*100);	
		}
	}

	public void executiveLoop() throws IOException {
		if (learnWeights) {
			String inputFilename = saveWeights + ".in";
			LineNumberReader reader = new LineNumberReader(new InputStreamReader(
					System.in, "UTF-8"));
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(inputFilename), "UTF-8"));
			for (String line; (line = reader.readLine()) != null;) {
				writer.write(line);
				writer.write("\n");
			}
			reader.close();
			writer.close();

			if (learningAlgorithm.equals(EVALUE_LEARNING)) {
				learnWeights(inputFilename);
			} else {
				throw new RuntimeException("Unrecognized learning algorithm");
			}
			scorer.saveWeights(saveWeights + ".final.wts");
		} else {
			decodeFromConsole();
		}
	}

	static public void main(String[] args) throws Exception {

    if(args.length < 1) {
      System.err.println("Usage:\n\tjava ...PseudoMoses (pharaoh.ini)");
      System.exit(-1);
    }

    Map<String, List<String>> config = (args.length == 1) ? readConfig(args[0]) : readArgs(args);
    PseudoMoses p = new PseudoMoses(config);
    
		p.executiveLoop();

	}

}

class IdScorePair implements Comparable<IdScorePair> {
	public final int id;
	public final double score;
	public final double evalScore;

	public IdScorePair(int id, double score, double evalScore) {
		this.id = id;
		this.score = score;
		this.evalScore = evalScore;
	}

	@Override
	public int compareTo(IdScorePair o) {
		int scoreSigNum = (int) Math.signum(o.score - score);
		if (scoreSigNum != 0)
			return scoreSigNum;
		return id - o.id;
	}
}
