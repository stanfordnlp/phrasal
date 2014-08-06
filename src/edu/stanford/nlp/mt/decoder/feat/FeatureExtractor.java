package edu.stanford.nlp.mt.decoder.feat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
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
  private final int numDerivationFeaturizers;
  private int featureAugmentationMode = -1;
  
  /**
   * Constructor.
   * 
   * @param featurizers
   */
  public FeatureExtractor(List<Featurizer<TK, FV>> featurizers) {
    this(featurizers, null);
  }
  
  /**
   * Constructor with optional feature splitting mode.
   * 
   * @param allFeaturizers
   * @param featureAugmentationMode
   */
  public FeatureExtractor(List<Featurizer<TK, FV>> featurizers,
      String featureAugmentationMode) {
    this.featurizers = Generics.newArrayList(featurizers);
    int id = -1;
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        DerivationFeaturizer<TK, FV> sfeaturizer = (DerivationFeaturizer<TK, FV>) featurizer;
        sfeaturizer.setId(++id);
      }
    }
    this.numDerivationFeaturizers = id + 1;
    
    if (featureAugmentationMode != null) {
      if (featureAugmentationMode.equals("all")) {
        this.featureAugmentationMode = 0;
      } else if (featureAugmentationMode.equals("dense")) {
        this.featureAugmentationMode = 1;
      } else if (featureAugmentationMode.equals("extended")) {
        this.featureAugmentationMode = 2;
      }
    }
    
    // Initialize rule featurizers
    initialize();
  }

  /**
   * Remove feature templates give a <code>Set</code> of class names.
   * 
   * @param disabledFeaturizers
   */
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

  /**
   * Get all feature templates in this feature extractor. Recursively extracts
   * feature templates from nested <code>FeatureExtractor</code>s.
   * 
   * @return
   */
  public List<Featurizer<TK, FV>> getFeaturizers() {
    List<Featurizer<TK, FV>> allFeaturizers = Generics.newLinkedList(
        featurizers);
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof FeatureExtractor) {
        allFeaturizers.addAll(((FeatureExtractor<TK, FV>) featurizer)
            .getFeaturizers());
      }
    }
    return allFeaturizers;
  }

  /**
   * Returns the number of <code>DerivationFeaturizer</code>s.
   * 
   * @return
   */
  public int getNumDerivationFeaturizers() {
    return numDerivationFeaturizers;
  }

  /**
   * Extract derivation features.
   */
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
            if (featureAugmentationMode >= 0) {
              augmentFeatureValue(fv, f.derivation.sourceInputProperties, featureValues);
            }
          }
        }
      }
    }
    return featureValues;
  }

  /**
   * Extract rule features.
   */
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
            if (featureAugmentationMode >= 0) {
              augmentFeatureValue(fv, f.sourceInputProperties, featureValues);
            }
          }
        }
      }
    }
    return featureValues;
  }

  /**
   * Feature space augmentation a la Daume III (2007).
   * 
   * @param fv
   * @param sourceInputProperties
   * @param featureValues
   */
  @SuppressWarnings("unchecked")
  private void augmentFeatureValue(FeatureValue<FV> fv,
      InputProperties sourceInputProperties, List<FeatureValue<FV>> featureValues) {
    final String genre = sourceInputProperties.containsKey(InputProperty.Domain)
        ? (String) sourceInputProperties.get(InputProperty.Domain) : null;
    if (genre != null) {
      if (featureAugmentationMode == 0 ||
          (featureAugmentationMode == 1 && fv.isDenseFeature) ||
          (featureAugmentationMode == 2 && ! fv.isDenseFeature)) {
        String featureValue = String.format("%s-%s", fv.name.toString(), genre);
        featureValues.add(new FeatureValue<FV>((FV) featureValue, fv.value, fv.isDenseFeature));
      }
    }
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
