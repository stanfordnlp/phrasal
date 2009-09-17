package mt.decoder.feat;

import java.io.IOException;
import java.util.*;

import mt.base.*;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public class NGramLanguageModelFeaturizer<TK> implements IncrementalFeaturizer<TK,String>, IsolatedPhraseFeaturizer<TK, String> {
	public static final String FEATURE_PREFIX = "LM:";
	public static final String FEATURE_NAME = "LM";
	public static final String DEBUG_PROPERTY = "ngramLMFeaturizerDebug";
	final String featureName;
	final String featureNameWithColen;
	final boolean ngramReweighting;
	final boolean lengthNorm;
	final WeakHashMap<Featurizable<TK,String>,Double> rawLMScoreHistory 
		= new WeakHashMap<Featurizable<TK,String>,Double>();
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	public static final boolean SVMNORM = Boolean.parseBoolean(System.getProperty("SVMNORM", "false"));
	
	public final LanguageModel<TK> lm;
	final int lmOrder;
	
	static public final double MOSES_LM_UNKNOWN_WORD_SCORE = -100; // in sri lm -99 is -infinity

	static public NGramLanguageModelFeaturizer<IString> fromFile(String... args) throws IOException {
		if(args.length != 2)
      throw new RuntimeException("Two arguments are needed: LM file name and LM ID");
		LanguageModel<IString> lm = ARPALanguageModel.load(args[0]);
		return new NGramLanguageModelFeaturizer<IString>(lm, args[1], false);
	}

	public NGramLanguageModelFeaturizer(LanguageModel<TK> lm) {
		this.lm = lm;
		featureName = FEATURE_NAME;
		featureNameWithColen = featureName + ":";
		this.ngramReweighting = false;
		this.lmOrder = lm.order();
		this.lengthNorm = false;	
	}
	
	/**
	 * 
	 * @return
	 */
	public int order() {
		return lm.order();
	}
	

	
	public NGramLanguageModelFeaturizer(LanguageModel<TK> lm, String featureName, boolean ngramReweighting) {
		this.lm = lm;
		this.featureName = featureName;
		featureNameWithColen = featureName + ":";
		this.ngramReweighting = ngramReweighting;
		this.lmOrder = lm.order();
		this.lengthNorm = false;	
	}
	
	
	/**
	 * 
	 * @param lm
	 */
	public NGramLanguageModelFeaturizer(LanguageModel<TK> lm, boolean lmLabeled) {
		this.lm = lm;
		this.ngramReweighting = false;
		this.lmOrder = lm.order();
		if (lmLabeled) {
			featureName = String.format("%s%s", FEATURE_PREFIX, lm.getName());
		} else {
			featureName = FEATURE_NAME;
		}
		featureNameWithColen = featureName + ":";
		this.lengthNorm = false;	
	}

	/**
   * Constructor called by PseudoMoses when NGramLanguageModelFeaturizer appears
   * in [additional-featurizers].
	 */
 @SuppressWarnings("unchecked")
public NGramLanguageModelFeaturizer(String... args) throws IOException {
    if(args.length != 2 && args.length != 3)
      throw new RuntimeException("Two arguments are needed: LM file name and LM ID");
    this.lm = (LanguageModel<TK>) ARPALanguageModel.load(args[0]);
    featureName = args[1];
    featureNameWithColen = featureName + ":";
    this.ngramReweighting = false;
    this.lmOrder = lm.order();
		if (args.length == 3) {
			this.lengthNorm = Boolean.parseBoolean(args[2]);
		} else {
			this.lengthNorm = false;
		}
  }

	/**
	 * 
	 * 
	 */
	@Override
	public FeatureValue<String> featurize(Featurizable<TK,String> featurizable) {	
		if (ngramReweighting) return null;
		
		if (DEBUG) {
			System.out.printf("Sequence: %s\n\tNovel Phrase: %s\n", featurizable.partialTranslation, featurizable.translatedPhrase);
			System.out.printf("Untranslated tokens: %d\n", featurizable.untranslatedTokens);
			System.out.println("ngram scoring:");
			System.out.println("===================");
		}
		
		TK startToken =  lm.getStartToken(); 
		TK endToken = lm.getEndToken();
		
		Sequence<TK> partialTranslation;
		int startPos = featurizable.translationPosition+1;
		if (featurizable.done) {
			partialTranslation = new InsertedStartEndToken<TK>(featurizable.partialTranslation, startToken, endToken);
		} else {
			partialTranslation = new InsertedStartToken<TK>(featurizable.partialTranslation, startToken);
		}
		int limit = partialTranslation.size();
		
		double lmScore = getScore(startPos, limit, partialTranslation);
		
		if (DEBUG) {
			System.out.printf("Final score: %f\n", lmScore);
		}
		if (SVMNORM) {
			return new FeatureValue<String>(featureName, lmScore/2.0);
		} else if (lengthNorm) {
			double v;
		  synchronized(rawLMScoreHistory) { 
			  double lastLMSent = (featurizable.prior == null ? 0 :
					rawLMScoreHistory.get(featurizable.prior));
			  double lastFv = (featurizable.prior == null ? 0 : 
                     lastLMSent/featurizable.prior.partialTranslation.size());
			  double currentLMSent = lastLMSent+lmScore;
				double currentFv = currentLMSent/featurizable.partialTranslation.size();
			  v = currentFv - lastFv;
			  rawLMScoreHistory.put(featurizable, currentLMSent);
      }
			return new FeatureValue<String>(featureName, v);
    } else {
			return new FeatureValue<String>(featureName, lmScore);	
		}		
	}
	
	/**
	 * 
	 * @param startPos
	 * @param limit
	 * @param translation
	 * @return
	 */
	private double getScore(int startPos, int limit, Sequence<TK> translation) {
		double lmSumScore = 0;
		int order = lmOrder;
		
		for (int pos = startPos; pos < limit; pos++) {
			int seqStart = pos - order+1;
			if (seqStart < 0) seqStart = 0;
			Sequence<TK> ngram = translation.subsequence(seqStart, pos+1);
			double ngramScore = lm.score(ngram);
			if (ngramScore == Double.NEGATIVE_INFINITY || ngramScore != ngramScore) {
				lmSumScore += MOSES_LM_UNKNOWN_WORD_SCORE;
				continue;
			}
			lmSumScore += ngramScore;
			if (DEBUG) {
				System.out.printf("\tn-gram: %s score: %f\n", ngram, ngramScore);
			}
		}
		return lmSumScore;
	}

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<TK,String> f) {
		if (!ngramReweighting) return null;
		
		TK startToken =  lm.getStartToken(); 
		TK endToken = lm.getEndToken();
		
		Sequence<TK> partialTranslation;
		int startPos = f.translationPosition+1;
		if (f.done) {
			partialTranslation = new InsertedStartEndToken<TK>(f.partialTranslation, startToken, endToken);
		} else {
			partialTranslation = new InsertedStartToken<TK>(f.partialTranslation, startToken);
		}
		int limit = partialTranslation.size();
		
		return getFeatureList(startPos, limit, partialTranslation);
	}
	
	/**
	 * 
	 * @param startPos
	 * @param limit
	 * @param translation
	 * @return
	 */
	private List<FeatureValue<String>> getFeatureList(int startPos, int limit, Sequence<TK> translation) {
		int maxOrder = lmOrder;
		int guessSize = (limit-startPos)*maxOrder;
		List<FeatureValue<String>> feats = new ArrayList<FeatureValue<String>>(guessSize);
		if (DEBUG) {
			System.err.printf("getFeatureList(%d,%d,%s) order:%d guessSize: %d\n",startPos, limit, translation, maxOrder, guessSize);
		}
		
		for (int endWordPos = startPos; endWordPos < limit; endWordPos++) { 
			for (int order = 0; order < maxOrder; order++) {
				int beginWordPos = endWordPos - order;
				if (beginWordPos < 0) break;
				Sequence<TK> ngram = translation.subsequence(beginWordPos, endWordPos+1);
				String featName = ngram.toString(featureNameWithColen, "_");
				if (DEBUG) {
					System.err.printf("dlm(%d,%d): %s\n", beginWordPos, endWordPos+1, featName);
				}
				feats.add(new FeatureValue<String>(featName, lm.score(ngram)));
			}
		}
		
		return feats;
	}
	
	@Override
	public FeatureValue<String> phraseFeaturize(Featurizable<TK, String> f) {
		if (ngramReweighting) return null;
		
		double lmScore = getScore(0, f.translatedPhrase.size(), f.translatedPhrase);
		if (SVMNORM) {
			return new FeatureValue<String>(featureName, lmScore/2.0);
		} else if (lengthNorm) {
			return new FeatureValue<String>(featureName, lmScore/f.translatedPhrase.size());
		} else {
			return new FeatureValue<String>(featureName, lmScore);
		}
	}

	@Override
	public List<FeatureValue<String>> phraseListFeaturize(
			Featurizable<TK, String> f) {
		if (!ngramReweighting) return null;
		return getFeatureList(0, f.translatedPhrase.size(), f.translatedPhrase);
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<TK>> options,
			Sequence<TK> foreign) {
		rawLMScoreHistory.clear();
	}
	
	public void reset() { }
}


