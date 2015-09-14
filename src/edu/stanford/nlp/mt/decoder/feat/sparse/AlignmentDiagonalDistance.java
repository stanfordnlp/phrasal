package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
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
    final int value = Math.abs(f.sourcePosition - f.targetPosition);
    f.setState(this, new DiagonalState(value));
    return Collections.singletonList(new FeatureValue<String>(FEATURE_NAME, value));
  }
  
  private static class DiagonalState extends FeaturizerState {

    private final int distance;

    public DiagonalState(int distortion) {
      this.distance = distortion;
    }
    
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if ( ! (other instanceof DiagonalState)) {
        return false;
      } else {
        DiagonalState o = (DiagonalState) other;
        return this.distance == o.distance;
      }
    }

    @Override
    public int hashCode() {
      return distance;
    }
  }
}
