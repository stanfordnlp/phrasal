package edu.stanford.nlp.mt.base;

import java.util.*;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * 
 * @author danielcer
 * 
 */
public class FeatureValues {
  
  private FeatureValues() {}

  /**
   * Return true of the feature is cacheable and false otherwise.
   * 
   * @param feature
   * @return
   */
  public static <T> boolean isCacheable(FeatureValue<T> feature) {
    return feature instanceof CacheableFeatureValue;
  }
  
  /**
   * 
   * @param <T>
   */
  public static <T> List<FeatureValue<T>> combine(
      Collection<FeatureValue<T>> featureValues) {
    ClassicCounter<T> counter = new ClassicCounter<T>();
    for (FeatureValue<T> fv : featureValues) {
      counter.incrementCount(fv.name, fv.value);
    }
    Set<T> keys = new TreeSet<T>(counter.keySet());
    List<FeatureValue<T>> featureList = new ArrayList<FeatureValue<T>>(
        keys.size());
    for (T key : keys) {
      featureList.add(new FeatureValue<T>(key, counter.getCount(key)));
    }
    return featureList;
  }

  public static <T> Counter<T> toCounter(Collection<FeatureValue<T>> featureValues) {
    ClassicCounter<T> counter = new ClassicCounter<T>();
    for (FeatureValue<T> fv : featureValues) {
      counter.incrementCount(fv.name, fv.value);
    }
    return counter;
  }
}
