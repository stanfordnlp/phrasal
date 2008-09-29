package mt.base;

import edu.stanford.nlp.util.IString;


/**
 * 
 * @author danielcer
 *
 */
public class LanguageModels {
	/**
	 * 
	 * @param <T>
	 * @param lm
	 * @param s
	 * @return
	 */
	static private final IString sequenceStart = new IString("<s>");
	
	static <T> double scoreSequence(LanguageModel<T> lm, Sequence<T> s) {
		double logP = 0;
		int sz = s.size();
		for (int i = 0; i < sz; i++) {			
			if (s.get(i).equals(sequenceStart)) {
                // don't explicitly score <s> as SRI LM
				// assigns log p(<s>) to -99 (i.e. p(<s>) /approx 0)
				continue;
			}
						
			Sequence<T> ngram = s.subsequence(0, i+1);
			
			double ngramScore = lm.score(ngram);
			
			if (ngramScore == Double.NEGATIVE_INFINITY) {
				// like sri lm's n-gram utility w.r.t. closed vocab models,
				// right now we silently ignore unknown words.
				continue;
			}
			logP += ngramScore; 			
		}
		return logP;
	}
}
