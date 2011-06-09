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

  /** the word we're looking at might not be the last element in input.
   *  if offset = 2, we're using the 2nd last word.
   *  offset is same as the length of phrase  */
  public static void doAction(Action a, Structure s, int offset) {
    if(a.action==ActionType.SHIFT) shift(s, offset);
    else if(a.action==ActionType.REDUCE) reduce(s);
    else if(a.action==ActionType.LEFT_ARC) leftArc(s, a.relation, offset);
    else if(a.action==ActionType.RIGHT_ARC) rightArc(s, a.relation, offset);
    else {
      throw new RuntimeException("undefined action: "+a.toString());
    }
  }

  private static void shift(Structure s, int offset){
    CoreLabel w;
    if(offset == 1) w = s.input.peek();
    else {
      Object[] o = s.input.peekN(offset);
      w = (CoreLabel) o[offset-1];
    }
    s.stack.push(w);
    s.actionTrace.push(new Action(ActionType.SHIFT));
  }

  private static void reduce(Structure s){
    reduce(s, false);
  }

  /** for MT hypothesis expansion: not mess up the previous stack if shallowCopyWhenReduce is true */
  private static void reduce(Structure s, boolean shallowCopyWhenReduce){
    // TODO
    // stack copy
    s.stack.pop();
    s.actionTrace.push(new Action(ActionType.REDUCE));
  }

  private static void leftArc(Structure s, GrammaticalRelation relation, int offset) {
    s.actionTrace.push(new Action(ActionType.LEFT_ARC, relation));
    CoreLabel w;
    if(offset == 1) w = s.input.peek();
    else {
      Object[] o = s.input.peekN(offset);
      w = (CoreLabel) o[offset-1];
    }
    CoreLabel topStack = s.stack.peek();

    TreeGraphNode gov = new TreeGraphNode(w);
    TreeGraphNode dep = new TreeGraphNode(topStack);
    TypedDependency dependency = new TypedDependency(relation, gov, dep);
    s.dependencies.push(dependency);
  }

  private static void rightArc(Structure s, GrammaticalRelation relation, int offset) {
    s.actionTrace.push(new Action(ActionType.RIGHT_ARC, relation));
    CoreLabel w;
    if(offset == 1) w = s.input.peek();
    else {
      Object[] o = s.input.peekN(offset);
      w = (CoreLabel) o[offset-1];
    }
    CoreLabel topStack = s.stack.peek();
    TreeGraphNode gov = new TreeGraphNode(topStack);
    TreeGraphNode dep = new TreeGraphNode(w);
    TypedDependency dependency = new TypedDependency(relation, gov, dep);
    s.dependencies.push(dependency);
  }
}
