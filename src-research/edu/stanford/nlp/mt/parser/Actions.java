package edu.stanford.nlp.mt.parser;

import java.io.Serializable;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.GrammaticalRelation;

public class Actions {
  
  public static enum ActionType {SHIFT, REDUCE, LEFT_ARC, RIGHT_ARC};
  private static final double EDGE_WEIGHT = 1.0;  // TODO: correct?
  
  public static class Action implements Serializable{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    ActionType action;
    GrammaticalRelation relation;
    boolean incrQueue;
    
    public Action(ActionType a){
      action = a;
      relation = null;
    }
    public Action(ActionType a, GrammaticalRelation r) {
      action = a;
      relation = r;
    }    
    public String toString() {
      if(relation==null) return action.toString();
      else return action+"-"+relation.getShortName();
    }
    
    @Override
    public boolean equals(Object o){
      if(this==o) return true;
      if(!(o instanceof Action)) return false;
      Action a = (Action) o;
      if(relation==null) return this.action==a.action;
      else return (this.action==a.action && this.relation.equals(a.relation));
    }
    @Override
    public int hashCode(){
      return toString().hashCode();
    }    
  }  

  public static void doAction(Action a, Structure s) {
    if(a.action==ActionType.SHIFT) shift(s);
    else if(a.action==ActionType.REDUCE) reduce(s);
    else if(a.action==ActionType.LEFT_ARC) leftArc(s, a.relation);
    else if(a.action==ActionType.RIGHT_ARC) rightArc(s, a.relation);
    else {
      throw new RuntimeException("undefined action: "+a.toString());
    }
  }
  
  private static void shift(Structure s){
    IndexedWord w = s.input.peek(); // s.input.get(s.inputIndex);
    s.stack.push(w);
    s.actionTrace.add(new Action(ActionType.SHIFT));
  }
  
  private static void reduce(Structure s){
    s.stack.pop();
    // TODO: throw runtime exception to ensure every node has a parent
//    IndexedWord pop = s.stack.pop();    
//    IndexedWord ww = s.dependencies.getParent(pop);
//    if(ww==null) {
//      throw new RuntimeException();
//    }
    s.actionTrace.add(new Action(ActionType.REDUCE));
  }
  
  private static void leftArc(Structure s, GrammaticalRelation relation) {
    s.actionTrace.add(new Action(ActionType.LEFT_ARC, relation));
    IndexedWord w = s.input.peek(); // s.input.get(s.inputIndex);
    IndexedWord topStack = s.stack.peek();
    s.dependencies.addVertex(w);
    s.dependencies.addVertex(topStack);
    s.dependencies.addEdge(w, topStack, relation, EDGE_WEIGHT);
  }
  
  private static void rightArc(Structure s, GrammaticalRelation relation) {
    s.actionTrace.add(new Action(ActionType.RIGHT_ARC, relation));
    IndexedWord w = s.input.peek(); // s.input.get(s.inputIndex);
    IndexedWord topStack = s.stack.peek();
    s.dependencies.addVertex(w);
    s.dependencies.addVertex(topStack);
    s.dependencies.addEdge(topStack, w, relation, EDGE_WEIGHT);
  }  
}
