package edu.stanford.nlp.mt.base;


/**
 * 
 * @author danielcer
 *
 */
public class LanguageModels {
	static private final IString sequenceStart = new IString("<s>");
	
	static <T> double scoreSequence(LanguageModel<T> lm, Sequence<T> s2) {
		double logP = 0;
		Sequence<T> s = new InsertedStartEndToken<T>(s2, lm.getStartToken(), lm.getEndToken());
    int sz = s.size();
    for (int i = 1; i < sz; i++) {
      
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
