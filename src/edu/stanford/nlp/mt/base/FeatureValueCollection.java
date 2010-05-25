package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.util.Index;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Michel Galley
 */
public class FeatureValueCollection<E> implements Collection<FeatureValue<E>> {

  final Index<E> featureIndex;
  final double[] arr;
  final BitSet isDefined;

  public FeatureValueCollection(FeatureValueCollection<E> c) {
    this.arr = Arrays.copyOf(c.arr, c.arr.length); // TODO: check if really need deep copy
    this.featureIndex = c.featureIndex;
    this.isDefined = c.isDefined;
  }

  public FeatureValueCollection(Collection<? extends FeatureValue<E>> c, Index<E> featureIndex) {
    arr = new double[featureIndex.size()];
    isDefined = new BitSet(c.size());
    this.featureIndex = featureIndex;
		for (FeatureValue<E> feature : c) {
			int index = featureIndex.indexOf(feature.name, true);
			arr[index] = feature.value;
      isDefined.set(index);
		}
  }

  private FeatureValue<E> denseGet(int index) {
    assert (isDefined.get(index));
    return new FeatureValue<E>(featureIndex.get(index), arr[index]);
  }

  public double[] toDoubleArray() { return arr; }

  @Override public int size() { return isDefined.cardinality(); }
  @Override public boolean isEmpty() { return size() == 0; }
  @Override public Iterator<FeatureValue<E>> iterator() { return new FVIterator(); }

  class FVIterator implements Iterator<FeatureValue<E>> {

    int position = -1;

    @Override public boolean hasNext() {
      return (isDefined.nextSetBit(position+1) >= 0);
    }

    @Override public FeatureValue<E> next() {
      position = isDefined.nextSetBit(position+1);
      return denseGet(position);
    }

    @Override public void remove() { throw new UnsupportedOperationException();  }
  }

  @Override public boolean remove(Object o) { throw new UnsupportedOperationException(); }
  @Override public boolean contains(Object o) { throw new UnsupportedOperationException(); }
  @Override public Object[] toArray() { throw new UnsupportedOperationException(); }
  @Override public <E> E[] toArray(E[] a) { throw new UnsupportedOperationException(); }
  @Override public boolean add(FeatureValue<E> e) { throw new UnsupportedOperationException(); }
  @Override public boolean containsAll(Collection<?> c) { throw new UnsupportedOperationException();  }
	@Override public boolean addAll(Collection<? extends FeatureValue<E>> c) { throw new UnsupportedOperationException(); }
  @Override public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException(); }
  @Override public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
  @Override public void clear() { throw new UnsupportedOperationException(); }

}
