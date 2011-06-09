package edu.stanford.nlp.mt.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.mt.parser.Actions.Action;
import edu.stanford.nlp.mt.parser.Actions.ActionType;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.trees.EnglishGrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Pair;

public class ActionRecoverer {

  // check the correctness of recovered action trace
  private static final boolean checkCorrectness = false;

  /** recover the list of actions for constructing s.dependencies
   *  the actions are stored in s.actionTrace */
  private static void recoverActionTrace(Structure s) {
    // extract arc & relation
    Counter<CoreLabel> leftsideEdgeCounter = new OpenAddressCounter<CoreLabel>();
    Set<Pair<CoreLabel, CoreLabel>> arcs = new HashSet<Pair<CoreLabel, CoreLabel>>();
    Map<Pair<CoreLabel,CoreLabel>, GrammaticalRelation> arcRelation =
      new HashMap<Pair<CoreLabel,CoreLabel>, GrammaticalRelation>();

    for(TypedDependency e : s.dependencies) {
      CoreLabel gov = e.gov().label();
      CoreLabel dependent = e.dep().label();
      Pair<CoreLabel, CoreLabel> pair = new Pair<CoreLabel, CoreLabel>(gov, dependent);
      arcs.add(pair);
      arcRelation.put(pair, e.reln());

      if(gov.get(IndexAnnotation.class) < dependent.get(IndexAnnotation.class)) {
        leftsideEdgeCounter.incrementCount(dependent);
      } else {
        leftsideEdgeCounter.incrementCount(gov);
      }
    }

    // recover actions
    Set<CoreLabel> dependents = new HashSet<CoreLabel>();
    Object[] inputs = s.input.peekN(s.input.size());
    for(int i = inputs.length-1 ; i >= 0 ;){
      CoreLabel w = (CoreLabel)inputs[i];
      if(leftsideEdgeCounter.getCount(w) > 0) {
        CoreLabel topStack = s.stack.peek();

        Pair<CoreLabel, CoreLabel> govPair = new Pair<CoreLabel, CoreLabel>(w, topStack);
        Pair<CoreLabel, CoreLabel> depPair = new Pair<CoreLabel, CoreLabel>(topStack, w);

        if(arcs.contains(govPair)) { // left-arc
          s.actionTrace.push(new Action(ActionType.LEFT_ARC, arcRelation.get(govPair)));
          dependents.add(topStack);
          leftsideEdgeCounter.decrementCount(w);
          arcs.remove(govPair);
        } else if (arcs.contains(depPair)) { // right-arc
          s.actionTrace.push(new Action(ActionType.RIGHT_ARC, arcRelation.get(depPair)));
          dependents.add(w);
          leftsideEdgeCounter.decrementCount(w);
          arcs.remove(depPair);
        } else {
          if(!dependents.contains(s.stack.pop())) {
            throw new RuntimeException();
          }
          s.actionTrace.push(new Action(ActionType.REDUCE));
        }
      } else {
        i--;
        s.stack.push(w);
        s.actionTrace.push(new Action(ActionType.SHIFT));

      }
    }
  }

  /** check the correctness of recovered action trace */
  private static void checkRecoveredActionTrace(Structure s){
    Collection<TypedDependency> gold = s.dependencies.getAll();
    LinkedStack<Action> recoveredActions = s.actionTrace;
    LinkedStack<CoreLabel> input = s.input;

    s.reset();

    // TODO
    //    Object[] inputs = input.peekN(input.size());
    //    Object[] actions = recoveredActions.peekN(recoveredActions.size());
    //    int inputIndex = inputs.length-1;
    //    s.stack.push((CoreLabel)inputs[inputIndex--]);
    //    CoreLabel curInput = (CoreLabel) inputs[inputIndex];
    //    for(int i = actions.length-1 ; i >= 0 ; i--){
    //      Action a = (Action)actions[i];
    //      Actions.doAction(a, s);
    //    }
    //    Collection<TypedDependency> recoveredDeps = s.dependencies.getAll();
    //    if(!recoveredDeps.containsAll(gold) || !gold.containsAll(recoveredDeps)) {
    //      throw new RuntimeException("Error in recovered actions");
    //    }
  }

  public static List<Structure> readTrainingData(String filename) throws IOException{
    List<GrammaticalStructure> gsList =
      EnglishGrammaticalStructure.readCoNLLXGrammaticStructureCollection(filename);
    List<Structure> structures = new ArrayList<Structure>();
    for(GrammaticalStructure gs : gsList){
      Structure s = new Structure(gs);
      recoverActionTrace(s);
      structures.add(s);
      if(checkCorrectness) checkRecoveredActionTrace(s);
    }
    return structures;
  }

  public static void main(String[] args) throws IOException{
    //    String filename = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/tb3-trunk-dev-2011-01-13.conll";
    String filename = "/scr/heeyoung/corpus/dependencies/Stanford-11Feb2011/temp.conll";
    List<Structure> structures = readTrainingData(filename);

    for(Structure s : structures) {
      checkRecoveredActionTrace(s);
    }
  }
}
