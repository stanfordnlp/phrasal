package mt.decoder.efeat;

import java.util.List;

import mt.base.*;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.util.IString;

/**
 * Assign large cost to phrase if sentence boundaries are not at the right place.
 * 
 * @author Michel Galley
 */
public class SentenceBoundaryFeaturizer implements IncrementalFeaturizer<IString,String>, IsolatedPhraseFeaturizer<IString, String> {
	static public String FEATURE_NAME = "SentenceBoundary";
	static public final double BAD_SENTENCE_BOUNDARY_PENALTY = -100.0;

  private static final IString startToken = new IString("<s>");
  private static final IString endToken = new IString("</s>");

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {

    double cost = BAD_SENTENCE_BOUNDARY_PENALTY;

    for (int i=0; i<f.translatedPhrase.size(); ++i) {
      if(f.translatedPhrase.get(i).id == startToken.id) {
        if(i == 0 && f.translationPosition == 0)
          cost = -cost;
        return new FeatureValue<String>(FEATURE_NAME, cost);
      }
      if(f.translatedPhrase.get(i).id == endToken.id) {
        if(i+1 == f.translatedPhrase.size() && f.done)
          cost = -cost;
        return new FeatureValue<String>(FEATURE_NAME, cost);
      }
    }

		return new FeatureValue<String>(FEATURE_NAME, 0.0);
	}

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {
		return null;
	}

	@Override
	public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
		return featurize(f);
	}

	@Override
	public List<FeatureValue<String>> phraseListFeaturize(
			Featurizable<IString, String> f) {
		return null;
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	public void reset() { }
}
