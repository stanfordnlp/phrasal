package edu.stanford.nlp.mt.parser;

public class LinkedStack<T> {
  StackNode current = null;
  private int size = 0;
  
  public LinkedStack() {    
  }
  
  public LinkedStack(LinkedStack<T> parent) {
    size = parent.size;
    current = parent.current;
  }
  
  int size() {
    return size;
  }
  
  
  void push(T value) {
     StackNode newNode = new StackNode(value, current);
     current = newNode;
     size++;
  }

  T pop() {
     if (current == null) {
        throw new RuntimeException("Stack is empty!");
     }

     T currentValue = current.value;
     current = current.parent;
     size--;
     return currentValue;
  }
  
  T peek() {
    if (current == null) {
      throw new RuntimeException("Stack is empty!");
   }
   return current.value;
  }
  
  /**
   * Get the top N elements off of the top of the stack
   * 
   * @param count
   * @return
   */
  T[] peekN(int n){
    @SuppressWarnings("unchecked")
    T[] elements = (T[]) new Object[n];
    int i;
    StackNode cursor;    
    for (i = 0, cursor = current; i < n && cursor != null; i++, cursor = cursor.parent) {
      elements[i] = cursor.value;
    }
    return elements;
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
