package mt.reranker;
import java.io.*;
import java.util.*;

public class MyList<E> extends ArrayList<E> {
  int size;
  E[] array;
  int[] mapping;
  /*
  public E remove(int i) {
    throw new RuntimeException("not used");
  }

  public boolean remove(Object o) {
    throw new RuntimeException("not used");
  }

  public

  public boolean add(Object o) {
    throw new RuntimeException("not used");
  }

  public Object[] toArray(Object[] a) {
    throw new RuntimeException("not used");
  }

  public Object[] toArray() {
    throw new RuntimeException("not used");
  }

  public int lastIndexOf(Object o) {
    throw new RuntimeException("not used");
  }
  public ListIterator listIterator() {
    throw new RuntimeException("not used");
  }

  public ListIterator listIterator(int i) {
    throw new RuntimeException("not used");
  }

  public List subList(int i, int j) {
    throw new RuntimeException("not used");
  }

  public int indexOf(Object o) {
    throw new RuntimeException("not used");
  }

  public void add(int i, E o) {
    throw new RuntimeException("not used");
  }

  public E set(int i, E o) {
    throw new RuntimeException("not used");
  }
  
  public void clear() {
    throw new RuntimeException("not used");
  }

  public boolean retainAll(Collection c) {
    throw new RuntimeException("not used");
  }

  public boolean removeAll(Collection c) {
    throw new RuntimeException("not used");
  }

  public boolean addAll(int i, Collection c) {
    throw new RuntimeException("not used");
  }

  public boolean addAll(Collection c) {
    throw new RuntimeException("not used");
  }

  public boolean containsAll(Collection c) {
    throw new RuntimeException("not used");
  }
  */
  public E get(int i) {
    return array[mapping[i]];
  }

  public int size() {
    return size;
  }

  public MyList(E[] array, int[] range) {
    this.array = array;
    size = 0;
    for(int i = range[0]; i <= range[1]; i++) {
      if (range.length==4 && i>=range[2] && i<=range[3]) {
        continue;
      }
      size++;
    }
    mapping = new int[size];
    int ctr = 0;
    for(int i = range[0]; i <= range[1]; i++) {
      if (range.length==4 && i>=range[2] && i<=range[3]) {
        continue;
      }
      mapping[ctr++] = i;
    }
  }
}