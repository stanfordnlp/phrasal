package edu.stanford.nlp.mt.parser;

import java.util.*;

public class LinkedStack<T> {
  StackNode current = null;
  private int size = 0;
  private Map<Integer, StackNode> nodesMap;
  
  public LinkedStack(){
    nodesMap = new HashMap<Integer, StackNode>();
  }
  
  int size() {
    return size;
  }
  
  T get(int index){
    return nodesMap.get(index).value;
  }

  void push(T value) {
     StackNode newNode = new StackNode(value, current);
     current = newNode;
     nodesMap.put(size++, newNode);
  }

  T pop() {
     if (current == null) {
        throw new RuntimeException("Stack is empty!");
     }

     T currentValue = current.value;
     current = current.parent;
     nodesMap.remove(--size);
     return currentValue;
  }
  
  T peek() {
    if (current == null) {
      throw new RuntimeException("Stack is empty!");
   }
   return current.value;
  }
  class StackNode {
    final T value;
    final StackNode parent;
    public StackNode(T value, StackNode parent) {
         this.value = value;
         this.parent = parent;
    }
  }
}
