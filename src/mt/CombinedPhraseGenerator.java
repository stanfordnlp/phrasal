package mt;

import java.util.*;
import java.io.*;

/**
 * 
 * @author Daniel Cer
 *
 * @param <TK>
 */
public class CombinedPhraseGenerator<TK,FV> implements PhraseGenerator<TK> {
	static public final String DEBUG_OPT = "CombinedPhraseGeneratorDebug";
	static public final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_OPT, "false"));
	
	public enum Type {CONCATENATIVE, STRICT_DOMINANCE};
	static public final Type DEFAULT_TYPE = Type.CONCATENATIVE;
	static public final int DEFAULT_PHRASE_LIMIT = 50;
	
	final List<PhraseGenerator<TK>> phraseGenerators;
	final Type type;
	final int phraseLimit;
	
	private void addToMap(ConcreteTranslationOption<TK> opt, Map<CoverageSet, List<ConcreteTranslationOption<TK>>> optsMap) {
		List<ConcreteTranslationOption<TK>> optList = optsMap.get(opt.foreignCoverage);
		if (optList == null) {
			optList = new LinkedList<ConcreteTranslationOption<TK>>();
			optsMap.put(opt.foreignCoverage, optList);
		}
		optList.add(opt);
	}
	
	public int getPhraseLimit() {
		return phraseLimit;
	}
	
	public List<ConcreteTranslationOption<TK>> translationOptions(Sequence<TK> sequence, int translationId) {
		Map<CoverageSet, List<ConcreteTranslationOption<TK>>> optsMap = new HashMap<CoverageSet, List<ConcreteTranslationOption<TK>>>();
		
		if (DEBUG) {
			System.err.printf("CombinedPhraseGenerator#translationOptions type: %s\n", type);
		}
		
		if (type.equals(Type.CONCATENATIVE)) {
			for (PhraseGenerator<TK> phraseGenerator : phraseGenerators) {
				for (ConcreteTranslationOption<TK> opt : phraseGenerator.translationOptions(sequence, translationId)) {
					addToMap(opt, optsMap);
				}			
			}
		} else if (type.equals(Type.STRICT_DOMINANCE)) {
			CoverageSet coverage = new CoverageSet(sequence.size());
			for (PhraseGenerator<TK> phraseGenerator : phraseGenerators) {
				if (DEBUG) {
					System.err.printf("Generator: %s\n", phraseGenerator.getClass().getName());
				}
				List<ConcreteTranslationOption<TK>> potentialOptions = phraseGenerator.translationOptions(sequence, translationId);
				CoverageSet novelCoverage = new CoverageSet(sequence.size());
				for (ConcreteTranslationOption<TK> option : potentialOptions) {
					if (coverage.intersects(option.foreignCoverage)) {
						if (DEBUG) {
							System.err.printf("Skipping %s intersects %s\n", coverage, option.foreignCoverage);
							System.err.printf("%s\n--\n", option);
						}
						continue;
					}
					novelCoverage.or(option.foreignCoverage);
					addToMap(option, optsMap);
				}
				coverage.or(novelCoverage);
			}
		} else {
			throw new RuntimeException(String.format("Unsupported combination type: %s", type));
		}
		
		/*
		if (DEBUG) {
			System.err.printf("Final concrete translation options(%s): \"%s\"\n", type, sequence);
			for (ConcreteTranslationOption<TK> option : opts) {
				System.err.println(option);
				System.err.println("----");
				System.err.println();
			}
		} */
		
		
		List<ConcreteTranslationOption<TK>> cutoffOpts = new LinkedList<ConcreteTranslationOption<TK>>();
		for (List<ConcreteTranslationOption<TK>> preCutOpts : optsMap.values()) {
			int sz = preCutOpts.size();
			if (sz <= phraseLimit) {
				cutoffOpts.addAll(preCutOpts);
				continue;
			}
			
			List<ConcreteTranslationOption<TK>> preCutOptsArray = new ArrayList<ConcreteTranslationOption<TK>>(preCutOpts);
			
			Collections.sort(preCutOptsArray);
			
			int preCutOptsArraySz = preCutOptsArray.size();
			
			for (int i = 0; (i < phraseLimit) || (phraseLimit == 0 && i < preCutOptsArraySz); i++) {
				cutoffOpts.add(preCutOptsArray.get(i));
			}
		
		}
		

		return cutoffOpts;
	}
	
	
	/**
	 * 
	 * @param phraseGenerators
	 * @param phraseFeaturizer
	 * @param scorer
	 */
	public CombinedPhraseGenerator(List<PhraseGenerator<TK>> phraseGenerators) {
		this.phraseGenerators = phraseGenerators;
		this.type = DEFAULT_TYPE;
		this.phraseLimit = DEFAULT_PHRASE_LIMIT;
	}
	
	/**
	 * 
	 * @param phraseGenerators
	 * @param type
	 * @param phraseFeaturizer
	 * @param scorer
	 */
	public CombinedPhraseGenerator(List<PhraseGenerator<TK>> phraseGenerators, Type type) {
		this.phraseGenerators = phraseGenerators;
		this.type = type;
		this.phraseLimit = DEFAULT_PHRASE_LIMIT;
	}
	
	/**
	 * 
	 * @param phraseGenerators
	 * @param type
	 * @param phraseLimit
	 */
	public CombinedPhraseGenerator(List<PhraseGenerator<TK>> phraseGenerators, Type type, int phraseLimit) {
		this.phraseGenerators = phraseGenerators;
		this.type = type;
		this.phraseLimit = phraseLimit;
	}
	
	
	
	/**
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length == 0) {
			System.out.printf("Usage:\n\tjava ...PhraseTableListGenerator (pharse table type):(filename) (pharse table type1):(filename1) ...\n");
			System.exit(-1);
		}
		
		String[] conf = new String[args.length+1];		
		conf[0] = PhraseGeneratorFactory.CONCATENATIVE_LIST_GENERATOR;		
		for (int i = 0; i < args.length; i++) conf[1+i] = args[i];
		
		PhraseGenerator<IString> ptGen = PhraseGeneratorFactory.factory(null, null, conf);
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		
		System.out.println("Interactive SimpleLookupPhraseGenerator");
		System.out.println("Please enter foreign sentences for which you want to look up translation phrases.");
		System.out.println();
		for (String line; (line = reader.readLine()) != null; ) {
			String[] tokens = line.split("\\s+");
			SimpleSequence<IString> sequence = new SimpleSequence<IString>(IStrings.toIStringArray(tokens));
			List<ConcreteTranslationOption<IString>> options = ptGen.translationOptions(sequence, -1);
			System.out.printf("Sequence: '%s'\n", sequence);
			System.out.println("Translation Options:\n");
			for (ConcreteTranslationOption<IString> option : options) {
				System.out.printf("\t%s -> %s coverage: %s score: %s\n", 
						sequence.subsequence(option.foreignCoverage),
						option.abstractOption.translation, 
						option.foreignCoverage, 
						Arrays.toString(option.abstractOption.scores));
			}
		}
	}
}
