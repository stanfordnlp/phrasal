package edu.stanford.nlp.mt.decoder.feat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.Generics;

/**
 * Container class for featurizers.
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class CombinedFeaturizer<TK, FV> extends 
    DerivationFeaturizer<TK, FV> implements RuleFeaturizer<TK, FV>,
    Cloneable {
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

  /**
	 */
  public CombinedFeaturizer(Featurizer<TK, FV>...featurizers) {
    this(Arrays.asList(featurizers));
    
    // Initialize rule featurizers
    initialize();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<FeatureValue<FV>> featurize(Featurizable<TK, FV> f) {

    List<Object> featureValueLists = Generics.newArrayList(featurizers.size());
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if ( ! (featurizer instanceof DerivationFeaturizer)) {
        continue;
      }
      DerivationFeaturizer<TK,FV> incFeaturizer = (DerivationFeaturizer<TK,FV>) featurizer;
      List<FeatureValue<FV>> listFeatureValues = incFeaturizer.featurize(f);
      if (listFeatureValues != null) {
        featureValueLists.add(listFeatureValues);
      }
    }
    
    List<FeatureValue<FV>> featureValues = Generics.newArrayList(featureValueLists.size());
    for (Object o : featureValueLists) {
      if (o instanceof FeatureValue) {
        featureValues.add((FeatureValue<FV>) o);
        continue;
      }
      List<FeatureValue<FV>> listFeatureValues = (List<FeatureValue<FV>>) o;
      // profiling reveals that addAll is slow due to a buried call to clone()
      for (FeatureValue<FV> fv : listFeatureValues) {
        if (fv.name != null && fv.value != 0.0)
          featureValues.add(fv);
      }
    }
    return featureValues;
  }

  @Override
  public List<FeatureValue<FV>> ruleFeaturize(Featurizable<TK, FV> f) {
    List<FeatureValue<FV>> featureValues = Generics.newLinkedList();
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (!(featurizer instanceof RuleFeaturizer)) {
        continue;
      }
      RuleFeaturizer<TK, FV> ruleFeaturizer = (RuleFeaturizer<TK, FV>) featurizer;
      List<FeatureValue<FV>> listFeatureValues = ruleFeaturizer
          .ruleFeaturize(f);
      if (listFeatureValues != null) {
        boolean doNotCache = (ruleFeaturizer instanceof RuleIsolationScoreFeaturizer);
        // profiling reveals that addAll is slow due to a buried call to clone()
        for (FeatureValue<FV> fv : listFeatureValues) {
          if (fv.name != null) {
            fv.doNotCache = doNotCache;
            featureValues.add(fv);
          }
        }
      }
    }
    return featureValues;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> foreign) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        ((DerivationFeaturizer<TK,FV>) featurizer).initialize(sourceInputId, ruleList, foreign);
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
  public boolean constructInternalAlignments() {
    for (Featurizer<TK,FV> featurizer : featurizers) {
      if (featurizer.constructInternalAlignments()) {
        return true;
      }
    }
    return false;
  }
}
