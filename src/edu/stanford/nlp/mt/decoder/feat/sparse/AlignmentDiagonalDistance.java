package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Penalizes distance from the diagonal when a rule is inserted.
 * 
 * @author Spence Green
 *
 */
public class AlignmentDiagonalDistance extends DerivationFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "RDG";

  @Override
  public void initialize(int sourceInputId, Sequence<IString> source) {}

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    if (f.targetPhrase.size() == 0) {
      // Source deletion rule
      return null;
    }
    final int value = Math.abs(f.sourcePosition - f.targetPosition);
    return Collections.singletonList(new FeatureValue<String>(FEATURE_NAME, value));
  }  
}
