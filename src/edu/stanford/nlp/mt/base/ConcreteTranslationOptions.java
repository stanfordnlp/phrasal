package edu.stanford.nlp.mt.base;

import java.util.*;

@SuppressWarnings("unused")
public class ConcreteTranslationOptions {

	private ConcreteTranslationOptions() { }
	
	/**
	 * 
	 * @param <TK>
	 */
	static public <TK> List<ConcreteTranslationOption<TK>> filterOptions(CoverageSet coverage, int foreignLength, List<ConcreteTranslationOption<TK>> options) {
		List<ConcreteTranslationOption<TK>> applicableOptions = new ArrayList<ConcreteTranslationOption<TK>>(options.size());
		CoverageSet flippedCoverage = new CoverageSet(foreignLength);
		flippedCoverage.or(coverage);
		flippedCoverage.flip(0, foreignLength);
		for (ConcreteTranslationOption<TK> option : options) {
			if (flippedCoverage.intersects(option.foreignCoverage)) {
				applicableOptions.add(option);
			}
		}
		return applicableOptions;
	}
}
