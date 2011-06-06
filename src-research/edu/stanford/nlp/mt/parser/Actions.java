package edu.stanford.nlp.mt.parser;

import java.io.Serializable;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;

public class Actions {

  public static enum ActionType {SHIFT, REDUCE, LEFT_ARC, RIGHT_ARC};

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
    @Override
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
    CoreLabel w = s.input.peek(); // s.input.get(s.inputIndex);
    s.stack.push(w);
    s.actionTrace.push(new Action(ActionType.SHIFT));
  }

  private static void reduce(Structure s){
    s.stack.pop();
    // TODO: throw runtime exception to ensure every node has a parent
    //    IndexedWord pop = s.stack.pop();
    //    IndexedWord ww = s.dependencies.getParent(pop);
    //    if(ww==null) {
    //      throw new RuntimeException();
    //    }
    s.actionTrace.push(new Action(ActionType.REDUCE));
  }

  private static void leftArc(Structure s, GrammaticalRelation relation) {
    s.actionTrace.push(new Action(ActionType.LEFT_ARC, relation));
    CoreLabel w = s.input.peek(); // s.input.get(s.inputIndex);
    CoreLabel topStack = s.stack.peek();

    TreeGraphNode gov = new TreeGraphNode(w);
    TreeGraphNode dep = new TreeGraphNode(topStack);
    TypedDependency dependency = new TypedDependency(relation, gov, dep);
    s.dependencies.push(dependency);
  }

  private static void rightArc(Structure s, GrammaticalRelation relation) {
    s.actionTrace.push(new Action(ActionType.RIGHT_ARC, relation));
    CoreLabel w = s.input.peek(); // s.input.get(s.inputIndex);
    CoreLabel topStack = s.stack.peek();
    TreeGraphNode gov = new TreeGraphNode(topStack);
    TreeGraphNode dep = new TreeGraphNode(w);
    TypedDependency dependency = new TypedDependency(relation, gov, dep);
    s.dependencies.push(dependency);
  }
}
