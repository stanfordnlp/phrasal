package mt.decoder.recomb;

import java.util.*;

import mt.base.FactoryUtil;
import mt.base.LanguageModel;
import mt.decoder.feat.Featurizers;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.util.Hypothesis;

import edu.stanford.nlp.util.IString;

/**
 * 
 * @author danielcer
 *
 */
public class RecombinationFilterFactory {
	static public final String NO_RECOMBINATION = "norecombination";
	static public final String TRANSLATION_IDENTITY = "translationidentity";
	static public final String FOREIGN_COVERAGE = "foreigncoverage";
	static public final String LINEAR_DISTORTION = "lineardistortion";
	static public final String TRANSLATION_NGRAM = "translationngram";
	static public final String CLASSICAL_TRANSLATION_MODEL = "classicaltranslationmodel";
	static public final String CLASSICAL_TRANSLATION_MODEL_ALT = "ctm";
  static public final String CLASSICAL_TRANSLATION_MODEL_FINE = "fine";
	static public final String DEFAULT_RECOMBINATION_FILTER = TRANSLATION_IDENTITY;
	
	static public final String TRANSLATION_NGRAM_PARAMETER = "ngramsize";
	
	private RecombinationFilterFactory() { }
	
	/**
	 * 
	 * @param rfSpecs
	 * @return
	 */
	static public RecombinationFilter<Hypothesis<IString,String>> factory(List<IncrementalFeaturizer<IString, String>> featurizers, String... rfSpecs) {
		String rfName;
		if (rfSpecs.length == 0) {
			rfName = DEFAULT_RECOMBINATION_FILTER;
		} else {
			rfName = rfSpecs[0].toLowerCase();
		}
		
		Map<String,String> paramPairs = FactoryUtil.getParamPairs(rfSpecs);
		
		
		
		// default to a history window that is appropriate for the highest order lm
		List<LanguageModel<IString>> lgModels = Featurizers.extractNGramLanguageModels(featurizers);  
		int ngramHistory = Integer.MAX_VALUE;
		String ngramParamStr = paramPairs.get(TRANSLATION_NGRAM_PARAMETER);
		if (ngramParamStr != null) {
			try {
				ngramHistory = Integer.parseInt(ngramParamStr);
			} catch (NumberFormatException e) {
				throw new RuntimeException(
						String
								.format(
										"RecombinationFilter option %s:%s can not be converted into an integer value",
										TRANSLATION_NGRAM_PARAMETER,
										ngramParamStr));
			}
		}
		
		if (rfName.equals(NO_RECOMBINATION)) {
			return new NoRecombination<Hypothesis<IString,String>>();
		} else if (rfName.equals(TRANSLATION_IDENTITY)) {
			// note that this is *surface* identity only
			return new TranslationIdentityRecombinationFilter<IString, String>();
		} else if (rfName.equals(FOREIGN_COVERAGE)) {
			return new ForeignCoverageRecombinationFilter<IString, String>();
		} else if (rfName.equals(LINEAR_DISTORTION)) {
			return new LinearDistorionRecombinationFilter<IString, String>();
		} else if (rfName.equals(TRANSLATION_NGRAM)) {
			return new TranslationNgramRecombinationFilter<IString, String>(lgModels, ngramHistory);
		} else if (rfName.equals(CLASSICAL_TRANSLATION_MODEL) || rfName.endsWith(CLASSICAL_TRANSLATION_MODEL_ALT)) {
			List<RecombinationFilter<Hypothesis<IString, String>>> filters = new LinkedList<RecombinationFilter<Hypothesis<IString, String>>>();
			// maintain uniqueness of hypotheses that will result in different linear distortion scores when extended
			// with future translation options.
			filters.add(new LinearDistorionRecombinationFilter<IString, String>());
			
			// maintain uniqueness of hypotheses that differ by the last N-tokens, this being relevant to lg model scoring
			filters.add(new TranslationNgramRecombinationFilter<IString, String>(lgModels, ngramHistory));
			
			// maintain uniqueness of hypotheses that differ in terms of foreign sequence coverage
			filters.add(new ForeignCoverageRecombinationFilter<IString, String>());
			
			return new CombinedRecombinationFilter<Hypothesis<IString, String>>(filters);

    } else if (rfName.equals(CLASSICAL_TRANSLATION_MODEL_FINE)) {
      List<RecombinationFilter<Hypothesis<IString, String>>> filters = new LinkedList<RecombinationFilter<Hypothesis<IString, String>>>();
      filters.add(new TranslationIdentityRecombinationFilter<IString, String>());
			filters.add(new LinearDistorionRecombinationFilter<IString, String>());
			filters.add(new ForeignCoverageRecombinationFilter<IString, String>());
			return new CombinedRecombinationFilter<Hypothesis<IString, String>>(filters);
    }
		throw new RuntimeException(String.format(
				"Unrecognized recombination filter: %s", rfName));
	}
}
