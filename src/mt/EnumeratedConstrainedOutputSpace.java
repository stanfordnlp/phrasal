package mt;

import java.util.*;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class EnumeratedConstrainedOutputSpace<TK, FV> implements ConstrainedOutputSpace<TK, FV> {
	public static final String DEBUG_PROPERTY = "EnumeratedConstrainedOutputSpaceDebug";
	public static final int DEBUG = Integer.parseInt(System.getProperty(DEBUG_PROPERTY, "0"));
	public static final int DEBUG_LEVEL_RESULTS = 1;
	public static final int DEBUG_LEVEL_COMPUTATION = 2;

	private final List<Sequence<TK>> allowableSequences;
	
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Allowable sequences:\n");
		for (Sequence<TK> seq : allowableSequences) {
			sb.append("\t").append(seq);
		}
		return sb.toString();
	}
	
	/**
	 * 
	 * @param allowableSequences
	 */
	public EnumeratedConstrainedOutputSpace(Collection<Sequence<TK>> allowableSequences) {
		this.allowableSequences = new ArrayList<Sequence<TK>>(allowableSequences);
	}
	
	@Override
	public boolean allowableFinal(Featurizable<TK, FV> featurizable) {
		if (featurizable == null) return false;
		
		Sequence<TK> translation = featurizable.partialTranslation;
		
		for (Sequence<TK> allowableSequence : allowableSequences) {
			if (allowableSequence.equals(translation)) {
				return true;
			}	
		} 
		
		return false;
	}

	@Override
	public boolean allowablePartial(Featurizable<TK, FV> featurizable) {
		return true;
	}

	@Override
	public boolean allowableContinuation(Featurizable<TK, FV> featurizable,
			Sequence<TK> nextPhrase) {
		
		if (featurizable == null) {
			for (Sequence<TK> allowableSequence : allowableSequences) {
				if (allowableSequence.startsWith(nextPhrase)) {
					return true;
				}	
			}	
			return false;
		}
		
		Sequence<TK> partialTranslation = featurizable.partialTranslation;
		
		for (Sequence<TK> allowableSequence : allowableSequences) {
			if (allowableSequence.startsWith(partialTranslation)) {
				int phraseSz = nextPhrase.size();
				int refPos = partialTranslation.size();
				if (refPos + phraseSz > allowableSequence.size()) {
					continue;
				}
				for (int phrasePos = 0; phrasePos < phraseSz; phrasePos++) {
					if (!allowableSequence.get(refPos+phrasePos).equals(nextPhrase.get(phrasePos))) {
						return false;
					}
				}
				return true;
			}	
		} 
		
		return false;	
	}

	@Override
	public List<ConcreteTranslationOption<TK>> filterOptions(
			List<ConcreteTranslationOption<TK>> optionList) {
		List<ConcreteTranslationOption<TK>> filteredOptions = new ArrayList<ConcreteTranslationOption<TK>>(optionList.size());
		
		for (ConcreteTranslationOption<TK> option : optionList) {
			if (DEBUG >= DEBUG_LEVEL_COMPUTATION) {
				System.err.printf("Examining: %s %s\n", option.abstractOption.translation, option.foreignCoverage);
			}
			for (Sequence<TK> allowableSequence : allowableSequences) {
				if (allowableSequence.contains(option.abstractOption.translation)) {
					filteredOptions.add(option);
					if (DEBUG >= DEBUG_LEVEL_COMPUTATION) {
						System.err.printf("\tAccepted!\n");
					}
					break;
				}
			}
		}
		
		if (DEBUG >= DEBUG_LEVEL_RESULTS) {
			System.err.println("Reference Set");
			System.err.println("--------------");
			for (Sequence<TK> allowableSequence : allowableSequences) {
				System.err.println(allowableSequence);
			}
			System.err.println("Filtered options");
			System.err.println("----------------");
			for (ConcreteTranslationOption<TK> option : filteredOptions) {
				System.err.printf("\t%s %s\n", option.abstractOption.translation, option.foreignCoverage);
			}
			System.err.println("--\n");
		}
		
		return filteredOptions;
	}
}
