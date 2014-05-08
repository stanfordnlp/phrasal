package edu.stanford.nlp.mt.util;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;

import java.util.Collection;
import java.util.Iterator;
import java.util.Arrays;

/**
 * @author Daniel Cer
 */
public class SparseFeatureValueCollection<E> implements
    FeatureValueCollection<E> {

  Index<E> featureIndex;
  double[] featureValues;
  int[] featureIndices;

  @Override
  @SuppressWarnings("unchecked")
  public Object clone() throws CloneNotSupportedException {
    SparseFeatureValueCollection<E> c = (SparseFeatureValueCollection<E>) super
        .clone();
    c.featureValues = Arrays.copyOf(featureValues, featureValues.length);
    c.featureIndex = this.featureIndex;
    c.featureIndices = this.featureIndices;
    return c;
  }

  public SparseFeatureValueCollection(Collection<? extends FeatureValue<E>> c,
      Index<E> featureIndex) {
    ClassicCounter<Integer> cnts = new ClassicCounter<Integer>();
    this.featureIndex = featureIndex;
    for (FeatureValue<E> feature : c) {
      int index = featureIndex.indexOf(feature.name, true);
      cnts.incrementCount(index, feature.value);
    }
    featureValues = new double[cnts.size()];
    featureIndices = new int[cnts.size()];
    Iterator<Integer> keys = cnts.keySet().iterator();
    for (int i = 0; keys.hasNext(); i++) {
      Integer index = keys.next();
      featureValues[i] = cnts.getCount(index);
      featureIndices[i] = index;
    }
  }

  @Override
  public int size() {
    return featureValues.length;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public Iterator<FeatureValue<E>> iterator() {
    return new FVIterator();
  }

  class FVIterator implements Iterator<FeatureValue<E>> {

    int position = 0;

    @Override
    public boolean hasNext() {
      return position < featureValues.length;
    }

    @Override
    public FeatureValue<E> next() {
      FeatureValue<E> next = new FeatureValue<E>(
          featureIndex.get(featureIndices[position]), featureValues[position]);
      position++;
      return next;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean contains(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object[] toArray() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <F> F[] toArray(F[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean add(FeatureValue<E> e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(Collection<? extends FeatureValue<E>> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }
}
