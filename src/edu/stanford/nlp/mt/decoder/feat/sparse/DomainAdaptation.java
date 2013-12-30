package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Indicator feature for rules that were extracted from selected sentences in
 * the bitext. Fires when two in-domain rules are adjacent in a derivation.
 * 
 * The line id file format is zero-indexed, newline delimited line numbers.
 * 
 * @author Spence Green
 *
 */
public class DomainAdaptation extends DerivationFeaturizer<IString, String> {

  private static final String FEATURE_PREFIX = "DOM";
  private final Map<Integer,Pair<String,Integer>> sourceIdInfoMap;
  private final boolean addAdjacentRuleFeature;
  private final boolean addDomainSpecificFeatures;
  
  public DomainAdaptation(String...args) {
    if (args.length < 1) {
      throw new RuntimeException("Specify the phrase table feature index of the in-domain indicator feature");
    }
    sourceIdInfoMap = SparseFeatureUtils.loadGenreFile(args[0]);
    addAdjacentRuleFeature = args.length > 1 ? Boolean.valueOf(args[1]) : false;
    addDomainSpecificFeatures = args.length > 2 ? Boolean.valueOf(args[2]) : false;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {}

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {
    Pair<String,Integer> genreInfo = sourceIdInfoMap.containsKey(f.sourceInputId) ?
        sourceIdInfoMap.get(f.sourceInputId) : null;
    List<FeatureValue<String>> features = Generics.newLinkedList();
    if (genreInfo != null) {
      final String genre = genreInfo.first();
      final int featureIndex = genreInfo.second();
      BoundaryState priorState = f.prior == null ? null : (BoundaryState) f.prior.getState(this);

      // Synthetic rules are always in-domain
      final boolean inDomain = featureIndex < f.rule.abstractRule.scores.length ?
          Math.round(f.rule.abstractRule.scores[featureIndex]) != 0 : true;
      if (inDomain) {
        String featureString = String.format("%s:inrule", FEATURE_PREFIX);
        features.add(new FeatureValue<String>(featureString, 1.0));
        if (addDomainSpecificFeatures) {
          features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
        }
      }
      if (addAdjacentRuleFeature && priorState != null && priorState.inDomain && inDomain) {
        String featureString = String.format("%s:adjrule", FEATURE_PREFIX);
        features.add(new FeatureValue<String>(featureString, 1.0));
        if (addDomainSpecificFeatures) {
          features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
        }
      }
      f.setState(this, new BoundaryState(inDomain));
    }
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
