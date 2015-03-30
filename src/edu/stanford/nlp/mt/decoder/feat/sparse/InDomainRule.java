package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;


/**
 * Indicator feature for rules that were extracted from selected sentences in
 * the bitext. Optionally fires when two in-domain rules are adjacent in a derivation.
 * 
 * The phrase table must be extracted with the <code>InDomainFeatureExtractor</code>
 * in the extractor list (see <code>PhraseExtract</code> for how to add extractors).
 * 
 * @author Spence Green
 *
 */
public class InDomainRule extends DerivationFeaturizer<IString, String> {

  private static final String FEATURE_PREFIX = "DOM";
  
  private final boolean addAdjacentRuleFeature;
  
  /**
   * Constructor.
   * 
   * @param args
   */
  public InDomainRule(String...args) {
    if (args.length < 1) {
      throw new RuntimeException("Specify the phrase table feature index of the in-domain indicator feature");
    }
    Properties options = FeatureUtils.argsToProperties(args);
    this.addAdjacentRuleFeature = options.containsKey("adjacentRuleFeature");
  }

  @Override
  public void initialize(int sourceInputId,
      Sequence<IString> source) {}

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {

    List<FeatureValue<String>> features = new LinkedList<>();
    final int featureIndex = f.sourceInputProperties.containsKey(InputProperty.RuleFeatureIndex) ?
        Integer.valueOf((String) f.sourceInputProperties.get(InputProperty.RuleFeatureIndex)) : -1;
    
    if (featureIndex < 0) {
      throw new RuntimeException("RuleFeatureIndex property not specified for input: " + String.valueOf(f.sourceInputId));
    }
    BoundaryState priorState = f.prior == null ? null : (BoundaryState) f.prior.getState(this);

    // Synthetic rules are always in-domain
    final boolean inDomain = featureIndex < f.rule.abstractRule.scores.length ?
        Math.round(f.rule.abstractRule.scores[featureIndex]) != 0 : true;
    if (inDomain) {
      // In-domain rule
      String featureStringDefault = String.format("%s:inrule", FEATURE_PREFIX);
      features.add(new FeatureValue<String>(featureStringDefault, 1.0));

      if (addAdjacentRuleFeature && priorState != null && priorState.inDomain) {
        String featureString = String.format("%s:adjrule", FEATURE_PREFIX);
        features.add(new FeatureValue<String>(featureString, 1.0));
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
