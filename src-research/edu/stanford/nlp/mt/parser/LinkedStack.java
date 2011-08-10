package edu.stanford.nlp.mt.parser;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * First element of the iteration is top element!
 * @author heeyoung
 *
 * @param <T>
 */
public class LinkedStack<T> implements Iterable<T> {
  StackNode current = null;
  private int size = 0;

  public LinkedStack() {
  }

  @Override
  public LinkedStack<T> clone() {
    LinkedStack<T> ls = new LinkedStack<T>();
    ls.current = current;
    ls.size = size;
    return ls;
  }

  public LinkedStack(LinkedStack<T> parent) {
    size = parent.size;
    current = parent.current;
  }
  public LinkedStack(List<T> list){
    size = list.size();
    for(T t : list){
      push(t);
    }
  }

  public int size() {
    return size;
  }


  void push(T value) {
    StackNode newNode = new StackNode(value, current);
    current = newNode;
    size++;
  }

  T pop() {
    if (current == null) {
      throw new RuntimeException("LinkedStack is empty");
    }

    T currentValue = current.value;
    current = current.parent;
    size--;
    return currentValue;
  }

  T peek() {
    if (current == null) {
      throw new RuntimeException("LinkedStack is empty");
    }
    return current.value;
  }

  /**
   * Get the top N elements off of the top of the stack
   * 
   * @param count
   * @return
   */
  @SuppressWarnings("unchecked")
  public T[] peekN(int n){
    T[] elements = (T[]) new Object[n];
    int i;
    StackNode cursor;
    for (i = 0, cursor = current; i < n && cursor != null; i++, cursor = cursor.parent) {
      elements[i] = cursor.value;
    }
    return elements;
  }

  /** O(n) */
  public Collection<T> getAll() {
    Collection<T> col = new HashSet<T>();
    StackNode cursor;
    for(cursor = current ; cursor != null ; cursor = cursor.parent){
      col.add(cursor.value);
    }
    return col;
  }

  class StackNode {
    final T value;
    final StackNode parent;
    public StackNode(T value, StackNode parent) {
      this.value = value;
      this.parent = parent;
    }
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {
      StackNode pointer = current;
      @Override
      public boolean hasNext(){
        return pointer!=null;
      }

      @Override
      public T next() {
        StackNode ret = pointer;
        pointer = pointer.parent;
        return ret.value;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public String toString() {
    StringBuilder sbuild = new StringBuilder();
    sbuild.append("[");
    boolean first = true;
    for (T node : this) {
      if (!first) {
        sbuild.append(", ");
      } else {
        first = false;
      }
      sbuild.append(node.toString());
    }
    sbuild.append("]");
    return sbuild.toString();
  }
}
