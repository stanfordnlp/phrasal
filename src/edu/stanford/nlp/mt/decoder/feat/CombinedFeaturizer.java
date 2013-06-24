package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

/**
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class CombinedFeaturizer<TK, FV> implements
    RichIncrementalFeaturizer<TK, FV>, IsolatedPhraseFeaturizer<TK, FV>,
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
          .add(f instanceof ClonedFeaturizer ? (IncrementalFeaturizer<TK, FV>) ((ClonedFeaturizer<TK, FV>) f)
              .clone() : f);
    }
    return featurizer;
  }

  /**
	 * 
	 */

  public List<Featurizer<TK, FV>> getFeaturizers() {
    return Generics.newArrayList(featurizers);
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
    this.featurizers = new ArrayList<Featurizer<TK, FV>>(featurizers);
    int id = -1;
    for (Featurizer<TK, FV> featurizer : featurizers) {
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
  public CombinedFeaturizer(Featurizer<TK, FV>...featurizers) {
    this(Arrays.asList(featurizers));
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<FeatureValue<FV>> listFeaturize(Featurizable<TK, FV> f) {

    List<Object> featureValueLists = Generics.newArrayList(featurizers.size());
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if ( ! (featurizer instanceof IncrementalFeaturizer)) {
        continue;
      }
      IncrementalFeaturizer<TK,FV> incFeaturizer = (IncrementalFeaturizer<TK,FV>) featurizer;
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
  public List<FeatureValue<FV>> phraseListFeaturize(Featurizable<TK, FV> f) {
    List<FeatureValue<FV>> featureValues = Generics.newLinkedList();
    for (Featurizer<TK, FV> featurizer : featurizers) {
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
  public void initialize(int sourceInputId,
      List<ConcreteTranslationOption<TK,FV>> options, Sequence<TK> foreign, Index<String> featureIndex) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof IncrementalFeaturizer) {
        ((IncrementalFeaturizer<TK,FV>) featurizer).initialize(sourceInputId, options, foreign, featureIndex);
      }
    }
  }

  @Override
  public void reset() {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof IncrementalFeaturizer) {
        ((IncrementalFeaturizer<TK,FV>) featurizer).reset();
      }
    }
  }

  @Override
  public void dump(Featurizable<TK, FV> f) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof RichIncrementalFeaturizer) {
        ((RichIncrementalFeaturizer<TK, FV>) featurizer).dump(f);
      }
    }
  }

  @Override
  public void rerankingMode(boolean r) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof RichIncrementalFeaturizer) {
        ((RichIncrementalFeaturizer<TK, FV>) featurizer).rerankingMode(r);
      }
    }
  }

  @Override
  public void initialize(Index<String> featureIndex) {
    // Initialize the IsolatedPhraseFeaturizers
    for (Featurizer<TK,FV> featurizer : featurizers) {
      if (featurizer instanceof IsolatedPhraseFeaturizer) {
        ((IsolatedPhraseFeaturizer<TK,FV>) featurizer).initialize(featureIndex);
      }
    }
  }
}
