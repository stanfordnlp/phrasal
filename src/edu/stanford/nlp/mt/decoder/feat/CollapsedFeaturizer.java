package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.Index;

/**
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class CollapsedFeaturizer<TK, FV> implements
    CombinationFeaturizer<TK, FV>, RuleFeaturizer<TK, FV> {
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
  public FeatureValue<FV> featurize(Featurizable<TK, FV> f) {
    double value = 0;

    int sz = featurizers.size();
    for (int i = 0; i < sz; i++) {
      Featurizer<TK, FV> featurizer = featurizers.get(i);
      if ( ! (featurizer instanceof CombinationFeaturizer)) {
        continue;
      }
      CombinationFeaturizer<TK,FV> incFeaturizer = (CombinationFeaturizer<TK,FV>) featurizer;
      FeatureValue<FV> singleFeatureValue = incFeaturizer.featurize(f);
      if (singleFeatureValue != null) {
        value += getIndividualWeight(singleFeatureValue.name)
            * featurizerWts[i] * singleFeatureValue.value;
      }

      List<FeatureValue<FV>> listFeatureValues = incFeaturizer.listFeaturize(f);
      if (listFeatureValues != null) {
        for (FeatureValue<FV> featureValue : listFeatureValues) {
          value += getIndividualWeight(featureValue.name) * featurizerWts[i]
              * featureValue.value;
        }
      }
    }

    return new FeatureValue<FV>(combinedFeatureName, value);
  }

  @Override
  public List<FeatureValue<FV>> listFeaturize(Featurizable<TK, FV> f) {
    return null;
  }

  @Override
  public FeatureValue<FV> phraseFeaturize(Featurizable<TK, FV> f) {
    double value = 0;

    int sz = featurizers.size();
    for (int i = 0; i < sz; i++) {
      Featurizer<TK, FV> featurizer = featurizers.get(i);
      if (!(featurizer instanceof RuleFeaturizer))
        continue;
      RuleFeaturizer<TK, FV> isoFeaturizer = (RuleFeaturizer<TK, FV>) featurizer;

      FeatureValue<FV> singleFeatureValue = isoFeaturizer.phraseFeaturize(f);

      if (singleFeatureValue != null) {
        value += getIndividualWeight(singleFeatureValue.name)
            * featurizerWts[i] * singleFeatureValue.value;
      }

      List<FeatureValue<FV>> listFeatureValues = isoFeaturizer
          .phraseListFeaturize(f);
      if (listFeatureValues != null) {
        for (FeatureValue<FV> featureValue : listFeatureValues) {
          value += getIndividualWeight(featureValue.name) * featurizerWts[i]
              * featureValue.value;
        }
      }
    }

    return new FeatureValue<FV>(combinedFeatureName, value);
  }

  @Override
  public List<FeatureValue<FV>> phraseListFeaturize(Featurizable<TK, FV> f) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteTranslationOption<TK,FV>> options, Sequence<TK> foreign, Index<String> featureIndex) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      ((CombinationFeaturizer<TK, FV>) featurizer).initialize(sourceInputId, options, foreign, featureIndex);
    }
  }

  public void reset() {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      ((CombinationFeaturizer<TK, FV>) featurizer).reset();
    }
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {
    // Initialize the IsolatedPhraseFeaturizers
    for (Featurizer<TK,FV> featurizer : featurizers) {
      if (featurizer instanceof RuleFeaturizer) {
        ((RuleFeaturizer<TK,FV>) featurizer).initialize(featureIndex);
      }
    }
  }
}
