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
  public int hashCode() {
    return hashcode;
  }

  public int length() {
    return elements.length;
  }

  @Override
  public String toString() {
    StringBuilder name = new StringBuilder();
    for (int i = 0; i < elements.length; i++) {
      name.append(get(i));
      if (i < elements.length - 1) {
        name.append(' ');
      }
    }
    return name.toString();
  }

  public void print() {
    String s = toString();
    System.out.print(s);
  }
}
