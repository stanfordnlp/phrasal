package edu.stanford.nlp.mt.parser;

import java.io.Serializable;

public class ObjectTuple<E> implements Serializable{

  private static final long serialVersionUID = -2097343525404385444L;

  final E[] elements;
  int hashcode = 17;

  public ObjectTuple(E[] arr) {
    elements = arr;
    calculateHashCode();
  }

  public E get(int num) {
    return elements[num];
  }

  public void set(int num, E e) {
    elements[num] = e;
    calculateHashCode();
  }

  private void calculateHashCode() {
    for(E e : elements){
      hashcode = 31 * hashcode + ((e==null)? 0 : e.hashCode()); 
    }
  }

  public E[] elems() {
    return elements;
  }
  
  @Override
  public boolean equals(Object o) {
    if(o==this) return true;
    if(o==null || !(o instanceof ObjectTuple<?>)) return false;
    ObjectTuple<?> ot = (ObjectTuple<?>) o;
    int len = this.elements.length;
    if(ot.elements.length != len) return false;
    for(int idx = 0 ; idx < len ; idx++) {
      if(!this.elements[idx].equals(ot.elements[idx])) return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  public int length() {
    return elements.length;
  }

  @Override
  public String toString() {
    StringBuilder name = new StringBuilder();
    name.append("[");
    for (int i = 0; i < elements.length; i++) {
      name.append(get(i));
      if (i < elements.length - 1) {
        name.append(' ');
      }
    }
    name.append("]");
    return name.toString();
  }

  public void print() {
    String s = toString();
    System.out.print(s);
  }
}
