package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Moses phrase penalty generated here so that you don't have to read it countless times 
 * from the phrase table.
 * 
 * @author Michel Galley
 *
 * @param <TK>
 */
@SuppressWarnings("unused")
public class PhrasePenaltyFeaturizer<TK> implements IncrementalFeaturizer<TK,String>, IsolatedPhraseFeaturizer<TK, String>  {
	static public String FEATURE_NAME = "TM:phrasePenalty";
  // mg2008: please don't change to "= 1" since not exactly the same value:
  private double phrasePenalty = Math.log(2.718);

  public PhrasePenaltyFeaturizer(String... args) {
    if (args.length >= 1) {
      assert (args.length == 1);
      phrasePenalty = Double.parseDouble(args[0]);
    }
  }

	@Override
	public FeatureValue<String> featurize(Featurizable<TK,String> f) {
		return new FeatureValue<String>(FEATURE_NAME, phrasePenalty);
	}

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<TK,String> f) {
		return null;
	}

	@Override
	public FeatureValue<String> phraseFeaturize(Featurizable<TK, String> f) {
		return featurize(f);
	}

	@Override
	public List<FeatureValue<String>> phraseListFeaturize(
			Featurizable<TK, String> f) {
		return null;
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<TK>> options,
			Sequence<TK> foreign) {
	}

  @Override
	public void reset() { }
}
