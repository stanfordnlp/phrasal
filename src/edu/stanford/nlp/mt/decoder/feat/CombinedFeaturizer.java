package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class CombinedFeaturizer<TK, FV> implements
    RichIncrementalFeaturizer<TK, FV>, IsolatedPhraseFeaturizer<TK, FV>,
    Cloneable {
  public List<IncrementalFeaturizer<TK, FV>> featurizers;

  private final int nbStatefulFeaturizers;

  public void deleteFeaturizers(Set<String> disabledFeaturizers) {
    System.err.println("Featurizers to disable: " + disabledFeaturizers);
    Set<String> foundFeaturizers = new HashSet<String>();
    List<IncrementalFeaturizer<TK, FV>> filteredFeaturizers = new LinkedList<IncrementalFeaturizer<TK, FV>>();
    for (IncrementalFeaturizer<TK, FV> f : featurizers) {
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
    featurizer.featurizers = new LinkedList<IncrementalFeaturizer<TK, FV>>();
    for (IncrementalFeaturizer<TK, FV> f : featurizers) {
      featurizer.featurizers
          .add(f instanceof ClonedFeaturizer ? (IncrementalFeaturizer<TK, FV>) ((ClonedFeaturizer<TK, FV>) f)
              .clone() : f);
    }
    return featurizer;
  }

  /**
	 * 
	 */

  public List<IncrementalFeaturizer<TK, FV>> getFeaturizers() {
    return new ArrayList<IncrementalFeaturizer<TK, FV>>(featurizers);
  }

  public List<IncrementalFeaturizer<TK, FV>> getNestedFeaturizers() {
    List<IncrementalFeaturizer<TK, FV>> allFeaturizers = new LinkedList<IncrementalFeaturizer<TK, FV>>(
        featurizers);
    for (IncrementalFeaturizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof CombinedFeaturizer) {
        allFeaturizers.addAll(((CombinedFeaturizer<TK, FV>) featurizer)
            .getNestedFeaturizers());
      }
    }

    return allFeaturizers;
  }

  /**
	 */
  public CombinedFeaturizer(List<IncrementalFeaturizer<TK, FV>> featurizers) {
    this.featurizers = new ArrayList<IncrementalFeaturizer<TK, FV>>(featurizers);
    int id = -1;
    for (IncrementalFeaturizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof StatefulFeaturizer) {
        StatefulFeaturizer<TK, FV> sfeaturizer = (StatefulFeaturizer<TK, FV>) featurizer;
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
  public CombinedFeaturizer(IncrementalFeaturizer<TK, FV>... featurizers) {
    this(Arrays.asList(featurizers));
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<FeatureValue<FV>> listFeaturize(Featurizable<TK, FV> f) {

    List<Object> featureValueLists = new ArrayList<Object>(featurizers.size());
    for (IncrementalFeaturizer<TK, FV> featurizer : featurizers) {

      // if a single feature value is available from the method
      // featurizer#featurize, then insert it into the aggregate
      // list
      FeatureValue<FV> singleFeatureValue = featurizer.featurize(f);
      if (singleFeatureValue != null) {
        featureValueLists.add(singleFeatureValue);
      }

      // if a list of feature values are available from the method
      // featurizer#listFeaturizer, then insert it into the aggregate
      // list
      List<FeatureValue<FV>> listFeatureValues = featurizer.listFeaturize(f);
      if (listFeatureValues != null) {
        featureValueLists.add(listFeatureValues);
      }
    }
    ArrayList<FeatureValue<FV>> featureValues = new ArrayList<FeatureValue<FV>>(
        featureValueLists.size());
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

  @SuppressWarnings("unchecked")
  @Override
  public List<FeatureValue<FV>> phraseListFeaturize(Featurizable<TK, FV> f) {
    List<FeatureValue<FV>> featureValues = new LinkedList<FeatureValue<FV>>();
    for (IncrementalFeaturizer<TK, FV> featurizer : featurizers) {
      if (!(featurizer instanceof IsolatedPhraseFeaturizer))
        continue;
      IsolatedPhraseFeaturizer<TK, FV> isoFeaturizer = (IsolatedPhraseFeaturizer<TK, FV>) featurizer;

      FeatureValue<FV> singleFeatureValue = isoFeaturizer.phraseFeaturize(f);
      if (singleFeatureValue != null) {
        featureValues.add(singleFeatureValue);
      }

      List<FeatureValue<FV>> listFeatureValues = isoFeaturizer
          .phraseListFeaturize(f);
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
  public FeatureValue<FV> phraseFeaturize(Featurizable<TK, FV> f) {
    return null;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<TK>> options,
      Sequence<TK> foreign) {
    for (IncrementalFeaturizer<TK, FV> featurizer : featurizers) {
      featurizer.initialize(options, foreign);
    }
  }

  @Override
  public void reset() {
    for (IncrementalFeaturizer<TK, FV> featurizer : featurizers) {
      featurizer.reset();
    }
  }

  @Override
  public void dump(Featurizable<TK, FV> f) {
    for (IncrementalFeaturizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof RichIncrementalFeaturizer) {
        ((RichIncrementalFeaturizer<TK, FV>) featurizer).dump(f);
      }
    }
  }

  @Override
  public void rerankingMode(boolean r) {
    for (IncrementalFeaturizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof RichIncrementalFeaturizer) {
        ((RichIncrementalFeaturizer<TK, FV>) featurizer).rerankingMode(r);
      }
    }
  }
}
