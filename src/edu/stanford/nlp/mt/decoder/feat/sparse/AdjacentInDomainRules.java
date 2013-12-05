package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.util.Generics;

/**
 * Indicator feature for rules that were extracted from selected sentences in
 * the bitext. Fires when two in-domain rules are adjacent in a derivation.
 * 
 * The line id file format is zero-indexed, newline delimited line numbers.
 * 
 * @author Spence Green
 *
 */
public class AdjacentInDomainRules extends DerivationFeaturizer<IString, String> {

  private static final String FEATURE_PREFIX = "IDOM";
  private final String featureName;
  private final int featureIndex;

  public AdjacentInDomainRules(String...args) {
    if (args.length != 1) {
      throw new RuntimeException("Specify the phrase table feature index of the in-domain indicator feature");
    }
    featureIndex = Integer.parseInt(args[0]);
    featureName = String.format("%s%d", FEATURE_PREFIX, featureIndex);
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {}

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {
    BoundaryState priorState = f.prior == null ? null : (BoundaryState) f.prior.getState(this);
    List<FeatureValue<String>> features = Generics.newArrayList(1);
    boolean inDomain = false;
    if (featureIndex < f.rule.abstractRule.scores.length) {
      // Don't fire for synthetic rules
      inDomain = ((int) f.rule.abstractRule.scores[featureIndex]) != 0;
      if (priorState != null && priorState.inDomain && inDomain) {
        features.add(new FeatureValue<String>(featureName, 1.0));
      }
    }
    f.setState(this, new BoundaryState(inDomain));
    return features;
  }

  private static class BoundaryState extends FeaturizerState {

    private final boolean inDomain;

    public BoundaryState(boolean inDomain) {
      this.inDomain = inDomain;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if ( ! (other instanceof BoundaryState)) {
        return false;
      } else {
        BoundaryState o = (BoundaryState) other;
        return inDomain == o.inDomain;
      }
    }

    @Override
    public int hashCode() {
      return inDomain ? 1 : 0;
    }
  }
}
