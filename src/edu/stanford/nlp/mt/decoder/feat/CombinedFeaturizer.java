package edu.stanford.nlp.mt.decoder.feat;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;

/**
 * Container class for featurizers.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public class CombinedFeaturizer<TK, FV> extends 
    DerivationFeaturizer<TK, FV> implements RuleFeaturizer<TK, FV>,
    Cloneable {
  
  // TODO(spenceg): Experimental code.
  public static boolean DROPOUT = false;
  private static final double DROPOUT_FRACTION = 0.5;
  
  public List<Featurizer<TK, FV>> featurizers;
  private final int nbStatefulFeaturizers;

  public void deleteFeaturizers(Set<String> disabledFeaturizers) {
    System.err.println("Featurizers to disable: " + disabledFeaturizers);
    Set<String> foundFeaturizers = new HashSet<String>();
    List<Featurizer<TK, FV>> filteredFeaturizers = Generics.newLinkedList();
    for (Featurizer<TK, FV> f : featurizers) {
      String className = f.getClass().getName();
      if (f instanceof CombinedFeaturizer)
        ((CombinedFeaturizer<?, ?>) f).deleteFeaturizers(disabledFeaturizers);
      if (!disabledFeaturizers.contains(className)) {
        System.err.println("Keeping featurizer: " + f);
        filteredFeaturizers.add(f);
      } else {
        System.err.println("Disabling featurizer: " + f);
        foundFeaturizers.add(className);
      }
    }
    for (String f : disabledFeaturizers)
      if (!foundFeaturizers.contains(f))
        System.err.println("No featurizer to disable for class: " + f);
    featurizers = filteredFeaturizers;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object clone() throws CloneNotSupportedException {
    CombinedFeaturizer<TK, FV> featurizer = (CombinedFeaturizer<TK, FV>) super
        .clone();
    featurizer.featurizers = Generics.newLinkedList();
    for (Featurizer<TK, FV> f : featurizers) {
      featurizer.featurizers
          .add(f instanceof NeedsCloneable ? (DerivationFeaturizer<TK, FV>) ((NeedsCloneable<TK, FV>) f)
              .clone() : f);
    }
    return featurizer;
  }

  public List<Featurizer<TK, FV>> getNestedFeaturizers() {
    List<Featurizer<TK, FV>> allFeaturizers = Generics.newLinkedList(
        featurizers);
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof CombinedFeaturizer) {
        allFeaturizers.addAll(((CombinedFeaturizer<TK, FV>) featurizer)
            .getNestedFeaturizers());
      }
    }

    return allFeaturizers;
  }

  /**
   * Constructor.
   * 
   * @param featurizers
   */
  public CombinedFeaturizer(List<Featurizer<TK, FV>> featurizers) {
    this.featurizers = Generics.newArrayList(featurizers);
    int id = -1;
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        DerivationFeaturizer<TK, FV> sfeaturizer = (DerivationFeaturizer<TK, FV>) featurizer;
        sfeaturizer.setId(++id);
      }
    }
    this.nbStatefulFeaturizers = id + 1;
    
    // Initialize rule featurizers
    initialize();
  }

  public int getNumberStatefulFeaturizers() {
    return nbStatefulFeaturizers;
  }

  @Override
  public List<FeatureValue<FV>> featurize(Featurizable<TK, FV> f) {
    List<FeatureValue<FV>> featureValues = Generics.newLinkedList();
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        List<FeatureValue<FV>> listFeatureValues = 
            ((DerivationFeaturizer<TK,FV>) featurizer).featurize(f);
        if (listFeatureValues != null) {
          for (FeatureValue<FV> fv : listFeatureValues) {
            featureValues.add(fv);
          }
        }
      }
    }
    if (DROPOUT) {
      featureValues = dropout(featureValues, DROPOUT_FRACTION);
    }
    return featureValues;
  }

  /**
   * Implementation of a dropout regularizer.
   * 
   * @param featureValues
   * @param dropoutFraction
   * @return
   */
  private List<FeatureValue<FV>> dropout(List<FeatureValue<FV>> featureValues, double dropoutFraction) {
    Counter<FV> featureCounter = new ClassicCounter<FV>(featureValues.size());
    Set<String> retainedFeatureNames = Generics.newHashSet(featureValues.size());
    for (FeatureValue<FV> fv : featureValues) {
      featureCounter.incrementCount(fv.name, fv.value);
      if (fv.name instanceof String) {
        retainedFeatureNames.add((String) fv.name);
      } else {
        // Shouldn't happen
        throw new RuntimeException();
      }
    }
    
    // Dropout on extended features in featureNames
    retainedFeatureNames.removeAll(FeatureUtils.BASELINE_DENSE_FEATURES);
    List<String> featureNameList = Generics.newArrayList(retainedFeatureNames);
    Collections.shuffle(featureNameList);
    int maxIndex = (int) (featureNameList.size() * dropoutFraction);
    featureNameList = featureNameList.subList(0, maxIndex);
    retainedFeatureNames = Generics.newHashSet(featureNameList);
    
    List<FeatureValue<FV>> dropOutList = Generics.newLinkedList();
    for (FV featureName : featureCounter.keySet()) {
      String featName = (featureName instanceof String) ? (String) featureName : null;
      assert featName != null;
      if (retainedFeatureNames.contains(featName) || FeatureUtils.BASELINE_DENSE_FEATURES.contains(featName)) {
        double value = featureCounter.getCount(featureName);
        dropOutList.add(new FeatureValue<FV>(featureName, value));
      }
    }
    return dropOutList;
  }

  @Override
  public List<FeatureValue<FV>> ruleFeaturize(Featurizable<TK, FV> f) {
    List<FeatureValue<FV>> featureValues = Generics.newLinkedList();
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof RuleFeaturizer) {
        RuleFeaturizer<TK, FV> ruleFeaturizer = (RuleFeaturizer<TK, FV>) featurizer;
        List<FeatureValue<FV>> listFeatureValues = 
            ((RuleFeaturizer<TK, FV>) featurizer).ruleFeaturize(f);
        if (listFeatureValues != null) {
          boolean doNotCache = ruleFeaturizer.isolationScoreOnly();
          for (FeatureValue<FV> fv : listFeatureValues) {
            fv.doNotCache = doNotCache;
            featureValues.add(fv);
          }
        }
      }
    }
    if (DROPOUT) {
      featureValues = dropout(featureValues, DROPOUT_FRACTION);
    }
    return featureValues;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> sourceSequence) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        ((DerivationFeaturizer<TK,FV>) featurizer).initialize(sourceInputId, ruleList, sourceSequence);
      }
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
  public boolean isolationScoreOnly() {
    return false;
  }
}
