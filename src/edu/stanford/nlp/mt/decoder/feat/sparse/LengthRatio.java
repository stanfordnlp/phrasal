package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Length ratio "trait" feature from Devlin et al. (2012).
 * 
 * @author Spence Green
 *
 */
public class LengthRatio extends DerivationFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "LRA";
  
  @Override
  public void initialize(int sourceInputId,
      Sequence<IString> source) {
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    double ratio = (double) f.targetPhrase.size() / (double) f.sourceSentence.size();
    return Collections.singletonList(new FeatureValue<>(FEATURE_NAME, ratio));
  }
}
