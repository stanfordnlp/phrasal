package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Index;

/**
 * An non-threadsafe, contiguous index from [-2, 2^31-1]. For compatibility with
 * the Index implementations in CoreNLP, -1 is still reserved as the flag for
 * values that don't occur in the index.
 * 
 * @author Spence Green
 *
 * @param <E>
 */
public class DecoderLocalIndex<E> implements Index<E> {

  private static final long serialVersionUID = 2165256186126958509L;
  
  public static final int MIN_INDEX = TranslationModelIndex.UNKNOWN_ID - 1;
  
  private final Map<E,Integer> index;
  private final List<E> objects;
  
  /**
   * Constructor.
   */
  public DecoderLocalIndex() {
    index = new HashMap<>();
    objects = new ArrayList<>();
  }
  
  /**
   * Constructor.
   * 
   * @param initialCapacity
   */
  public DecoderLocalIndex(int initialCapacity) {
    index = new HashMap<>(initialCapacity);
    objects = new ArrayList<>(initialCapacity);
  }
  
  @Override
  public Iterator<E> iterator() {
    return objects.iterator();
  }

  @Override
  public int size() {
    return index.size();
  }

  @Override
  public E get(int i) {
    if (i < 0 || i >= objects.size()) {
      throw new ArrayIndexOutOfBoundsException("Index " + i +
                                               " outside the bounds [0," +
                                               size() + ")");
    }
    return objects.get(i);
  }

  @Override
  public int indexOf(E o) {
    return index.containsKey(o) ? index.get(o) : TranslationModelIndex.UNKNOWN_ID;
  }

  @Override
  public int addToIndex(E o) {
    if (index.containsKey(o)) {
      return index.get(o);
    
    } else {
      final int newIndex = -1 * (index.size() - MIN_INDEX);
      index.put(o, newIndex);
      objects.add(o);
      return newIndex;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0, sz = objects.size(); i < sz; ++i) {
      if (i > 0) sb.append(" ");
      sb.append(i).append(":").append(objects.get(i));
    }
    sb.append("]");
    return sb.toString();
  }
  
  @Override
  public int indexOf(E o, boolean add) {
    throw new UnsupportedOperationException("Method is deprecated.");
  }

  @Override
  public List<E> objectsList() {
    return Collections.unmodifiableList(objects);
  }

  @Override
  public Collection<E> objects(int[] indices) {
    List<E> subset = new ArrayList<>(indices.length);
    for (int i : indices) {
      if (i >= subset.size()) {
        throw new ArrayIndexOutOfBoundsException("Index " + i +
            " outside the bounds [0," +
            size() + ")");
      }
      subset.add(objects.get(i));
    }
    return subset;
  }

  @Override
  public boolean isLocked() {
    return false;
  }

  @Override
  public void lock() {}

  @Override
  public void unlock() {}

  @Override
  public void saveToWriter(Writer out) throws IOException {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public void saveToFilename(String s) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public boolean contains(Object o) {
    return index.containsKey(o);
  }

  @Override
  public boolean add(E e) {
    return addToIndex(e) != TranslationModelIndex.UNKNOWN_ID;
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    boolean success = true;
    for (E e : c) {
      success &= add(e);
    }
    return success;
  }

  @Override
  public void clear() {
    index.clear();
    objects.clear();
  }
}
