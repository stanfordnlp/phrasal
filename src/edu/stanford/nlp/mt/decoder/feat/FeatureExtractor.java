package edu.stanford.nlp.mt.decoder.feat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.Sequence;
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
public class FeatureExtractor<TK, FV> extends 
    DerivationFeaturizer<TK, FV> implements RuleFeaturizer<TK, FV>,
    Cloneable {
  
  private List<Featurizer<TK, FV>> featurizers;
  private final int nbStatefulFeaturizers;

  /**
   * Constructor.
   * 
   * @param featurizers
   */
  public FeatureExtractor(List<Featurizer<TK, FV>> featurizers) {
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
  
  public void deleteFeaturizers(Set<String> disabledFeaturizers) {
    System.err.println("Featurizers to disable: " + disabledFeaturizers);
    Set<String> foundFeaturizers = new HashSet<String>();
    List<Featurizer<TK, FV>> filteredFeaturizers = Generics.newLinkedList();
    for (Featurizer<TK, FV> f : featurizers) {
      String className = f.getClass().getName();
      if (f instanceof FeatureExtractor)
        ((FeatureExtractor<?, ?>) f).deleteFeaturizers(disabledFeaturizers);
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
    FeatureExtractor<TK, FV> featurizer = (FeatureExtractor<TK, FV>) super
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
      if (featurizer instanceof FeatureExtractor) {
        allFeaturizers.addAll(((FeatureExtractor<TK, FV>) featurizer)
            .getNestedFeaturizers());
      }
    }

    return allFeaturizers;
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
    return featureValues;
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
