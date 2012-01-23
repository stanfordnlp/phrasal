package edu.stanford.nlp.mt.parser;

import java.io.Serializable;
import java.util.Comparator;
import java.util.TreeSet;

import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LeftChildrenNodeAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Pair;

/**
 * Actions (e.g., shift or left_arc) for shift-reduce dependency parser
 * 
 * @author heeyoung
 */
public class Actions {

  public static enum ActionType {SHIFT, REDUCE, LEFT_ARC, RIGHT_ARC}

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
  public static class IndexComparator implements Comparator<Pair<CoreLabel,String>> {
    public int compare (Pair<CoreLabel, String> l1, Pair<CoreLabel, String> l2) {
      int idx1 = l1.first().get(IndexAnnotation.class);
      int idx2 = l2.first().get(IndexAnnotation.class);
      if(idx1 < idx2) return -1;
      else if (idx1 == idx2) return 0;
      else if (idx1 > idx2) return 1;
      else throw new RuntimeException("Index comparator error");
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

    TreeGraphNode gov;
    if(s.getInputIdxToNode().containsKey(w.get(IndexAnnotation.class))) {
      gov = s.getInputIdxToNode().get(w.get(IndexAnnotation.class));
    } else {
      gov = new TreeGraphNode(w);
      s.getInputIdxToNode().put(w.get(IndexAnnotation.class), gov);
    }
    TreeGraphNode dep;

    if(s.getInputIdxToNode().containsKey(topStack.get(IndexAnnotation.class))) {
      dep = s.getInputIdxToNode().get(topStack.get(IndexAnnotation.class));
    } else {
      dep = new TreeGraphNode(topStack);
      s.getInputIdxToNode().put(topStack.get(IndexAnnotation.class), dep);
    }
    TypedDependency dependency = new TypedDependency(relation, gov, dep);
    s.dependencies.push(dependency);
    s.dependentsIdx.add(topStack.get(IndexAnnotation.class));

    if(!w.containsKey(LeftChildrenNodeAnnotation.class)) {
      w.set(LeftChildrenNodeAnnotation.class, new TreeSet<Pair<CoreLabel, String>>(new IndexComparator()));
    }
    w.get(LeftChildrenNodeAnnotation.class).add(new Pair<CoreLabel, String>(topStack, relation == null ? "dep" : relation.toString()));
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
    TreeGraphNode gov;
    if(s.getInputIdxToNode().containsKey(topStack.get(IndexAnnotation.class))) {
      gov = s.getInputIdxToNode().get(topStack.get(IndexAnnotation.class));
    } else {
      gov = new TreeGraphNode(topStack);
      s.getInputIdxToNode().put(topStack.get(IndexAnnotation.class), gov);
    }
    TreeGraphNode dep;
    if(s.getInputIdxToNode().containsKey(w.get(IndexAnnotation.class))) {
      dep = s.getInputIdxToNode().get(w.get(IndexAnnotation.class));
    } else {
      dep = new TreeGraphNode(w);
      s.getInputIdxToNode().put(w.get(IndexAnnotation.class), dep);
    }
    TypedDependency dependency = new TypedDependency(relation, gov, dep);
    s.dependencies.push(dependency);
    s.dependentsIdx.add(w.get(IndexAnnotation.class));
  }
}
