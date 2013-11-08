package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.Generics;

/**
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class CollapsedFeaturizer<TK, FV> extends 
    DerivationFeaturizer<TK, FV> implements RuleFeaturizer<TK, FV> {
  final public List<Featurizer<TK, FV>> featurizers;
  final double[] featurizerWts;
  final Map<FV, Double> weightMap;
  final FV combinedFeatureName;

  /**
	 */
  public CollapsedFeaturizer(FV combinedFeatureName, Map<FV, Double> weightMap,
      Featurizer<TK, FV>... featurizers) {
    this.combinedFeatureName = combinedFeatureName;
    this.featurizers = Arrays.asList(featurizers);
    featurizerWts = new double[featurizers.length];
    Arrays.fill(featurizerWts, 1.0);
    this.weightMap = new HashMap<FV, Double>(weightMap);
  }

  /**
	 */
  public CollapsedFeaturizer(FV combinedFeatureName, double[] featurizerWts,
      Featurizer<TK, FV>... featurizers) {
    this.combinedFeatureName = combinedFeatureName;
    this.featurizers = Arrays.asList(featurizers);
    this.featurizerWts = Arrays.copyOf(featurizerWts, featurizerWts.length);
    weightMap = null;
  }

  /**
	 */

  public CollapsedFeaturizer(FV combinedFeatureName,
      Featurizer<TK, FV>... featurizers) {
    this.combinedFeatureName = combinedFeatureName;
    this.featurizers = Arrays.asList(featurizers);
    featurizerWts = new double[featurizers.length];
    Arrays.fill(featurizerWts, 1.0);
    weightMap = null;
  }

  private double getIndividualWeight(FV featureName) {
    if (weightMap == null) {
      return 1.0;
    }
    Double wt = weightMap.get(featureName);
    if (wt == null) {
      return 0.0;
    }
    return wt;
  }

  @Override
  public List<FeatureValue<FV>> featurize(Featurizable<TK, FV> f) {
    double value = 0;

    int sz = featurizers.size();
    for (int i = 0; i < sz; i++) {
      Featurizer<TK, FV> featurizer = featurizers.get(i);
      if ( ! (featurizer instanceof DerivationFeaturizer)) {
        continue;
      }
      DerivationFeaturizer<TK,FV> incFeaturizer = (DerivationFeaturizer<TK,FV>) featurizer;
      List<FeatureValue<FV>> listFeatureValues = incFeaturizer.featurize(f);
      if (listFeatureValues != null) {
        for (FeatureValue<FV> featureValue : listFeatureValues) {
          value += getIndividualWeight(featureValue.name) * featurizerWts[i]
              * featureValue.value;
        }
      }
    }
    
    List<FeatureValue<FV>> features = Generics.newLinkedList();
    features.add(new FeatureValue<FV>(combinedFeatureName, value));
    return features;
  }

  @Override
  public List<FeatureValue<FV>> ruleFeaturize(Featurizable<TK, FV> f) {
    double value = 0;

    int sz = featurizers.size();
    for (int i = 0; i < sz; i++) {
      Featurizer<TK, FV> featurizer = featurizers.get(i);
      if (!(featurizer instanceof RuleFeaturizer))
        continue;
      RuleFeaturizer<TK, FV> isoFeaturizer = (RuleFeaturizer<TK, FV>) featurizer;

      List<FeatureValue<FV>> listFeatureValues = isoFeaturizer
          .ruleFeaturize(f);
      if (listFeatureValues != null) {
        for (FeatureValue<FV> featureValue : listFeatureValues) {
          value += getIndividualWeight(featureValue.name) * featurizerWts[i]
              * featureValue.value;
        }
      }
    }

    List<FeatureValue<FV>> features = Generics.newLinkedList();
    features.add(new FeatureValue<FV>(combinedFeatureName, value));
    return features;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<TK,FV>> options, Sequence<TK> foreign) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      ((DerivationFeaturizer<TK, FV>) featurizer).initialize(sourceInputId, options, foreign);
    }
  }
  
  @Override
  public void initialize() {
    // Initialize the IsolatedPhraseFeaturizers
    for (Featurizer<TK,FV> featurizer : featurizers) {
      if (featurizer instanceof RuleFeaturizer) {
        ((RuleFeaturizer<TK,FV>) featurizer).initialize();
      }
    }
  }
  
  @Override
  public boolean constructInternalAlignments() {
    for (Featurizer<TK,FV> featurizer : featurizers) {
      if (featurizer.constructInternalAlignments()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
