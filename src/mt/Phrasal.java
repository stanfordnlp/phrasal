package mt;

import java.io.*;
import java.util.*;

/**
 * 
 * @author danielcer
 *
 */
public class Phrasal {	
	public static final String PHRASE_GENERATOR_OPT = "phrasegenerator";
	public static final String FEATURIZER_OPT = "featurizer";
	public static final String SCORER_OPT = "scorer";
	public static final String INFERENCE_OPT = "inference";
	public static final String SEARCH_HEURISTIC_OPT = "searchheuristic";
	public static final String RECOMBINATION_FILTER_OPT = "recombination";
	public static final String INPUT_OPT = "input";
	public static final String OUTPUT_OPT = "output";
	public static final String LOG_OPT = "log";
	
	public static final String STD_IN_OUT_ERR_DEFAULT = "-";
	private static final Map<String,String> optDefaults = new HashMap<String, String>();
	
	static {
		optDefaults.put(INFERENCE_OPT, InfererBuilderFactory.DEFAULT_INFERER);
		optDefaults.put(SEARCH_HEURISTIC_OPT, HeuristicFactory.DEFAULT_HEURISTIC);
		optDefaults.put(SCORER_OPT, ScorerFactory.DEFAULT_SCORER);
		optDefaults.put(RECOMBINATION_FILTER_OPT, RecombinationFilterFactory.DEFAULT_RECOMBINATION_FILTER);
		optDefaults.put(INPUT_OPT, STD_IN_OUT_ERR_DEFAULT);
        optDefaults.put(OUTPUT_OPT, STD_IN_OUT_ERR_DEFAULT);
        optDefaults.put(LOG_OPT, STD_IN_OUT_ERR_DEFAULT);
	}
	
	private static final String[] validOptions = {PHRASE_GENERATOR_OPT, SCORER_OPT, SEARCH_HEURISTIC_OPT, 
		    FEATURIZER_OPT, INFERENCE_OPT,  INPUT_OPT, OUTPUT_OPT, RECOMBINATION_FILTER_OPT};
	
	// everything we need to run the main translation loop
	Inferer<IString,String> inferer;
	LineNumberReader in;
	PrintStream out;
	PrintStream log;
	
	// auxiliary information regarding how the current instance of phrasal
	// is configured
	String inName;
	String outName;
	String logName;
	
	private String getMandatoryOption(Map<String, String> map, String key) {
		String value = map.get(key);
		if (value == null) {
			throw new RuntimeException(String.format(
					"Error: mandatory option '%s' has been omitted", key));
		}
		return value;
	}
	
	/**
	 * 
	 * @param properties
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	public Phrasal(Properties properties) throws IOException {
		Map<String, String> propsNoCase = new HashMap<String,String>(optDefaults);
		Set<String> validOptionsSet = new HashSet<String>(Arrays.asList(validOptions));		
		
		// check for invalid options & build case insensitive map of the properties
		for (Object key : properties.keySet()) {
			String keyLowerCase = ((String)key).toLowerCase();
			if (!validOptionsSet.contains(keyLowerCase)) {
				throw new RuntimeException(String.format("Unrecognized property: %s\n", key));
			}
			propsNoCase.put(keyLowerCase, properties.getProperty((String)key));
		}
		
		// Create input reader
		String inputSpecs = getMandatoryOption(propsNoCase, INPUT_OPT);
		if (inputSpecs.equals(STD_IN_OUT_ERR_DEFAULT)) {
			in = new LineNumberReader(new InputStreamReader(System.in));
			inName = "stdin";
		} else {
			in = new LineNumberReader(new FileReader(inputSpecs));
			inName = inputSpecs;
		}

		// Create output print writer
		String outputSpecs = getMandatoryOption(propsNoCase, OUTPUT_OPT);
		if (outputSpecs.equals(STD_IN_OUT_ERR_DEFAULT)) {
			out = System.out;
			outName = "stdout";
		} else {
			out = new PrintStream(new FileOutputStream(outputSpecs));
			outName = outputSpecs;
		}
		
		// Create log print writer
		String logSpecs = getMandatoryOption(propsNoCase, LOG_OPT);
		if (logSpecs.equals(STD_IN_OUT_ERR_DEFAULT)) {
			log = System.err;
			logName = "stderr";
		} else {
			log = new PrintStream(new FileOutputStream(logSpecs));
			logName = logSpecs;
		}
	
		
		// Create Featurizer
		String featurizerSpecs = getMandatoryOption(propsNoCase, FEATURIZER_OPT);
		CombinedFeaturizer<IString,String> featurizer = FeaturizerFactory.factory(featurizerSpecs.split("\\s+"));
		
		// Create Scorer
		Scorer<String> scorer;
		String scorerSpecs = getMandatoryOption(propsNoCase, SCORER_OPT);
		scorer = ScorerFactory.factory(scorerSpecs);
		
		// Create Phrase Generator		
		String pgSpecs = getMandatoryOption(propsNoCase, PHRASE_GENERATOR_OPT);
		PhraseGenerator<IString> phraseGenerator =  PhraseGeneratorFactory.factory(featurizer, scorer, pgSpecs.split("\\s+"));
		
		
		// Create Recombination Filter
		String recombinationSpecs = getMandatoryOption(propsNoCase, RECOMBINATION_FILTER_OPT);
		RecombinationFilter<Hypothesis<IString, String>> filter = RecombinationFilterFactory.factory(featurizer.getFeaturizers(), recombinationSpecs.split("\\s+"));
		
		// Create Search Heuristic
		String heuristicSpecs = getMandatoryOption(propsNoCase, SEARCH_HEURISTIC_OPT);
		IsolatedPhraseFeaturizer<IString, String> isolatedPhraseFeaturizer;
		if (featurizer instanceof IsolatedPhraseFeaturizer) {
			isolatedPhraseFeaturizer = (IsolatedPhraseFeaturizer<IString,String>)featurizer;
		} else {
			isolatedPhraseFeaturizer = null;
		}
		SearchHeuristic<IString,String> heuristic = HeuristicFactory.factory(isolatedPhraseFeaturizer, scorer, heuristicSpecs.split("\\s+"));		
		
		
		// Create InfererBuilder
		String infererSpecs = getMandatoryOption(propsNoCase, INFERENCE_OPT);
		
		// Configure InfererBuilder
		InfererBuilder<IString,String> infererBuilder = InfererBuilderFactory.factory(infererSpecs.split("\\s+"));
		infererBuilder.setIncrementalFeaturizer(featurizer);
		infererBuilder.setPhraseGenerator(phraseGenerator);
		infererBuilder.setScorer(scorer);
		infererBuilder.setSearchHeuristic(heuristic);
		infererBuilder.setRecombinationFilter(filter);
		
		// Create Inferer		
		inferer = infererBuilder.build();
	}
	
	public String toString() {
		StringBuffer sbuf = new StringBuffer();
		sbuf.append("mt.Phrasal - Stanford Machine Translation System Harness\n");
		sbuf.append("Input: ").append(inName);
		sbuf.append("Output: ").append(outName);
		sbuf.append("Log: ").append(logName);
		sbuf.append("Inferer:\n\t").append(inferer.toString().replaceAll("\n", "\n\t"));
		
		return sbuf.toString();
	}
	
	public void setReader(LineNumberReader in, String inName) {
		this.in = in;
		this.inName = inName;
	}
	
	public void setPrintStream(PrintStream out, String outName) {
		this.out = out;
		this.outName = outName;
	}
	
	public void setLogStream(PrintStream log, String logName) {
		this.log = log;
		this.logName = logName;
	}
	
	/**
	 * 
	 */
	public void translationLoop() throws IOException {
		
		log.println("Entering main translation loop");
		
		for (String line; (line = in.readLine()) != null; ) {
			String[] tokens = line.split("\\s+");
			Sequence<IString> foreign = new SimpleSequence<IString>(true, IStrings.toIStringArray(tokens));
			
			// log foreign sentence
			log.printf("Translating: %s\n", foreign);
			
			// do translation
			long startTime = System.currentTimeMillis();
			RichTranslation<IString,String> translation = inferer.translate(foreign, in.getLineNumber()-1, null);
			long translationTime = System.currentTimeMillis() - startTime;
			
			// display results
			out.printf("%s\n", translation.translation);
			
			// log additional information to stderr
			log.printf("Best Translation: %s\n", translation.translation);
			log.printf("Final score: %f\n", translation.score);
			log.printf("Time: %f seconds\n", translationTime/(1000.0));
			log.printf("Coverage: %s\n", translation.foreignCoverage);
			log.printf("Foreign words covered: %d (/%d)  - %.3f %%\n", translation.foreignCoverage.cardinality(), foreign.size(), translation.foreignCoverage.cardinality()*100.0/foreign.size());
		}
	}
	
	public static void usage() {
		System.out.printf("Usage:\n\t java ...Phrasal (configuration file)\n\n");
		System.out.printf(
				"Phrasal provides a harness for running the Stanford Phrasal MT system.\n"+
				"Foreign sentences are read in from the specified input stream, and then\n"+
				"translated with the results written to the designate output stream.\n\n"); 
		
		System.out.print("General Options:\n"+
				"\t"+FEATURIZER_OPT+" (featurizer name) (arguments)\n" +
				"\t"+PHRASE_GENERATOR_OPT+" (phraseGenerator name) (arguments)\n" +
				"\t"+SCORER_OPT+" (scorer name) (arguments) # default %s"+ScorerFactory.DEFAULT_SCORER+"\n"+
				"\t"+INFERENCE_OPT+" (inference name) (arguments) # default %s"+InfererBuilderFactory.DEFAULT_INFERER+"\n"+
				"\t"+SEARCH_HEURISTIC_OPT+" (heuristic name) (arguments) # default %s"+HeuristicFactory.DEFAULT_HEURISTIC+"\n"+
				"\t"+RECOMBINATION_FILTER_OPT+"  (recombination filter name) arguments # default %s"+RecombinationFilterFactory.DEFAULT_RECOMBINATION_FILTER+"\n" +
				"\t"+INPUT_OPT+" (file name or -)  # default '-' stdin\n" +
                "\t"+OUTPUT_OPT+" (file name or -) # default '-' stdout\n" +
                "\t"+LOG_OPT+" (file name or -) # default '-' stderr\n"+
                "\n");				
	}
	
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			usage();
			System.exit(-1);			
		}
		
		String configFilename = args[0];
		Properties props = new Properties();
		FileReader propsReader = new FileReader(configFilename);
		props.load(propsReader);
		propsReader.close();
		
		Phrasal p = new Phrasal(props);
		
		p.translationLoop();
	}
}
