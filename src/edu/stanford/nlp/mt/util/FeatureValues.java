package edu.stanford.nlp.mt.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Utilities for manipulating feature values.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 */
public class FeatureValues {

  private FeatureValues() {}

  /**
   * Convert a collection of feature values to a counter.
   * 
   * @param featureValues
   * @return
   */
  public static <T> Counter<T> toCounter(Collection<FeatureValue<T>> featureValues) {
    Counter<T> counter = new ClassicCounter<T>();
    for (FeatureValue<T> fv : featureValues) {
      counter.incrementCount(fv.name, fv.value);
    }
    return counter;
  }

  /**
   * Aggregate feature values stored in a chain of hypotheses.
   * 
   * @param hyp
   * @return
   */
  public static <TK,FV> FeatureValueCollection<FV> combine(
      Derivation<TK, FV> hyp) {
    Counter<FV> counter = new ClassicCounter<FV>();
    for (; hyp != null; hyp = hyp.preceedingDerivation) {
      if (hyp.localFeatures != null) {
        for (FeatureValue<FV> feature : hyp.localFeatures) {
          counter.incrementCount(feature.name, feature.value);
        }
      }
    }
    Set<FV> featureNames = new TreeSet<FV>(counter.keySet());
    FeatureValueCollection<FV> combinedList = new FeatureValueList<FV>(featureNames.size());
    for (FV feature : featureNames) {
      combinedList.add(new FeatureValue<FV>(feature, counter.getCount(feature)));
    }
    return combinedList;
  }

  private static class FeatureValueList<FV2> extends ArrayList<FeatureValue<FV2>> implements FeatureValueCollection<FV2> {
    private static final long serialVersionUID = -586793184334698666L;
    public FeatureValueList(int initialCapacity) {
      super(initialCapacity);
    }
    @Override
    public Object clone() {
      return super.clone();
    }
  }

}
