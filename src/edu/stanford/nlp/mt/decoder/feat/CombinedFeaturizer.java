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
import edu.stanford.nlp.util.Index;

/**
 * Container class for featurizers.
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class CombinedFeaturizer<TK, FV> implements
    RichCombinationFeaturizer<TK, FV>, RuleFeaturizer<TK, FV>,
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
          .add(f instanceof NeedsCloneable ? (CombinationFeaturizer<TK, FV>) ((NeedsCloneable<TK, FV>) f)
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
      if (featurizer instanceof NeedsState) {
        NeedsState<TK, FV> sfeaturizer = (NeedsState<TK, FV>) featurizer;
        sfeaturizer.setId(++id);
      }
    }
    this.nbStatefulFeaturizers = id + 1;
  }

  public int getNumberStatefulFeaturizers() {
    return nbStatefulFeaturizers;
  }

  /**
	 */
  public CombinedFeaturizer(Featurizer<TK, FV>...featurizers) {
    this(Arrays.asList(featurizers));
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<FeatureValue<FV>> listFeaturize(Featurizable<TK, FV> f) {

    List<Object> featureValueLists = Generics.newArrayList(featurizers.size());
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if ( ! (featurizer instanceof CombinationFeaturizer)) {
        continue;
      }
      CombinationFeaturizer<TK,FV> incFeaturizer = (CombinationFeaturizer<TK,FV>) featurizer;
      // if a single feature value is available from the method
      // featurizer#featurize, then insert it into the aggregate
      // list
      FeatureValue<FV> singleFeatureValue = incFeaturizer.featurize(f);
      if (singleFeatureValue != null) {
        featureValueLists.add(singleFeatureValue);
      }

      // if a list of feature values are available from the method
      // featurizer#listFeaturizer, then insert it into the aggregate
      // list
      List<FeatureValue<FV>> listFeatureValues = incFeaturizer.listFeaturize(f);
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
  public FeatureValue<FV> featurize(Featurizable<TK, FV> f) {
    return null;
  }

  @Override
  public List<FeatureValue<FV>> ruleFeaturize(Featurizable<TK, FV> f) {
    List<FeatureValue<FV>> featureValues = Generics.newLinkedList();
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (!(featurizer instanceof RuleFeaturizer)) {
        continue;
      }
      RuleFeaturizer<TK, FV> isoFeaturizer = (RuleFeaturizer<TK, FV>) featurizer;
      List<FeatureValue<FV>> listFeatureValues = isoFeaturizer
          .ruleFeaturize(f);
      if (listFeatureValues != null) {
        // profiling reveals that addAll is slow due to a buried call to clone()
        for (FeatureValue<FV> fv : listFeatureValues) {
          if (fv.name != null)
            featureValues.add(fv);
        }
      }
    }
    return featureValues;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> foreign, Index<String> featureIndex) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof CombinationFeaturizer) {
        ((CombinationFeaturizer<TK,FV>) featurizer).initialize(sourceInputId, ruleList, foreign, featureIndex);
      }
    }
  }

  @Override
  public void reset() {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof CombinationFeaturizer) {
        ((CombinationFeaturizer<TK,FV>) featurizer).reset();
      }
    }
  }

  @Override
  public void dump(Featurizable<TK, FV> f) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof RichCombinationFeaturizer) {
        ((RichCombinationFeaturizer<TK, FV>) featurizer).dump(f);
      }
    }
  }

  @Override
  public void rerankingMode(boolean r) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof RichCombinationFeaturizer) {
        ((RichCombinationFeaturizer<TK, FV>) featurizer).rerankingMode(r);
      }
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
