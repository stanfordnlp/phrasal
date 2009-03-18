package mt.base;

import java.util.*;
import java.io.*;

import mt.decoder.feat.CombinedFeaturizer;
import mt.decoder.feat.IsolatedPhraseFeaturizer;
import mt.decoder.util.PhraseGenerator;
import mt.decoder.util.PhraseGeneratorFactory;
import mt.decoder.util.Scorer;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

/**
 * 
 * @author Daniel Cer
 *
 * @param <TK>
 */
public class CombinedPhraseGenerator<TK,FV> implements PhraseGenerator<TK> {
	static public final int FORCE_ADD_LIMIT = Integer.MAX_VALUE; // 200;
	static public final String DEBUG_OPT = "CombinedPhraseGeneratorDebug";
	static public final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_OPT, "false"));
	static final int PRELIMINARY_FILTER_MULTIPLIER = 3;
	
	final CombinedFeaturizer<TK, FV> phraseFeaturizer; 
	final Scorer<FV> scorer;
	
	public PhraseGenerator<TK> clone() {
		try{
		return (PhraseGenerator<TK>)super.clone();
		} catch (CloneNotSupportedException e) { return null; /* wnh */ }
	}
	
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
	
	public List<ConcreteTranslationOption<TK>> translationOptions(Sequence<TK> sequence, List<Sequence<TK>> targets, int translationId) {
		Map<CoverageSet, List<ConcreteTranslationOption<TK>>> optsMap = new HashMap<CoverageSet, List<ConcreteTranslationOption<TK>>>();
		
		if (DEBUG) {
			System.err.printf("CombinedPhraseGenerator#translationOptions type: %s\n", type);
		}
		
		for (PhraseGenerator<TK> phraseGenerator : phraseGenerators) {
			phraseGenerator.setCurrentSequence(sequence, targets);
		}
		
		if (type.equals(Type.CONCATENATIVE)) {
			for (PhraseGenerator<TK> phraseGenerator : phraseGenerators) {
				for (ConcreteTranslationOption<TK> opt : phraseGenerator.translationOptions(sequence, targets, translationId)) {
					addToMap(opt, optsMap);
				}			
			}
		} else if (type.equals(Type.STRICT_DOMINANCE)) {
			CoverageSet coverage = new CoverageSet(sequence.size());
			for (PhraseGenerator<TK> phraseGenerator : phraseGenerators) {
				if (DEBUG) {
					System.err.printf("Generator: %s\n", phraseGenerator.getClass().getName());
				}
				List<ConcreteTranslationOption<TK>> potentialOptions = phraseGenerator.translationOptions(sequence, targets, translationId);
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
		
		List<ConcreteTranslationOption<TK>> cutoffOpts = new LinkedList<ConcreteTranslationOption<TK>>();
		for (List<ConcreteTranslationOption<TK>> preCutOpts : optsMap.values()) {
			int sz = preCutOpts.size();
			if (sz <= phraseLimit) {
				cutoffOpts.addAll(preCutOpts);
				continue;
			}
			
			List<ConcreteTranslationOption<TK>> preCutOptsArr = new ArrayList<ConcreteTranslationOption<TK>>(preCutOpts);
			
			Collections.sort(preCutOptsArr);
			int preliminaryFilterPhraseLimit = phraseLimit*PRELIMINARY_FILTER_MULTIPLIER;
			List<ConcreteTranslationOption<TK>> pass1CutOpts = preCutOptsArr.subList(0, preliminaryFilterPhraseLimit);
			
			for (ConcreteTranslationOption<TK> opt: pass1CutOpts) {
				opt.refineScore(phraseFeaturizer, scorer, sequence, translationId);
			}
			
			Collections.sort(pass1CutOpts);
			
			if (DEBUG) {
				System.err.println("Sorted Options");
				for (ConcreteTranslationOption<TK>  opt : preCutOpts) {
					System.err.println("--");
					System.err.printf("%s => %s : %f\n", opt.abstractOption.foreign, opt.abstractOption.translation, opt.isolationScore);
					System.err.printf("%s\n", Arrays.toString(opt.abstractOption.scores));
				}
			}
			
			int pass1CutOpsArraySz = pass1CutOpts.size();
			
			int forceAddCnt = 0;
			for (int i = 0; (i < phraseLimit) || (phraseLimit == 0 && i < pass1CutOpsArraySz); i++) {
				if (pass1CutOpts.get(i).abstractOption.forceAdd) continue;
				cutoffOpts.add(pass1CutOpts.get(i));
			}
			
			if (phraseLimit != 0) for (int i = 0; i < preCutOptsArr.size() && forceAddCnt < FORCE_ADD_LIMIT; i++) {
				if (preCutOptsArr.get(i).abstractOption.forceAdd) {
					preCutOptsArr.get(i).refineScore(phraseFeaturizer, scorer, sequence, translationId);
					cutoffOpts.add(preCutOptsArr.get(i));
					forceAddCnt++;
				}
			}
		}
		

		return cutoffOpts;
	}
	
	
	/**
	 * 
	 * @param phraseGenerators
	 */
	public CombinedPhraseGenerator(List<PhraseGenerator<TK>> phraseGenerators, CombinedFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer) {
		this.phraseGenerators = phraseGenerators;
		this.type = DEFAULT_TYPE;
		this.phraseLimit = DEFAULT_PHRASE_LIMIT;
		this.phraseFeaturizer = phraseFeaturizer;
		this.scorer = scorer;
	}
	
	/**
	 * 
	 * @param phraseGenerators
	 * @param type
	 */
	public CombinedPhraseGenerator(List<PhraseGenerator<TK>> phraseGenerators, CombinedFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer, Type type) {
		this.phraseGenerators = phraseGenerators;
		this.type = type;
		this.phraseLimit = DEFAULT_PHRASE_LIMIT;
		this.phraseFeaturizer = phraseFeaturizer;
		this.scorer = scorer;
	}
	
	/**
	 * 
	 * @param phraseGenerators
	 * @param type
	 * @param phraseLimit
	 */
	public CombinedPhraseGenerator(List<PhraseGenerator<TK>> phraseGenerators, CombinedFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer, Type type, int phraseLimit) {
		this.phraseGenerators = phraseGenerators;
		this.type = type;
		this.phraseLimit = phraseLimit;
		this.phraseFeaturizer = phraseFeaturizer;
		this.scorer = scorer;
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
			List<ConcreteTranslationOption<IString>> options = ptGen.translationOptions(sequence, null, -1);
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

	@Override
	public void setCurrentSequence(Sequence<TK> foreign,
			List<Sequence<TK>> tranList) {
		for (PhraseGenerator<TK> pGen : phraseGenerators) {
			System.err.printf("COMBINED PHRASE GENERATOR SETTING CURRENT SEQ");
			pGen.setCurrentSequence(foreign, tranList);
		}
		
	}

	@Override
	public int longestForeignPhrase() {
		int longest = -1;
		for (PhraseGenerator<TK> phraseGenerator : phraseGenerators) {
			if (longest < phraseGenerator.longestForeignPhrase()) 
				longest = phraseGenerator.longestForeignPhrase();
		}
		return longest;
	}
}
